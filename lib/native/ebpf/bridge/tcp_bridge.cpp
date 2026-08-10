#include "tcp_bridge.hpp"

#include "redirect_types.hpp"
#include "socks5.hpp"

#include <arpa/inet.h>

#include <array>
#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>

#include <fcntl.h>
#include <netinet/in.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

#ifndef SPLICE_F_MOVE
#define SPLICE_F_MOVE 1U
#endif

#ifndef SPLICE_F_NONBLOCK
#define SPLICE_F_NONBLOCK 2U
#endif

namespace yumebox::ebpf {
namespace {

constexpr std::size_t kRelayBufferSize = 32 * 1024;
constexpr std::size_t kHandshakeBufferSize = 512;
// Bound fallback relay memory: each non-splice session can own two 32 KiB buffers.
constexpr std::size_t kMaxTcpSessions = 1024;
constexpr std::uint64_t kSessionIdleMs = 120'000;
using RelayBuffer = std::array<std::uint8_t, kRelayBufferSize>;
// EPOLLHUP/EPOLLERR are terminal for a session. RDHUP is subscribed below
// only while the corresponding source endpoint can still produce input;
// leaving it in every relay mask creates a level-triggered busy loop after a
// peer half-closes its stream.
constexpr std::uint32_t kBaseEvents = EPOLLERR | EPOLLHUP;

std::uint64_t monotonicMs() {
    timespec value{};
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) return 0;
    return static_cast<std::uint64_t>(value.tv_sec) * 1000U +
        static_cast<std::uint64_t>(value.tv_nsec) / 1'000'000U;
}

bool setNonBlocking(int fd) {
    const int flags = fcntl(fd, F_GETFL, 0);
    return flags >= 0 && fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0;
}

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

struct TcpDirection final {
    int source = -1;
    int destination = -1;
    int pipe_read = -1;
    int pipe_write = -1;
    bool use_splice = true;
    bool pipe_pending = false;
    bool source_eof = false;
    bool destination_shutdown = false;
    std::unique_ptr<RelayBuffer> buffer;
    std::size_t buffer_offset = 0;
    std::size_t buffer_size = 0;

    [[nodiscard]] bool ensureBuffer() {
        if (buffer != nullptr) return true;
        buffer.reset(new (std::nothrow) RelayBuffer);
        return buffer != nullptr;
    }

    [[nodiscard]] std::size_t bufferCapacity() const {
        return buffer == nullptr ? 0 : buffer->size();
    }

    void closePipe() {
        closeFd(&pipe_read);
        closeFd(&pipe_write);
        pipe_pending = false;
    }

    void closeAll() {
        closePipe();
        buffer.reset();
    }

    [[nodiscard]] bool pending() const {
        return use_splice ? pipe_pending : buffer_size != 0;
    }
};

}  // namespace

struct TcpEndpoint final {
    TcpSession* session = nullptr;
    int fd = -1;
};

struct TcpSession final {
    enum class State : std::uint8_t {
        ConnectingProxy,
        SendGreeting,
        WaitGreeting,
        SendConnect,
        WaitConnect,
        Relay,
    };

    TcpBridge* owner = nullptr;
    TcpSession* next = nullptr;
    TcpEndpoint client_endpoint{};
    TcpEndpoint proxy_endpoint{};
    int client_fd = -1;
    int proxy_fd = -1;
    State state = State::ConnectingProxy;
    bool closed = false;
    std::uint64_t last_activity_ms = 0;
    std::array<std::uint8_t, 64> transmit{};
    std::size_t transmit_offset = 0;
    std::size_t transmit_size = 0;
    std::array<std::uint8_t, kHandshakeBufferSize> receive{};
    std::size_t receive_size = 0;
    std::size_t receive_need = 0;
    OriginalDestination original{};
    TcpDirection client_to_proxy{};
    TcpDirection proxy_to_client{};

    void markClosed() {
        if (closed) {
            return;
        }
        closed = true;
        (void)epoll_ctl(owner->epoll_fd_, EPOLL_CTL_DEL, client_fd, nullptr);
        (void)epoll_ctl(owner->epoll_fd_, EPOLL_CTL_DEL, proxy_fd, nullptr);
        closeFd(&client_fd);
        closeFd(&proxy_fd);
        client_to_proxy.closeAll();
        proxy_to_client.closeAll();
    }

