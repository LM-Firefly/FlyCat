#include "udp_bridge.hpp"

#include "redirect_types.hpp"
#include "socks5.hpp"

#include <arpa/inet.h>

#include <array>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <new>

#include <fcntl.h>
#include <netinet/in.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

#ifndef IP_PKTINFO
#define IP_PKTINFO 8
#endif

#ifndef IP_FREEBIND
#define IP_FREEBIND 15
#endif

namespace yumebox::ebpf {
namespace {

constexpr std::size_t kMaxUdpSessions = 256;
constexpr std::size_t kMaxUdpPayload = 65507;
constexpr std::size_t kHandshakeBufferSize = 256;
constexpr std::size_t kMaxBindings = 64;
constexpr std::uint64_t kSessionIdleMs = 30'000;
constexpr std::uint32_t kBaseEvents = EPOLLERR | EPOLLHUP | EPOLLRDHUP;

bool addEpoll(int epoll_fd, int fd, std::uint32_t events, void* pointer) {
    epoll_event event{};
    event.events = events;
    event.data.ptr = pointer;
    return epoll_ctl(epoll_fd, EPOLL_CTL_ADD, fd, &event) == 0;
}

bool modifyEpoll(int epoll_fd, int fd, std::uint32_t events, void* pointer) {
    epoll_event event{};
    event.events = events;
    event.data.ptr = pointer;
    return epoll_ctl(epoll_fd, EPOLL_CTL_MOD, fd, &event) == 0;
}

void closeFd(int* fd) {
    if (fd != nullptr && *fd >= 0) {
        close(*fd);
        *fd = -1;
    }
}

std::uint64_t monotonicMs() {
    timespec value{};
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) {
        return 0;
    }
    return static_cast<std::uint64_t>(value.tv_sec) * 1000U +
        static_cast<std::uint64_t>(value.tv_nsec) / 1'000'000U;
}

bool sameEndpoint(const sockaddr_in& left, const sockaddr_in& right) {
    return left.sin_family == right.sin_family &&
        left.sin_addr.s_addr == right.sin_addr.s_addr &&
        left.sin_port == right.sin_port;
}

struct UdpEndpoint final {
    UdpSession* session = nullptr;
    int kind = 0;
    int fd = -1;
};

struct UdpBinding final {
    OriginalDestination destination{};
    std::uint8_t token_addr[4]{};
    std::uint64_t last_used_ms = 0;
    bool valid = false;
};

}  // namespace

struct UdpBatchStorage final {
    static constexpr std::size_t kBatchSize = 8;
    static constexpr std::size_t kControlSize = CMSG_SPACE(sizeof(in_pktinfo));

    std::array<std::array<std::uint8_t, kMaxUdpPayload>, kBatchSize> receive_buffers{};
    std::array<sockaddr_in, kBatchSize> receive_addresses{};
    std::array<std::array<std::uint8_t, kControlSize>, kBatchSize> receive_controls{};
    std::array<iovec, kBatchSize> receive_vectors{};
    std::array<mmsghdr, kBatchSize> receive_messages{};
    std::array<std::array<std::uint8_t, kMaxUdpPayload>, kBatchSize> response_buffers{};
    std::array<sockaddr_in, kBatchSize> response_addresses{};
    std::array<std::array<std::uint8_t, 4>, kBatchSize> response_tokens{};
    std::array<std::array<std::uint8_t, kControlSize>, kBatchSize> response_controls{};
    std::array<iovec, kBatchSize> response_vectors{};
    std::array<mmsghdr, kBatchSize> response_messages{};
};

UdpBridge::UdpBridge() = default;

struct UdpSession final {
    enum class State : std::uint8_t {
        ConnectingProxy,
        SendGreeting,
        WaitGreeting,
        SendAssociate,
        WaitAssociate,
        Relay,
    };

    static constexpr int kControlEndpoint = 1;
    static constexpr int kRelayEndpoint = 2;

