#include "udp_bridge.hpp"

#include "redirect_types.hpp"
#include "socks5.hpp"

#include <arpa/inet.h>

#include <array>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <memory>
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
    static constexpr std::size_t kBatchSize = 4;
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
    static constexpr std::size_t kInitialBindings = 4;

    UdpBridge* owner = nullptr;
    UdpSession* next = nullptr;
    UdpSession* hash_next = nullptr;
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
    std::unique_ptr<std::uint8_t[]> pending_datagram;
    std::size_t datagram_size = 0;
    OriginalDestination pending_destination{};
    std::uint8_t pending_token[4]{};
    std::unique_ptr<UdpBinding[]> bindings;
    std::size_t binding_count = 0;
    std::size_t binding_capacity = 0;

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
        if (owner->runtime_ != nullptr && pending_valid) {
            (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, pending_token);
        }
        pending_valid = false;
        pending_datagram.reset();
        datagram_size = 0;
        bindings.reset();
        binding_count = 0;
        binding_capacity = 0;
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

    bool ensureBindingCapacity(std::size_t required) {
        if (required <= binding_capacity) return true;
        if (required > kMaxBindings) return false;
        std::size_t next_capacity = binding_capacity == 0 ? kInitialBindings : binding_capacity * 2;
        if (next_capacity < required) next_capacity = required;
        if (next_capacity > kMaxBindings) next_capacity = kMaxBindings;
        std::unique_ptr<UdpBinding[]> next(new (std::nothrow) UdpBinding[next_capacity]{});
        if (next == nullptr) return false;
        for (std::size_t index = 0; index < binding_count; ++index) {
            next[index] = bindings[index];
        }
        bindings = std::move(next);
        binding_capacity = next_capacity;
        return true;
    }

    bool rememberBinding(const OriginalDestination& destination, const std::uint8_t token[4], std::uint64_t now_ms) {
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
                return true;
            }
        }
        std::size_t slot = 0;
        if (binding_count < kMaxBindings && binding_count == binding_capacity) {
            if (!ensureBindingCapacity(binding_count + 1)) return false;
        }
        if (binding_count < kMaxBindings) {
            slot = binding_count++;
        }
        std::uint64_t oldest = UINT64_MAX;
        if (binding_count == kMaxBindings) {
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
        return true;
    }

    bool sendDatagram(const OriginalDestination& destination, const std::uint8_t token[4], const std::uint8_t* payload, std::size_t payload_size) {
        if (payload == nullptr || payload_size > kMaxUdpPayload ||
            (destination.family != AF_INET && destination.family != AF_INET6) ||
            destination.protocol != kProtocolUdp) {
            return false;
        }
        Socks5Endpoint endpoint{};
        endpoint.address_type = destination.family == AF_INET ? 0x01 : 0x04;
        std::memcpy(endpoint.address, destination.addr, destination.family == AF_INET ? 4 : 16);
        endpoint.port = destination.port;
        std::array<std::uint8_t, 22> header{};
        const std::uint8_t empty_payload = 0;
        const std::size_t header_size = buildUdpDatagram(
            endpoint,
            &empty_payload,
            0,
            header.data(),
            header.size());
        if (header_size == 0) return false;
        iovec vectors[2] = {
            {header.data(), header_size},
            {const_cast<std::uint8_t*>(payload), payload_size},
        };
        msghdr message{};
        message.msg_name = &relay_address;
        message.msg_namelen = sizeof(relay_address);
        message.msg_iov = vectors;
        message.msg_iovlen = 2;
        ssize_t sent = -1;
        do {
            sent = sendmsg(relay_fd, &message, MSG_NOSIGNAL);
        } while (sent < 0 && errno == EINTR);
        if (sent < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            if (owner->runtime_ != nullptr) {
                (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, token);
            }
            return true;
        }
        if (sent != static_cast<ssize_t>(header_size + payload_size)) {
            if (owner->runtime_ != nullptr) {
                (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, token);
            }
            return false;
        }
        const std::uint64_t now_ms = monotonicMs();
        if (!rememberBinding(destination, token, now_ms)) {
            if (owner->runtime_ != nullptr) {
                (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, token);
            }
            return false;
        }
        last_activity_ms = now_ms;
        return true;
    }

    void sendPending() {
        if (!pending_valid || state != State::Relay) return;
        if (pending_datagram == nullptr) {
            pending_valid = false;
            datagram_size = 0;
            fail();
            return;
        }
        const bool sent = sendDatagram(
            pending_destination,
            pending_token,
            pending_datagram.get(),
            datagram_size);
        pending_valid = false;
        pending_datagram.reset();
        datagram_size = 0;
        if (!sent) fail();
    }

    bool queueOrSend(
        const OriginalDestination& destination,
        const std::uint8_t token[4],
        const std::uint8_t* payload,
        std::size_t payload_size) {
        if (payload == nullptr || payload_size > kMaxUdpPayload) return false;
        last_activity_ms = monotonicMs();
        if (state == State::Relay) return sendDatagram(destination, token, payload, payload_size);
        if (pending_valid) {
            if (owner->runtime_ != nullptr) {
                (void)owner->runtime_->releaseUdpDestination(owner->bridge_port_, token);
            }
            return true;
        }
        pending_datagram.reset(new (std::nothrow) std::uint8_t[payload_size]);
        if (pending_datagram == nullptr) return false;
        std::memcpy(pending_datagram.get(), payload, payload_size);
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
        if (owner->relay_buffer_ == nullptr) {
            owner->relay_buffer_.reset(new (std::nothrow) std::uint8_t[kMaxUdpPayload]);
        }
        if (owner->relay_buffer_ == nullptr) {
            fail();
            return;
        }
        auto* datagram = owner->relay_buffer_.get();
        const std::uint64_t now_ms = monotonicMs();
        while (true) {
            const ssize_t count = recv(relay_fd, datagram, kMaxUdpPayload, 0);
            if (count < 0 && (errno == EINTR)) continue;
            if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return;
            if (count <= 0) {
                fail();
                return;
            }
            const auto* frame = datagram;
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
                    binding.last_used_ms = now_ms;
                    break;
                }
            }
            if (selected == nullptr && binding_count == 1) selected = &bindings[0];
            if (selected != nullptr) {
                sendToClient(selected->token_addr, frame + payload_offset, size - payload_offset);
            }
            last_activity_ms = now_ms;
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
    session_buckets_.fill(nullptr);
    session_count_ = 0;
    response_count_ = 0;
    closeFd(&listener_fd_);
    closeFd(&epoll_fd_);
    bridge_port_ = 0;
    runtime_ = nullptr;
    socks_host_ = nullptr;
    socks_port_ = 0;
    batch_.reset();
    relay_buffer_.reset();
}