    void updateEvents();
    void onEvent(int fd, std::uint32_t events);

private:
    friend class TcpBridge;

    void fail() { markClosed(); }
    void beginGreeting();
    void beginConnectRequest();
    void flushTransmit();
    void readGreeting();
    void readConnectReply();
    void enterRelay();
    void handleDirection(TcpDirection& direction, int fd, std::uint32_t events);
    void handleSplice(TcpDirection& direction, int fd, std::uint32_t events);
    void handleBuffered(TcpDirection& direction, int fd, std::uint32_t events);
    bool drainSplice(TcpDirection& direction);
    bool flushBuffered(TcpDirection& direction);
    bool shutdownDestinationIfDone(TcpDirection& direction);
    void disableSplice(TcpDirection& direction);
};

void TcpSession::updateEvents() {
    if (closed) {
        return;
    }
    if (state != State::Relay) {
        std::uint32_t proxy_events = kBaseEvents;
        if (state == State::ConnectingProxy || state == State::SendGreeting || state == State::SendConnect) {
            proxy_events |= EPOLLOUT | EPOLLRDHUP;
        } else {
            proxy_events |= EPOLLIN | EPOLLRDHUP;
        }
        if (!modifyEpoll(owner->epoll_fd_, proxy_fd, proxy_events, &proxy_endpoint)) {
            fail();
        }
        return;
    }

    std::uint32_t client_events = kBaseEvents;
    std::uint32_t proxy_events = kBaseEvents;
    const auto addDirectionEvents = [](const TcpDirection& direction, std::uint32_t* source_events, std::uint32_t* destination_events) {
        if (direction.use_splice) {
            if (!direction.source_eof && !direction.pipe_pending) {
                *source_events |= EPOLLIN | EPOLLRDHUP;
            }
            if (direction.pipe_pending) {
                *destination_events |= EPOLLOUT;
            }
            return;
        }
        if (!direction.source_eof && direction.buffer_size < direction.bufferCapacity()) {
            *source_events |= EPOLLIN | EPOLLRDHUP;
        }
        if (direction.buffer_size != 0) {
            *destination_events |= EPOLLOUT;
        }
    };
    addDirectionEvents(client_to_proxy, &client_events, &proxy_events);
    addDirectionEvents(proxy_to_client, &proxy_events, &client_events);
    if (!modifyEpoll(owner->epoll_fd_, client_fd, client_events, &client_endpoint) ||
        !modifyEpoll(owner->epoll_fd_, proxy_fd, proxy_events, &proxy_endpoint)) {
        fail();
    }
}

void TcpSession::beginGreeting() {
    transmit_offset = 0;
    transmit_size = buildNoAuthGreeting(transmit.data(), transmit.size());
    receive_size = 0;
    receive_need = 2;
    state = State::SendGreeting;
    updateEvents();
}

void TcpSession::beginConnectRequest() {
    if ((original.family != AF_INET && original.family != AF_INET6) ||
        original.protocol != kProtocolTcp) {
        fail();
        return;
    }
    Socks5Endpoint endpoint{};
    endpoint.address_type = original.family == AF_INET ? 0x01 : 0x04;
    std::memcpy(endpoint.address, original.addr, original.family == AF_INET ? 4 : 16);
    endpoint.port = original.port;
    transmit_offset = 0;
    transmit_size = buildConnectRequest(endpoint, transmit.data(), transmit.size());
    if (transmit_size == 0) {
        fail();
        return;
    }
    receive_size = 0;
    receive_need = 4;
    state = State::SendConnect;
    updateEvents();
}

void TcpSession::flushTransmit() {
    while (transmit_offset < transmit_size) {
        const ssize_t written = send(
            proxy_fd,
            transmit.data() + transmit_offset,
            transmit_size - transmit_offset,
            MSG_NOSIGNAL);
        if (written > 0) {
            transmit_offset += static_cast<std::size_t>(written);
            continue;
        }
        if (written < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) {
            return;
        }
        fail();
        return;
    }
    if (state == State::SendGreeting) {
        state = State::WaitGreeting;
        updateEvents();
    } else if (state == State::SendConnect) {
        state = State::WaitConnect;
        updateEvents();
    }
}