    UdpBridge* owner = nullptr;
    UdpSession* next = nullptr;
    UdpEndpoint control_endpoint{};
    UdpEndpoint relay_endpoint{};
    sockaddr_in client_address{};
    sockaddr_in relay_address{};
    int control_fd = -1;
    int relay_fd = -1;
    State state = State::ConnectingProxy;
    bool closed = false;
    bool pending_valid = false;
    std::uint64_t last_activity_ms = 0;
    std::array<std::uint8_t, 64> transmit{};
    std::size_t transmit_offset = 0;
    std::size_t transmit_size = 0;
    std::array<std::uint8_t, kHandshakeBufferSize> receive{};
    std::size_t receive_size = 0;
    std::size_t receive_need = 0;
    std::array<std::uint8_t, kMaxUdpPayload> datagram{};
    std::size_t datagram_size = 0;
    OriginalDestination pending_destination{};
    std::uint8_t pending_token[4]{};
    std::array<UdpBinding, kMaxBindings> bindings{};
    std::size_t binding_count = 0;

    void markClosed() {
        if (closed) return;
        closed = true;
        if (owner->runtime_ != nullptr) {
            for (std::size_t index = 0; index < binding_count; ++index) {
                if (bindings[index].valid) {
                    (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, bindings[index].token_addr);
                    bindings[index].valid = false;
                }
            }
        }
        (void)epoll_ctl(owner->epoll_fd_, EPOLL_CTL_DEL, control_fd, nullptr);
        (void)epoll_ctl(owner->epoll_fd_, EPOLL_CTL_DEL, relay_fd, nullptr);
        closeFd(&control_fd);
        closeFd(&relay_fd);
    }

    void updateEvents() {
        if (closed) return;
        if (state == State::Relay) {
            if (!modifyEpoll(owner->epoll_fd_, control_fd, kBaseEvents, &control_endpoint) ||
                !modifyEpoll(owner->epoll_fd_, relay_fd, EPOLLIN | kBaseEvents, &relay_endpoint)) {
                markClosed();
            }
            return;
        }
        std::uint32_t events = kBaseEvents;
        if (state == State::ConnectingProxy || state == State::SendGreeting || state == State::SendAssociate) {
            events |= EPOLLOUT;
        } else {
            events |= EPOLLIN;
        }
        if (!modifyEpoll(owner->epoll_fd_, control_fd, events, &control_endpoint)) {
            markClosed();
        }
    }

    void beginGreeting() {
        transmit[0] = 0x05;
        transmit[1] = 0x01;
        transmit[2] = 0x00;
        transmit_offset = 0;
        transmit_size = 3;
        receive_size = 0;
        receive_need = 2;
        state = State::SendGreeting;
        updateEvents();
    }

    void beginAssociate() {
        transmit[0] = 0x05;
        transmit[1] = 0x03;
        transmit[2] = 0x00;
        transmit[3] = 0x01;
        std::memset(transmit.data() + 4, 0, 6);
        transmit_offset = 0;
        transmit_size = 10;
        receive_size = 0;
        receive_need = 4;
        state = State::SendAssociate;
        updateEvents();
    }

    void fail() { markClosed(); }

    void flushTransmit() {
        while (transmit_offset < transmit_size) {
            const ssize_t written = send(
                control_fd,
                transmit.data() + transmit_offset,
                transmit_size - transmit_offset,
                MSG_NOSIGNAL);
            if (written > 0) {
                transmit_offset += static_cast<std::size_t>(written);
                continue;
            }
            if (written < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) return;
            fail();
            return;
        }
        if (state == State::SendGreeting) {
            state = State::WaitGreeting;
        } else if (state == State::SendAssociate) {
            state = State::WaitAssociate;
        }
        updateEvents();
    }

