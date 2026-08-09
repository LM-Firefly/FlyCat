// This file is part of YumeBox.
//
// YumeBox is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation, either version 3 of the
// License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// Copyright (c)  YumeYucca 2025 - Present
//

#pragma once

#include "cgroup_runtime.hpp"

#include <cstddef>
#include <cstdint>
#include <array>
#include <memory>

#include <netinet/in.h>
#include <signal.h>
#include <sys/socket.h>

namespace yumebox::ebpf {

struct UdpBridgeConfig final {
    std::uint16_t bridge_port = 0;
    const char* socks_host = "127.0.0.1";
    std::uint16_t socks_port = 7890;
};

struct UdpSession;
struct UdpBatchStorage;

class UdpBridge final {
public:
    UdpBridge();
    UdpBridge(const UdpBridge&) = delete;
    UdpBridge& operator=(const UdpBridge&) = delete;
    ~UdpBridge();

    bool open(const UdpBridgeConfig& config, CgroupRuntime* runtime);
    int run(volatile sig_atomic_t* stop_flag);
    void close();

    [[nodiscard]] bool valid() const { return listener_fd_ >= 0 && epoll_fd_ >= 0; }

private:
    friend struct UdpSession;

    static constexpr std::size_t kSessionHashCapacity = 512;

    void acceptDatagram();
    void processDatagram(const msghdr& message, std::size_t count);
    void queueToClient(
        const sockaddr_in& client,
        const std::uint8_t token[4],
        const std::uint8_t* payload,
        std::size_t payload_size);
    void flushToClients();
    void cleanupSessions(std::uint64_t now_ms);
    [[nodiscard]] std::size_t sessionBucket(const sockaddr_in& client) const;
    [[nodiscard]] UdpSession* findSession(const sockaddr_in& client) const;
    void insertSession(UdpSession* session);
    void removeSession(UdpSession* session);

    int listener_fd_ = -1;
    int epoll_fd_ = -1;
    std::uint16_t bridge_port_ = 0;
    CgroupRuntime* runtime_ = nullptr;
    const char* socks_host_ = nullptr;
    std::uint16_t socks_port_ = 0;
    UdpSession* sessions_ = nullptr;
    std::array<UdpSession*, kSessionHashCapacity> session_buckets_{};
    std::size_t session_count_ = 0;
    std::size_t response_count_ = 0;
    std::unique_ptr<UdpBatchStorage> batch_;
    std::unique_ptr<std::uint8_t[]> relay_buffer_;
};

}  // namespace yumebox::ebpf