void TcpSession::readGreeting() {
    while (receive_size < receive_need) {
        const ssize_t count = recv(
            proxy_fd,
            receive.data() + receive_size,
            receive_need - receive_size,
            0);
        if (count > 0) {
            receive_size += static_cast<std::size_t>(count);
            continue;
        }
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) {
            return;
        }
        fail();
        return;
    }
    if (receive[0] != 0x05 || receive[1] != 0x00) {
        fail();
        return;
    }
    beginConnectRequest();
}

void TcpSession::readConnectReply() {
    while (receive_size < receive_need) {
        const ssize_t count = recv(
            proxy_fd,
            receive.data() + receive_size,
            receive_need - receive_size,
            0);
        if (count > 0) {
            receive_size += static_cast<std::size_t>(count);
            if (receive_size >= 4 && receive_need == 4) {
                if (receive[0] != 0x05 || receive[1] != 0x00 || receive[2] != 0x00) {
                    fail();
                    return;
                }
                switch (receive[3]) {
                    case 0x01:
                        receive_need = 10;
                        break;
                    case 0x04:
                        receive_need = 22;
                        break;
                    case 0x03:
                        receive_need = 7;
                        break;
                    default:
                        fail();
                        return;
                }
            }
            if (receive[3] == 0x03 && receive_size >= 5 && receive_need == 7) {
                receive_need = 7 + receive[4];
            }
            if (receive_need > receive.size()) {
                fail();
                return;
            }
            continue;
        }
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) {
            return;
        }
        fail();
        return;
    }
    enterRelay();
}

void TcpSession::enterRelay() {
    state = State::Relay;
    client_to_proxy.source = client_fd;
    client_to_proxy.destination = proxy_fd;
    proxy_to_client.source = proxy_fd;
    proxy_to_client.destination = client_fd;
    updateEvents();
}

bool TcpSession::shutdownDestinationIfDone(TcpDirection& direction) {
    if (direction.source_eof && !direction.pending() && !direction.destination_shutdown) {
        if (shutdown(direction.destination, SHUT_WR) != 0 && errno != ENOTCONN) {
            fail();
            return false;
        }
        direction.destination_shutdown = true;
    }
    return true;
}

bool TcpSession::drainSplice(TcpDirection& direction) {
    while (direction.pipe_pending) {
        const ssize_t count = splice(
            direction.pipe_read,
            nullptr,
            direction.destination,
            nullptr,
            kRelayBufferSize,
            SPLICE_F_MOVE | SPLICE_F_NONBLOCK);
        if (count > 0) {
            continue;
        }
        if (count < 0 && (errno == EINTR)) {
            continue;
        }
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            int pending_bytes = 1;
            if (ioctl(direction.pipe_read, FIONREAD, &pending_bytes) == 0 && pending_bytes == 0) {
                direction.pipe_pending = false;
                return shutdownDestinationIfDone(direction);
            }
            return true;
        }
        fail();
        return false;
    }
    return shutdownDestinationIfDone(direction);
}

void TcpSession::disableSplice(TcpDirection& direction) {
    if (direction.pipe_pending) {
        fail();
        return;
    }
    if (!direction.ensureBuffer()) {
        fail();
        return;
    }
    direction.use_splice = false;
    direction.closePipe();
}

void TcpSession::handleSplice(TcpDirection& direction, int fd, std::uint32_t events) {
    if (direction.pipe_pending && fd == direction.destination && (events & EPOLLOUT) != 0) {
        if (!drainSplice(direction)) {
            return;
        }
    }
    if (!direction.pipe_pending && !direction.source_eof && fd == direction.source &&
        (events & (EPOLLIN | EPOLLRDHUP | EPOLLHUP)) != 0) {
        const ssize_t count = splice(
            direction.source,
            nullptr,
            direction.pipe_write,
            nullptr,
            kRelayBufferSize,
            SPLICE_F_MOVE | SPLICE_F_NONBLOCK);
        if (count > 0) {
            direction.pipe_pending = true;
            (void)drainSplice(direction);
        } else if (count == 0) {
            direction.source_eof = true;
            (void)shutdownDestinationIfDone(direction);
        } else if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
            return;
        } else if (errno == ENOSYS || errno == EINVAL || errno == ENOTSUP || errno == EOPNOTSUPP) {
            disableSplice(direction);
            if (!closed) {
                handleBuffered(direction, fd, events);
            }
        } else {
            fail();
        }
    }
}