    bool readExact() {
        while (receive_size < receive_need) {
            const ssize_t count = recv(
                control_fd,
                receive.data() + receive_size,
                receive.size() - receive_size,
                0);
            if (count > 0) {
                receive_size += static_cast<std::size_t>(count);
                continue;
            }
            if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) return false;
            fail();
            return false;
        }
        return true;
    }

    void readGreeting() {
        if (!readExact() || closed) return;
        if (receive[0] != 0x05 || receive[1] != 0x00) {
            fail();
            return;
        }
        beginAssociate();
    }

    bool openRelay() {
        relay_fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
        if (relay_fd < 0) return false;
        sockaddr_in local{};
        local.sin_family = AF_INET;
        local.sin_addr.s_addr = htonl(INADDR_ANY);
        local.sin_port = htons(0);
        if (bind(relay_fd, reinterpret_cast<const sockaddr*>(&local), sizeof(local)) != 0) return false;
        if (receive[3] == 0x01) {
            std::memcpy(&relay_address.sin_addr.s_addr, receive.data() + 4, 4);
            std::memcpy(&relay_address.sin_port, receive.data() + 8, 2);
        } else {
            relay_address.sin_addr.s_addr = inet_addr(owner->socks_host_);
            std::memcpy(&relay_address.sin_port, receive.data() + receive_need - 2, 2);
        }
        relay_address.sin_family = AF_INET;
        if (relay_address.sin_addr.s_addr == htonl(INADDR_ANY) || relay_address.sin_addr.s_addr == INADDR_NONE) {
            relay_address.sin_addr.s_addr = inet_addr(owner->socks_host_);
        }
        if (relay_address.sin_addr.s_addr == INADDR_NONE || relay_address.sin_port == 0) return false;
        relay_endpoint = {this, kRelayEndpoint, relay_fd};
        return addEpoll(owner->epoll_fd_, relay_fd, EPOLLIN | kBaseEvents, &relay_endpoint);
    }

    void readAssociate() {
        while (receive_size < receive_need) {
            const ssize_t count = recv(
                control_fd,
                receive.data() + receive_size,
                receive.size() - receive_size,
                0);
            if (count > 0) {
                receive_size += static_cast<std::size_t>(count);
                if (receive_size >= 4 && receive_need == 4) {
                    if (receive[0] != 0x05 || receive[1] != 0x00 || receive[2] != 0x00) {
                        fail();
                        return;
                    }
                    if (receive[3] == 0x01) {
                        receive_need = 10;
                    } else if (receive[3] == 0x04) {
                        receive_need = 22;
                    } else if (receive[3] == 0x03) {
                        if (receive_size >= 5) {
                            receive_need = 7 + receive[4];
                        }
                    } else {
                        fail();
                        return;
                    }
                }
                if (receive_need > receive.size()) {
                    fail();
                    return;
                }
                continue;
            }
            if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) return;
            fail();
            return;
        }
        if (!openRelay()) {
            fail();
            return;
        }
        state = State::Relay;
        updateEvents();
        if (pending_valid) {
            sendPending();
        }
    }

    void rememberBinding(const OriginalDestination& destination, const std::uint8_t token[4], std::uint64_t now_ms) {
        for (std::size_t index = 0; index < binding_count; ++index) {
            UdpBinding& binding = bindings[index];
            const std::size_t address_size = destination.family == AF_INET ? 4 : 16;
            if (binding.valid && binding.destination.family == destination.family &&
                binding.destination.port == destination.port &&
                std::memcmp(binding.destination.addr, destination.addr, address_size) == 0) {
                if (std::memcmp(binding.token_addr, token, 4) != 0 && owner->runtime_ != nullptr) {
                    (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, binding.token_addr);
                }
                std::memcpy(binding.token_addr, token, 4);
                binding.last_used_ms = now_ms;
                return;
            }
        }
        std::size_t slot = binding_count < bindings.size() ? binding_count++ : 0;
        std::uint64_t oldest = UINT64_MAX;
        if (binding_count == bindings.size()) {
            for (std::size_t index = 0; index < binding_count; ++index) {
                if (bindings[index].last_used_ms < oldest) {
                    oldest = bindings[index].last_used_ms;
                    slot = index;
                }
            }
            if (bindings[slot].valid && owner->runtime_ != nullptr) {
                (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, bindings[slot].token_addr);
            }
        }
        bindings[slot].destination = destination;
        std::memcpy(bindings[slot].token_addr, token, 4);
        bindings[slot].last_used_ms = now_ms;
        bindings[slot].valid = true;
    }

    bool sendDatagram(const OriginalDestination& destination, const std::uint8_t token[4], const std::uint8_t* payload, std::size_t payload_size) {
        if ((destination.family != AF_INET && destination.family != AF_INET6) ||
            destination.protocol != kProtocolUdp) {
            return false;
        }
        Socks5Endpoint endpoint{};
        endpoint.address_type = destination.family == AF_INET ? 0x01 : 0x04;
        std::memcpy(endpoint.address, destination.addr, destination.family == AF_INET ? 4 : 16);
        endpoint.port = destination.port;
        std::array<std::uint8_t, kMaxUdpPayload + 16> framed{};
        const std::size_t frame_size = buildUdpDatagram(
            endpoint,
            payload,
            payload_size,
            framed.data(),
            framed.size());
        if (frame_size == 0) return false;
        const ssize_t sent = sendto(
            relay_fd,
            framed.data(),
            frame_size,
            MSG_NOSIGNAL,
            reinterpret_cast<const sockaddr*>(&relay_address),
            sizeof(relay_address));
        if (sent < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) return true;
        if (sent != static_cast<ssize_t>(frame_size)) return false;
        rememberBinding(destination, token, monotonicMs());
        last_activity_ms = monotonicMs();
        return true;
    }

    void sendPending() {
        if (!pending_valid || state != State::Relay) return;
        const bool sent = sendDatagram(
            pending_destination,
            pending_token,
            datagram.data(),
            datagram_size);
        pending_valid = false;
        if (!sent) fail();
    }

    bool queueOrSend(
        const OriginalDestination& destination,
        const std::uint8_t token[4],
        const std::uint8_t* payload,
        std::size_t payload_size) {
        if (payload_size > datagram.size()) return false;
        last_activity_ms = monotonicMs();
        if (state == State::Relay) return sendDatagram(destination, token, payload, payload_size);
        if (pending_valid) return true;
        std::memcpy(datagram.data(), payload, payload_size);
        datagram_size = payload_size;
        pending_destination = destination;
        std::memcpy(pending_token, token, 4);
        pending_valid = true;
        return true;
    }

    void sendToClient(const std::uint8_t token[4], const std::uint8_t* payload, std::size_t payload_size) {
        owner->queueToClient(client_address, token, payload, payload_size);
    }

    void readRelay() {
        while (true) {
            const ssize_t count = recv(relay_fd, datagram.data(), datagram.size(), 0);
            if (count < 0 && (errno == EINTR)) continue;
            if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return;
            if (count <= 0) {
                fail();
                return;
            }
            const auto* frame = datagram.data();
            const std::size_t size = static_cast<std::size_t>(count);
            if (size < 4 || frame[0] != 0 || frame[1] != 0 || frame[2] != 0) continue;
            std::size_t address_offset = 4;
            std::size_t address_size = 0;
            if (frame[3] == 0x01) {
                address_size = 4;
            } else if (frame[3] == 0x04) {
                address_size = 16;
            } else if (frame[3] == 0x03 && size >= 5) {
                address_size = frame[4];
                address_offset = 5;
            } else {
                continue;
            }
            const std::size_t port_offset = address_offset + address_size;
            const std::size_t payload_offset = port_offset + 2;
            if (size < payload_offset || address_size > 16) continue;
            const std::uint16_t remote_port = static_cast<std::uint16_t>(frame[port_offset] << 8U | frame[port_offset + 1]);
            const std::uint8_t* remote_addr = frame + address_offset;
            UdpBinding* selected = nullptr;
            for (std::size_t index = 0; index < binding_count; ++index) {
                UdpBinding& binding = bindings[index];
                const std::size_t binding_address_size = binding.destination.family == AF_INET ? 4 : 16;
                const std::uint8_t expected_address_type = binding.destination.family == AF_INET ? 0x01 : 0x04;
                if (binding.valid && frame[3] == expected_address_type &&
                    binding.destination.port == remote_port &&
                    std::memcmp(binding.destination.addr, remote_addr, binding_address_size) == 0) {
                    selected = &binding;
                    binding.last_used_ms = monotonicMs();
                    break;
                }
            }
            if (selected == nullptr && binding_count == 1) selected = &bindings[0];
            if (selected != nullptr) {
                sendToClient(selected->token_addr, frame + payload_offset, size - payload_offset);
            }
            last_activity_ms = monotonicMs();
        }
    }

    void onEvent(int fd, std::uint32_t events) {
        if (closed) return;
        last_activity_ms = monotonicMs();
        if ((events & (EPOLLERR | EPOLLHUP | EPOLLRDHUP)) != 0) {
            if (state != State::ConnectingProxy) {
                fail();
                return;
            }
        }
        if (fd == control_fd) {
            if (state == State::ConnectingProxy) {
                int error = 0;
                socklen_t length = sizeof(error);
                if (getsockopt(control_fd, SOL_SOCKET, SO_ERROR, &error, &length) != 0 || error != 0) {
                    fail();
                    return;
                }
                beginGreeting();
            } else if ((events & EPOLLOUT) != 0 && (state == State::SendGreeting || state == State::SendAssociate)) {
                flushTransmit();
            } else if ((events & EPOLLIN) != 0 && state == State::WaitGreeting) {
                readGreeting();
            } else if ((events & EPOLLIN) != 0 && state == State::WaitAssociate) {
                readAssociate();
            }
        } else if (fd == relay_fd && state == State::Relay && (events & EPOLLIN) != 0) {
            readRelay();
        }
    }
};

