#pragma once

#include "bpf_syscall.hpp"
#include "redirect_types.hpp"

#include <cstdint>
#include <cstddef>

namespace yumebox::ebpf {

struct CidrRule final {
    std::uint8_t family = 0;
    std::uint8_t prefix_length = 0;
    std::uint8_t address[16]{};
};

class CgroupRuntime final {
public:
    CgroupRuntime() = default;
    CgroupRuntime(const CgroupRuntime&) = delete;
    CgroupRuntime& operator=(const CgroupRuntime&) = delete;
    ~CgroupRuntime();

    bool start(
        const char* cgroup_path,
        std::uint16_t listener_port,
        std::uint32_t bridge_tgid,
        std::uint32_t mihomo_tgid = 0,
        std::uint8_t uid_policy_mode = 0,
        const std::uint32_t* policy_uids = nullptr,
        std::size_t policy_uid_count = 0,
        std::uint8_t dns_mode = kDnsModeBypass,
        std::uint16_t dns_listener_port = 0,
        bool enable_ipv6 = true,
        const CidrRule* bypass_cidrs = nullptr,
        std::size_t bypass_cidr_count = 0);
    void stop();

    bool takeTcpDestination(
        std::uint16_t listener_port,
        const std::uint8_t token_addr[4],
        OriginalDestination* destination);

    bool takeUdpDestination(
        std::uint16_t listener_port,
        const std::uint8_t token_addr[4],
        OriginalDestination* destination);

    bool releaseUdpDestination(
        std::uint16_t listener_port,
        const std::uint8_t token_addr[4]);

    [[nodiscard]] int redirectMapFd() const { return redirect_map_.fd(); }

    [[nodiscard]] bool active() const { return attached_; }

private:
    bool addBypassTgid(std::uint32_t tgid);

    BpfMap cgroup_fd_;
    BpfMap redirect_map_;
    BpfMap bypass_tgid_map_;
    BpfMap uid_policy_map_;
    BpfMap bypass_cidr4_map_;
    BpfMap bypass_cidr6_map_;
    BpfMap connect4_program_;
    BpfMap connect6_program_;
    BpfMap udp4_sendmsg_program_;
    BpfMap udp4_recvmsg_program_;
    BpfMap udp6_sendmsg_program_;
    BpfMap udp6_recvmsg_program_;
    bool attached_ = false;
};

}  // namespace yumebox::ebpf