bool TcpSession::flushBuffered(TcpDirection& direction) {
    if (direction.buffer == nullptr) {
        fail();
        return false;
    }
    while (direction.buffer_size != 0) {
        const ssize_t count = send(
            direction.destination,
            direction.buffer->data() + direction.buffer_offset,
            direction.buffer_size,
            MSG_NOSIGNAL);
        if (count > 0) {
            direction.buffer_offset += static_cast<std::size_t>(count);
            direction.buffer_size -= static_cast<std::size_t>(count);
            if (direction.buffer_size == 0) {
                direction.buffer_offset = 0;
            }
            continue;
        }
        if (count < 0 && (errno == EINTR)) {
            continue;
        }
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
            return true;
        }
        fail();
        return false;
    }
    return shutdownDestinationIfDone(direction);
}

void TcpSession::handleBuffered(TcpDirection& direction, int fd, std::uint32_t events) {
    if (!direction.ensureBuffer()) {
        fail();
        return;
    }
    if (fd == direction.destination && (events & EPOLLOUT) != 0) {
        if (!flushBuffered(direction)) {
            return;
        }
    }
    if (closed || direction.buffer_size != 0 || direction.source_eof || fd != direction.source ||
        (events & (EPOLLIN | EPOLLRDHUP | EPOLLHUP)) == 0) {
        return;
    }
    while (direction.buffer_size < direction.buffer->size()) {
        const ssize_t count = recv(
            direction.source,
            direction.buffer->data() + direction.buffer_size,
            direction.buffer->size() - direction.buffer_size,
            0);
        if (count > 0) {
            direction.buffer_size += static_cast<std::size_t>(count);
            if (!flushBuffered(direction)) {
                return;
            }
            if (direction.buffer_size != 0) {
                return;
            }
            continue;
        }
        if (count == 0) {
            direction.source_eof = true;
            (void)shutdownDestinationIfDone(direction);
            return;
        }
        if (errno == EINTR) {
            continue;
        }
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return;
        }
        fail();
        return;
    }
}

void TcpSession::handleDirection(TcpDirection& direction, int fd, std::uint32_t events) {
    if (direction.use_splice) {
        handleSplice(direction, fd, events);
    } else {
        handleBuffered(direction, fd, events);
    }
}

void TcpSession::onEvent(int fd, std::uint32_t events) {
    if (closed) {
        return;
    }
    last_activity_ms = monotonicMs();
    if ((events & EPOLLERR) != 0) {
        fail();
        return;
    }
    // EPOLLHUP can be delivered with the final readable bytes. During relay it
    // is an EOF hint, not permission to discard that data; the direction readers
    // drain it and perform their normal half-close sequence. Before relay there
    // is no payload to preserve unless a SOCKS reply is readable.
    if (
        state != State::Relay &&
        (events & EPOLLHUP) != 0 &&
        !((state == State::WaitGreeting || state == State::WaitConnect) && (events & EPOLLIN) != 0)
    ) {
        fail();
        return;
    }
    // A half-close is meaningful only after the SOCKS5 handshake. Closing a
    // handshaking session here also prevents a persistent RDHUP wakeup.
    if (state != State::Relay && (events & EPOLLRDHUP) != 0) {
        fail();
        return;
    }
    if (state == State::ConnectingProxy && fd == proxy_fd && (events & (EPOLLOUT | EPOLLERR | EPOLLHUP)) != 0) {
        int socket_error = 0;
        socklen_t socket_error_size = sizeof(socket_error);
        if (getsockopt(proxy_fd, SOL_SOCKET, SO_ERROR, &socket_error, &socket_error_size) != 0 || socket_error != 0) {
            fail();
            return;
        }
        beginGreeting();
        return;
    }
    if (state == State::SendGreeting || state == State::SendConnect) {
        if (fd == proxy_fd && (events & EPOLLOUT) != 0) {
            flushTransmit();
        }
        return;
    }
    if (state == State::WaitGreeting) {
        if (fd == proxy_fd && (events & (EPOLLIN | EPOLLRDHUP | EPOLLHUP)) != 0) {
            readGreeting();
        }
        return;
    }
    if (state == State::WaitConnect) {
        if (fd == proxy_fd && (events & (EPOLLIN | EPOLLRDHUP | EPOLLHUP)) != 0) {
            readConnectReply();
        }
        return;
    }
    if (state != State::Relay) {
        return;
    }
    if ((events & EPOLLERR) != 0) {
        fail();
        return;
    }
    handleDirection(client_to_proxy, fd, events);
    if (!closed) {
        handleDirection(proxy_to_client, fd, events);
    }
    if (!closed && client_to_proxy.source_eof && client_to_proxy.destination_shutdown &&
        proxy_to_client.source_eof && proxy_to_client.destination_shutdown) {
        fail();
        return;
    }
    if (!closed) {
        updateEvents();
    }
}