std::size_t UdpBridge::sessionBucket(const sockaddr_in& client) const {
    std::uint32_t hash = ntohl(client.sin_addr.s_addr);
    hash ^= static_cast<std::uint32_t>(ntohs(client.sin_port)) * 0x9e3779b1U;
    hash ^= hash >> 16U;
    hash *= 0x85ebca6bU;
    hash ^= hash >> 13U;
    return static_cast<std::size_t>(hash & (kSessionHashCapacity - 1));
}

UdpSession* UdpBridge::findSession(const sockaddr_in& client) const {
    for (UdpSession* session = session_buckets_[sessionBucket(client)]; session != nullptr; session = session->hash_next) {
        if (!session->closed && sameEndpoint(session->client_address, client)) return session;
    }
    return nullptr;
}

void UdpBridge::insertSession(UdpSession* session) {
    const std::size_t bucket = sessionBucket(session->client_address);
    session->hash_next = session_buckets_[bucket];
    session_buckets_[bucket] = session;
}

void UdpBridge::removeSession(UdpSession* session) {
    UdpSession** cursor = &session_buckets_[sessionBucket(session->client_address)];
    while (*cursor != nullptr) {
        if (*cursor == session) {
            *cursor = session->hash_next;
            session->hash_next = nullptr;
            return;
        }
        cursor = &(*cursor)->hash_next;
    }
}

void UdpBridge::acceptDatagram(const std::atomic_bool* stop_flag) {
    if (batch_ == nullptr) return;
    while (stop_flag == nullptr || !stop_flag->load(std::memory_order_relaxed)) {
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
    const auto releaseToken = [this, &token] {
        (void)runtime_->releaseUdpDestination(bridge_port_, token);
    };

    UdpSession* session = findSession(*client);
    if (session == nullptr) {
        if (session_count_ >= kMaxUdpSessions) {
            releaseToken();
            return;
        }
        const int control_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
        if (control_fd < 0) {
            releaseToken();
            return;
        }
        sockaddr_in proxy{};
        proxy.sin_family = AF_INET;
        proxy.sin_addr.s_addr = inet_addr(socks_host_);
        proxy.sin_port = htons(socks_port_);
        const int result = connect(control_fd, reinterpret_cast<const sockaddr*>(&proxy), sizeof(proxy));
        if (result != 0 && errno != EINPROGRESS) {
            ::close(control_fd);
            releaseToken();
            return;
        }
        session = new (std::nothrow) UdpSession();
        if (session == nullptr) {
            ::close(control_fd);
            releaseToken();
            return;
        }
        session->owner = this;
        session->client_address = *client;
        session->control_fd = control_fd;
        session->control_endpoint = {session, UdpSession::kControlEndpoint, control_fd};
        session->last_activity_ms = monotonicMs();
        session->next = sessions_;
        sessions_ = session;
        insertSession(session);
        ++session_count_;
        if (!addEpoll(epoll_fd_, control_fd, kBaseEvents | EPOLLOUT, &session->control_endpoint)) {
            session->markClosed();
            removeSession(session);
            sessions_ = session->next;
            delete session;
            --session_count_;
            releaseToken();
            return;
        }
        if (result == 0) session->beginGreeting();
    }
    if (!session->queueOrSend(destination, token, payload, count)) {
        releaseToken();
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
            removeSession(session);
            delete session;
            --session_count_;
        } else {
            cursor = &session->next;
        }
    }
}

int UdpBridge::run(std::atomic_bool* stop_flag) {
    if (!valid() || runtime_ == nullptr || !runtime_->active()) {
        errno = EINVAL;
        return -1;
    }
    epoll_event events[64]{};
    while (stop_flag == nullptr || !stop_flag->load(std::memory_order_relaxed)) {
        const int count = epoll_wait(epoll_fd_, events, 64, 1000);
        if (count < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        for (int index = 0; index < count; ++index) {
            if (events[index].data.ptr == nullptr) {
                acceptDatagram(stop_flag);
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