UdpBridge::~UdpBridge() { close(); }

bool UdpBridge::open(const UdpBridgeConfig& config, CgroupRuntime* runtime) {
    close();
    if (config.bridge_port == 0 || config.socks_host == nullptr || config.socks_port == 0 || runtime == nullptr) {
        errno = EINVAL;
        return false;
    }
    batch_.reset(new (std::nothrow) UdpBatchStorage());
    if (batch_ == nullptr) {
        errno = ENOMEM;
        return false;
    }
    runtime_ = runtime;
    socks_host_ = config.socks_host;
    socks_port_ = config.socks_port;
    listener_fd_ = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (listener_fd_ < 0) {
        close();
        return false;
    }
    int enabled = 1;
    if (setsockopt(listener_fd_, IPPROTO_IP, IP_PKTINFO, &enabled, sizeof(enabled)) != 0) {
        close();
        return false;
    }
    (void)setsockopt(listener_fd_, IPPROTO_IP, IP_FREEBIND, &enabled, sizeof(enabled));
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_ANY);
    address.sin_port = htons(config.bridge_port);
    if (bind(listener_fd_, reinterpret_cast<const sockaddr*>(&address), sizeof(address)) != 0) {
        close();
        return false;
    }
    epoll_fd_ = epoll_create1(EPOLL_CLOEXEC);
    if (epoll_fd_ < 0 || !addEpoll(epoll_fd_, listener_fd_, EPOLLIN | kBaseEvents, nullptr)) {
        close();
        return false;
    }
    bridge_port_ = config.bridge_port;
    return true;
}