TcpBridge::~TcpBridge() {
    close();
}

bool TcpBridge::open(const TcpBridgeConfig& config, CgroupRuntime* runtime) {
    close();
    if (runtime == nullptr || config.socks_host == nullptr || config.socks_port == 0) {
        errno = EINVAL;
        return false;
    }
    in_addr socks_address{};
    if (inet_pton(AF_INET, config.socks_host, &socks_address) != 1) {
        errno = EAFNOSUPPORT;
        return false;
    }
    socks_host_ = config.socks_host;
    socks_port_ = config.socks_port;
    runtime_ = runtime;

    listener_fd_ = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (listener_fd_ < 0) {
        close();
        return false;
    }
    int reuse = 1;
    (void)setsockopt(listener_fd_, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));
    sockaddr_in listener_address{};
    listener_address.sin_family = AF_INET;
    listener_address.sin_addr.s_addr = htonl(INADDR_ANY);
    listener_address.sin_port = htons(config.bridge_port);
    if (bind(listener_fd_, reinterpret_cast<const sockaddr*>(&listener_address), sizeof(listener_address)) != 0 ||
        listen(listener_fd_, 512) != 0) {
        close();
        return false;
    }
    sockaddr_in actual_address{};
    socklen_t actual_address_size = sizeof(actual_address);
    if (getsockname(listener_fd_, reinterpret_cast<sockaddr*>(&actual_address), &actual_address_size) != 0) {
        close();
        return false;
    }
    bridge_port_ = ntohs(actual_address.sin_port);
    if (bridge_port_ == 0) {
        close();
        errno = EADDRNOTAVAIL;
        return false;
    }
    epoll_fd_ = epoll_create1(EPOLL_CLOEXEC);
    if (epoll_fd_ < 0 || !addEpoll(epoll_fd_, listener_fd_, EPOLLIN | EPOLLERR | EPOLLHUP, nullptr)) {
        close();
        return false;
    }
    return true;
}

void TcpBridge::close() {
    for (TcpSession* session = sessions_; session != nullptr; session = session->next) {
        session->markClosed();
    }
    while (sessions_ != nullptr) {
        TcpSession* session = sessions_;
        sessions_ = session->next;
        delete session;
    }
    session_count_ = 0;
    closeFd(&listener_fd_);
    closeFd(&epoll_fd_);
    bridge_port_ = 0;
    runtime_ = nullptr;
    socks_host_ = nullptr;
    socks_port_ = 0;
}

