#pragma once

#include "cgroup_runtime.hpp"

#include <cstdint>
#include <cstddef>

#include <signal.h>

namespace yumebox::ebpf {

struct TcpBridgeConfig final {
    std::uint16_t bridge_port = 0;
    const char* socks_host = "127.0.0.1";
    std::uint16_t socks_port = 7890;
};

struct TcpSession;

class TcpBridge final {
public:
    TcpBridge() = default;
    TcpBridge(const TcpBridge&) = delete;
    TcpBridge& operator=(const TcpBridge&) = delete;
    ~TcpBridge();

    bool open(const TcpBridgeConfig& config, CgroupRuntime* runtime);
    int run(volatile sig_atomic_t* stop_flag);
    void close();

    [[nodiscard]] std::uint16_t port() const { return bridge_port_; }
    [[nodiscard]] bool valid() const { return listener_fd_ >= 0 && epoll_fd_ >= 0; }

private:
    friend struct TcpSession;

    int listener_fd_ = -1;
    int epoll_fd_ = -1;
    std::uint16_t bridge_port_ = 0;
    CgroupRuntime* runtime_ = nullptr;
    const char* socks_host_ = nullptr;
    std::uint16_t socks_port_ = 0;
    TcpSession* sessions_ = nullptr;
    std::size_t session_count_ = 0;
};

}  // namespace yumebox::ebpf