void UdpBridge::close() {
    for (UdpSession* session = sessions_; session != nullptr; session = session->next) session->markClosed();
    while (sessions_ != nullptr) {
        UdpSession* session = sessions_;
        sessions_ = session->next;
        delete session;
    }
    session_count_ = 0;
    response_count_ = 0;
    closeFd(&listener_fd_);
    closeFd(&epoll_fd_);
    bridge_port_ = 0;
    runtime_ = nullptr;
    socks_host_ = nullptr;
    socks_port_ = 0;
    batch_.reset();
}

void UdpBridge::acceptDatagram() {
    if (batch_ == nullptr) return;
    while (true) {
        for (std::size_t index = 0; index < UdpBatchStorage::kBatchSize; ++index) {
            batch_->receive_addresses[index] = {};
            batch_->receive_controls[index].fill(0);
            batch_->receive_vectors[index] = {batch_->receive_buffers[index].data(), batch_->receive_buffers[index].size()};
            batch_->receive_messages[index] = {};
            batch_->receive_messages[index].msg_hdr.msg_name = &batch_->receive_addresses[index];
            batch_->receive_messages[index].msg_hdr.msg_namelen = sizeof(sockaddr_in);
            batch_->receive_messages[index].msg_hdr.msg_iov = &batch_->receive_vectors[index];
            batch_->receive_messages[index].msg_hdr.msg_iovlen = 1;
            batch_->receive_messages[index].msg_hdr.msg_control = batch_->receive_controls[index].data();
            batch_->receive_messages[index].msg_hdr.msg_controllen = batch_->receive_controls[index].size();
        }
        const int count = recvmmsg(
            listener_fd_,
            batch_->receive_messages.data(),
            static_cast<unsigned int>(UdpBatchStorage::kBatchSize),
            MSG_DONTWAIT,
            nullptr);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return;
        if (count <= 0) return;
        for (int index = 0; index < count; ++index) {
            processDatagram(
                batch_->receive_messages[static_cast<std::size_t>(index)].msg_hdr,
                batch_->receive_messages[static_cast<std::size_t>(index)].msg_len);
        }
        if (count < static_cast<int>(UdpBatchStorage::kBatchSize)) return;
    }
}