int TcpBridge::run(std::atomic_bool* stop_flag) {
    if (!valid() || runtime_ == nullptr || !runtime_->active()) {
        errno = EINVAL;
        return -1;
    }
    epoll_event events[64]{};
    while (stop_flag == nullptr || !stop_flag->load(std::memory_order_relaxed)) {
        const int count = epoll_wait(epoll_fd_, events, 64, 1000);
        if (count < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        for (int index = 0; index < count; ++index) {
            if (events[index].data.ptr == nullptr) {
                while (true) {
                    sockaddr_storage peer{};
                    socklen_t peer_size = sizeof(peer);
                    const int client_fd = accept(listener_fd_, reinterpret_cast<sockaddr*>(&peer), &peer_size);
                    if (client_fd < 0) {
                        if (errno == EINTR) {
                            continue;
                        }
                        break;
                    }
                    (void)fcntl(client_fd, F_SETFD, FD_CLOEXEC);
                    if (!setNonBlocking(client_fd)) {
                        ::close(client_fd);
                        continue;
                    }
                    if (session_count_ >= kMaxTcpSessions) {
                        ::close(client_fd);
                        continue;
                    }
                    sockaddr_storage local{};
                    socklen_t local_size = sizeof(local);
                    if (getsockname(client_fd, reinterpret_cast<sockaddr*>(&local), &local_size) != 0 ||
                        local.ss_family != AF_INET) {
                        ::close(client_fd);
                        continue;
                    }
                    const auto* local4 = reinterpret_cast<const sockaddr_in*>(&local);
                    OriginalDestination original{};
                    if (!runtime_->takeTcpDestination(
                            bridge_port_,
                            reinterpret_cast<const std::uint8_t*>(&local4->sin_addr.s_addr),
                            &original)) {
                        ::close(client_fd);
                        continue;
                    }

                    const int proxy_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
                    if (proxy_fd < 0) {
                        ::close(client_fd);
                        continue;
                    }
                    sockaddr_in proxy_address{};
                    proxy_address.sin_family = AF_INET;
                    proxy_address.sin_addr.s_addr = inet_addr(socks_host_);
                    proxy_address.sin_port = htons(socks_port_);
                    const int connect_result = connect(
                        proxy_fd,
                        reinterpret_cast<const sockaddr*>(&proxy_address),
                        sizeof(proxy_address));
                    if (connect_result != 0 && errno != EINPROGRESS) {
                        ::close(proxy_fd);
                        ::close(client_fd);
                        continue;
                    }

                    TcpSession* session = new (std::nothrow) TcpSession();
                    if (session == nullptr) {
                        ::close(proxy_fd);
                        ::close(client_fd);
                        continue;
                    }
                    session->owner = this;
                    session->client_fd = client_fd;
                    session->proxy_fd = proxy_fd;
                    session->client_endpoint = {session, client_fd};
                    session->proxy_endpoint = {session, proxy_fd};
                    session->last_activity_ms = monotonicMs();
                    session->original = original;
                    session->client_to_proxy.source = client_fd;
                    session->client_to_proxy.destination = proxy_fd;
                    session->proxy_to_client.source = proxy_fd;
                    session->proxy_to_client.destination = client_fd;
                    int first_pipe[2] = {-1, -1};
                    int second_pipe[2] = {-1, -1};
                    if (pipe2(first_pipe, O_NONBLOCK | O_CLOEXEC) == 0 &&
                        pipe2(second_pipe, O_NONBLOCK | O_CLOEXEC) == 0) {
                        session->client_to_proxy.pipe_read = first_pipe[0];
                        session->client_to_proxy.pipe_write = first_pipe[1];
                        session->proxy_to_client.pipe_read = second_pipe[0];
                        session->proxy_to_client.pipe_write = second_pipe[1];
                    } else {
                        closeFd(&first_pipe[0]);
                        closeFd(&first_pipe[1]);
                        closeFd(&second_pipe[0]);
                        closeFd(&second_pipe[1]);
                        session->client_to_proxy.use_splice = false;
                        session->proxy_to_client.use_splice = false;
                    }
                    if (!session->client_to_proxy.use_splice &&
                        (!session->client_to_proxy.ensureBuffer() || !session->proxy_to_client.ensureBuffer())) {
                        session->markClosed();
                        delete session;
                        continue;
                    }
                    session->next = sessions_;
                    sessions_ = session;
                    ++session_count_;
                    if (!addEpoll(epoll_fd_, proxy_fd, kBaseEvents | EPOLLRDHUP | EPOLLOUT, &session->proxy_endpoint) ||
                        !addEpoll(epoll_fd_, client_fd, kBaseEvents, &session->client_endpoint)) {
                        session->markClosed();
                        continue;
                    }
                    if (connect_result == 0) {
                        session->beginGreeting();
                    }
                }
                continue;
            }
            auto* endpoint = static_cast<TcpEndpoint*>(events[index].data.ptr);
            TcpSession* session = endpoint == nullptr ? nullptr : endpoint->session;
            if (session != nullptr && !session->closed) {
                session->onEvent(endpoint->fd, events[index].events);
            }
        }
        const std::uint64_t now_ms = monotonicMs();
        TcpSession** cursor = &sessions_;
        while (*cursor != nullptr) {
            if ((*cursor)->closed ||
                (now_ms > (*cursor)->last_activity_ms &&
                 now_ms - (*cursor)->last_activity_ms > kSessionIdleMs)) {
                (*cursor)->markClosed();
                TcpSession* dead = *cursor;
                *cursor = dead->next;
                delete dead;
                --session_count_;
            } else {
                cursor = &(*cursor)->next;
            }
        }
    }
    return 0;
}

}  // namespace yumebox::ebpf