void UdpBridge::processDatagram(const msghdr& message, std::size_t count) {
    if (count == 0 || message.msg_name == nullptr || message.msg_namelen < sizeof(sockaddr_in)) return;
    const auto* client = reinterpret_cast<const sockaddr_in*>(message.msg_name);
    if (client->sin_family != AF_INET || message.msg_iov == nullptr || message.msg_iovlen == 0) return;
    const auto* payload = static_cast<const std::uint8_t*>(message.msg_iov[0].iov_base);
    if (payload == nullptr) return;

    std::uint8_t token[4]{};
    bool has_token = false;
    msghdr* mutable_message = const_cast<msghdr*>(&message);
    for (cmsghdr* header = CMSG_FIRSTHDR(mutable_message); header != nullptr; header = CMSG_NXTHDR(mutable_message, header)) {
        if (header->cmsg_level == IPPROTO_IP && header->cmsg_type == IP_PKTINFO &&
            header->cmsg_len >= CMSG_LEN(sizeof(in_pktinfo))) {
            const auto* info = reinterpret_cast<const in_pktinfo*>(CMSG_DATA(header));
            std::memcpy(token, &info->ipi_addr.s_addr, 4);
            has_token = true;
            break;
        }
    }
    if (!has_token) return;
    OriginalDestination destination{};
    if (!runtime_->takeUdpDestination(bridge_port_, token, &destination)) return;

    UdpSession* session = nullptr;
    for (UdpSession* cursor = sessions_; cursor != nullptr; cursor = cursor->next) {
        if (!cursor->closed && sameEndpoint(cursor->client_address, *client)) {
            session = cursor;
            break;
        }
    }
    if (session == nullptr) {
        if (session_count_ >= kMaxUdpSessions) return;
        const int control_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
        if (control_fd < 0) return;
        sockaddr_in proxy{};
        proxy.sin_family = AF_INET;
        proxy.sin_addr.s_addr = inet_addr(socks_host_);
        proxy.sin_port = htons(socks_port_);
        const int result = connect(control_fd, reinterpret_cast<const sockaddr*>(&proxy), sizeof(proxy));
        if (result != 0 && errno != EINPROGRESS) {
            ::close(control_fd);
            return;
        }
        session = new (std::nothrow) UdpSession();
        if (session == nullptr) {
            ::close(control_fd);
            return;
        }
        session->owner = this;
        session->client_address = *client;
        session->control_fd = control_fd;
        session->control_endpoint = {session, UdpSession::kControlEndpoint, control_fd};
        session->last_activity_ms = monotonicMs();
        session->next = sessions_;
        sessions_ = session;
        ++session_count_;
        if (!addEpoll(epoll_fd_, control_fd, kBaseEvents | EPOLLOUT, &session->control_endpoint)) {
            session->markClosed();
            return;
        }
        if (result == 0) session->beginGreeting();
    }
    if (!session->queueOrSend(destination, token, payload, count)) {
        session->markClosed();
    }
}

void UdpBridge::queueToClient(
    const sockaddr_in& client,
    const std::uint8_t token[4],
    const std::uint8_t* payload,
    std::size_t payload_size) {
    if (payload == nullptr || payload_size > kMaxUdpPayload) return;
    if (response_count_ == UdpBatchStorage::kBatchSize) flushToClients();
    if (response_count_ == UdpBatchStorage::kBatchSize) return;
    const std::size_t index = response_count_++;
    batch_->response_addresses[index] = client;
    std::memcpy(batch_->response_tokens[index].data(), token, 4);
    std::memcpy(batch_->response_buffers[index].data(), payload, payload_size);
    batch_->response_vectors[index] = {batch_->response_buffers[index].data(), payload_size};
}

void UdpBridge::flushToClients() {
    if (batch_ == nullptr || response_count_ == 0 || listener_fd_ < 0) return;
    for (std::size_t index = 0; index < response_count_; ++index) {
        batch_->response_controls[index].fill(0);
        batch_->response_messages[index] = {};
        batch_->response_messages[index].msg_hdr.msg_name = &batch_->response_addresses[index];
        batch_->response_messages[index].msg_hdr.msg_namelen = sizeof(sockaddr_in);
        batch_->response_messages[index].msg_hdr.msg_iov = &batch_->response_vectors[index];
        batch_->response_messages[index].msg_hdr.msg_iovlen = 1;
        batch_->response_messages[index].msg_hdr.msg_control = batch_->response_controls[index].data();
        batch_->response_messages[index].msg_hdr.msg_controllen = batch_->response_controls[index].size();
        cmsghdr* header = CMSG_FIRSTHDR(&batch_->response_messages[index].msg_hdr);
        if (header == nullptr) continue;
        header->cmsg_level = IPPROTO_IP;
        header->cmsg_type = IP_PKTINFO;
        header->cmsg_len = CMSG_LEN(sizeof(in_pktinfo));
        auto* packet_info = reinterpret_cast<in_pktinfo*>(CMSG_DATA(header));
        packet_info->ipi_ifindex = 0;
        std::memcpy(&packet_info->ipi_spec_dst.s_addr, batch_->response_tokens[index].data(), 4);
    }
    const int sent = sendmmsg(
        listener_fd_,
        batch_->response_messages.data(),
        static_cast<unsigned int>(response_count_),
        MSG_DONTWAIT | MSG_NOSIGNAL);
    if (sent < 0) {
        if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) return;
        response_count_ = 0;
        return;
    }
    if (static_cast<std::size_t>(sent) >= response_count_) {
        response_count_ = 0;
        return;
    }
    const std::size_t unsent = response_count_ - static_cast<std::size_t>(sent);
    for (std::size_t index = 0; index < unsent; ++index) {
        const std::size_t source = index + static_cast<std::size_t>(sent);
        batch_->response_addresses[index] = batch_->response_addresses[source];
        batch_->response_tokens[index] = batch_->response_tokens[source];
        const std::size_t payload_size = batch_->response_vectors[source].iov_len;
        std::memmove(batch_->response_buffers[index].data(), batch_->response_buffers[source].data(), payload_size);
        batch_->response_vectors[index] = {batch_->response_buffers[index].data(), payload_size};
    }
    response_count_ = unsent;
}

void UdpBridge::cleanupSessions(std::uint64_t now_ms) {
    UdpSession** cursor = &sessions_;
    while (*cursor != nullptr) {
        UdpSession* session = *cursor;
        if (session->closed || (now_ms > session->last_activity_ms && now_ms - session->last_activity_ms > kSessionIdleMs)) {
            session->markClosed();
            *cursor = session->next;
            delete session;
            --session_count_;
        } else {
            cursor = &session->next;
        }
    }
}

int UdpBridge::run(volatile sig_atomic_t* stop_flag) {
    if (!valid() || runtime_ == nullptr || !runtime_->active()) {
        errno = EINVAL;
        return -1;
    }
    epoll_event events[64]{};
    while (stop_flag == nullptr || *stop_flag == 0) {
        const int count = epoll_wait(epoll_fd_, events, 64, 1000);
        if (count < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        for (int index = 0; index < count; ++index) {
            if (events[index].data.ptr == nullptr) {
                acceptDatagram();
                continue;
            }
            auto* endpoint = static_cast<UdpEndpoint*>(events[index].data.ptr);
            if (endpoint->session != nullptr && !endpoint->session->closed) {
                endpoint->session->onEvent(endpoint->fd, events[index].events);
            }
        }
        flushToClients();
        cleanupSessions(monotonicMs());
    }
    return 0;
}

}  // namespace yumebox::ebpf
