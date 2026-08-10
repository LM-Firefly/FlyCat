#include "cgroup_runtime.hpp"

#include "bpf_program_builder.hpp"
#include "redirect_types.hpp"

#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include <fcntl.h>
#include <netinet/in.h>
#include <sys/stat.h>
#include <unistd.h>

namespace yumebox::ebpf {
namespace {

constexpr std::uint32_t kRedirectMapCapacity = 65536;
constexpr std::uint32_t kBypassTgidMapCapacity = 64;
constexpr std::uint32_t kUidPolicyMapCapacity = 4096;
constexpr std::uint32_t kBypassCidrMapCapacity = 256;

struct CidrKey4 final {
    std::uint32_t prefix_length = 0;
    std::uint8_t address[4]{};
};

struct CidrKey6 final {
    std::uint32_t prefix_length = 0;
    std::uint8_t address[16]{};
};

}  // namespace

CgroupRuntime::~CgroupRuntime() {
    stop();
}

bool CgroupRuntime::takeTcpDestination(
    std::uint16_t listener_port,
    const std::uint8_t token_addr[4],
    OriginalDestination* destination) {
    if (!attached_ || token_addr == nullptr || destination == nullptr) {
        errno = EINVAL;
        return false;
    }
    RedirectKey key{};
    key.family = AF_INET;
    key.protocol = kProtocolTcp;
    key.listener_port = listener_port;
    std::memcpy(key.token_addr, token_addr, 4);
    OriginalDestination value{};
    if (lookupMap(redirect_map_.fd(), &key, &value) != 0) {
        return false;
    }
    const int saved_errno = errno;
    if (deleteMap(redirect_map_.fd(), &key) != 0 && errno != ENOENT) {
        errno = saved_errno;
        return false;
    }
    if ((value.family != AF_INET && value.family != AF_INET6) ||
        value.protocol != kProtocolTcp || value.port == 0) {
        errno = EPROTO;
        return false;
    }
    *destination = value;
    return true;
}

bool CgroupRuntime::takeUdpDestination(
    std::uint16_t listener_port,
    const std::uint8_t token_addr[4],
    OriginalDestination* destination) {
    if (!attached_ || token_addr == nullptr || destination == nullptr) {
        errno = EINVAL;
        return false;
    }
    RedirectKey key{};
    key.family = AF_INET;
    key.protocol = kProtocolUdp;
    key.listener_port = listener_port;
    std::memcpy(key.token_addr, token_addr, 4);
    OriginalDestination value{};
    if (lookupMap(redirect_map_.fd(), &key, &value) != 0) {
        return false;
    }
    if ((value.family != AF_INET && value.family != AF_INET6) ||
        value.protocol != kProtocolUdp || value.port == 0) {
        errno = EPROTO;
        return false;
    }
    *destination = value;
    return true;
}

bool CgroupRuntime::releaseUdpDestination(
    std::uint16_t listener_port,
    const std::uint8_t token_addr[4]) {
    if (!redirect_map_.valid() || token_addr == nullptr) {
        errno = EINVAL;
        return false;
    }
    RedirectKey key{};
    key.family = AF_INET;
    key.protocol = kProtocolUdp;
    key.listener_port = listener_port;
    std::memcpy(key.token_addr, token_addr, 4);
    return deleteMap(redirect_map_.fd(), &key) == 0 || errno == ENOENT;
}

bool CgroupRuntime::addBypassTgid(std::uint32_t tgid) {
    if (!bypass_tgid_map_.valid() || tgid == 0) {
        errno = EINVAL;
        return false;
    }
    const std::uint8_t enabled = 1;
    return updateMap(bypass_tgid_map_.fd(), &tgid, &enabled, BPF_ANY) == 0;
}

bool CgroupRuntime::start(
    const char* cgroup_path,
    std::uint16_t listener_port,
    std::uint32_t bridge_tgid,
    std::uint32_t mihomo_tgid,
    std::uint8_t uid_policy_mode,
    const std::uint32_t* policy_uids,
    std::size_t policy_uid_count,
    std::uint8_t dns_mode,
    bool enable_ipv6,
    const CidrRule* bypass_cidrs,
    std::size_t bypass_cidr_count) {
    stop();
    const auto fail = [this](const char* stage) -> bool {
        const int saved_errno = errno == 0 ? EIO : errno;
        std::fprintf(
            stderr,
            "eBPF bridge: cgroup %s failed: errno=%d (%s)\n",
            stage,
            saved_errno,
            std::strerror(saved_errno));
        stop();
        errno = saved_errno;
        return false;
    };
    const char* path = cgroup_path == nullptr || cgroup_path[0] == '\0'
        ? "/sys/fs/cgroup"
        : cgroup_path;
    if (listener_port == 0 || bridge_tgid == 0 || uid_policy_mode > 2 || dns_mode > 1 ||
        policy_uid_count > kUidPolicyMapCapacity ||
        (policy_uid_count != 0 && policy_uids == nullptr) ||
        bypass_cidr_count > kBypassCidrMapCapacity ||
        (bypass_cidr_count != 0 && bypass_cidrs == nullptr) || !isCgroupV2Mount(path)) {
        errno = EINVAL;
        return fail("argument/cgroup validation");
    }

    const int cgroup_fd = open(path, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (cgroup_fd < 0) {
        return fail("open target cgroup");
    }
    cgroup_fd_.reset(cgroup_fd);

    int redirect_fd = createMap(
        BPF_MAP_TYPE_LRU_HASH,
        sizeof(RedirectKey),
        sizeof(OriginalDestination),
        kRedirectMapCapacity,
        0);
    if (redirect_fd < 0 && (errno == EINVAL || errno == ENOTSUP || errno == EOPNOTSUPP)) {
        redirect_fd = createMap(
            BPF_MAP_TYPE_HASH,
            sizeof(RedirectKey),
            sizeof(OriginalDestination),
            kRedirectMapCapacity,
            0);
    }
    if (redirect_fd < 0) {
        return fail("create redirect map");
    }
    redirect_map_.reset(redirect_fd);

    const int bypass_fd = createMap(
        BPF_MAP_TYPE_HASH,
        sizeof(std::uint32_t),
        sizeof(std::uint8_t),
        kBypassTgidMapCapacity,
        0);
    if (bypass_fd < 0) {
        return fail("create bypass map");
    }
    bypass_tgid_map_.reset(bypass_fd);
    if (!addBypassTgid(bridge_tgid) || (mihomo_tgid != 0 && !addBypassTgid(mihomo_tgid))) {
        return fail("populate bypass map");
    }

    if (uid_policy_mode != 0) {
        const int uid_map_fd = createMap(
            BPF_MAP_TYPE_HASH,
            sizeof(std::uint32_t),
            sizeof(std::uint8_t),
            kUidPolicyMapCapacity,
            0);
        if (uid_map_fd < 0) {
            return fail("create UID policy map");
        }
        uid_policy_map_.reset(uid_map_fd);
        const std::uint8_t enabled = 1;
        for (std::size_t index = 0; index < policy_uid_count; ++index) {
            if (updateMap(uid_policy_map_.fd(), &policy_uids[index], &enabled, BPF_ANY) != 0) {
                return fail("populate UID policy map");
            }
        }
    }

    std::size_t cidr4_count = 0;
    std::size_t cidr6_count = 0;
    for (std::size_t index = 0; index < bypass_cidr_count; ++index) {
        const CidrRule& rule = bypass_cidrs[index];
        if (rule.family == AF_INET && rule.prefix_length <= 32) {
            ++cidr4_count;
        } else if (rule.family == AF_INET6 && rule.prefix_length <= 128) {
            ++cidr6_count;
        } else {
            errno = EINVAL;
            return fail("validate bypass CIDR");
        }
    }
    if (cidr4_count != 0) {
        const int map_fd = createMap(
            BPF_MAP_TYPE_LPM_TRIE,
            sizeof(CidrKey4),
            sizeof(std::uint8_t),
            kBypassCidrMapCapacity,
            BPF_F_NO_PREALLOC);
        if (map_fd < 0) {
            return fail("create IPv4 CIDR map");
        }
        bypass_cidr4_map_.reset(map_fd);
    }
    if (cidr6_count != 0) {
        const int map_fd = createMap(
            BPF_MAP_TYPE_LPM_TRIE,
            sizeof(CidrKey6),
            sizeof(std::uint8_t),
            kBypassCidrMapCapacity,
            BPF_F_NO_PREALLOC);
        if (map_fd < 0) {
            return fail("create IPv6 CIDR map");
        }
        bypass_cidr6_map_.reset(map_fd);
    }
    const std::uint8_t enabled = 1;
    for (std::size_t index = 0; index < bypass_cidr_count; ++index) {
        const CidrRule& rule = bypass_cidrs[index];
        if (rule.family == AF_INET) {
            CidrKey4 key{};
            key.prefix_length = rule.prefix_length;
            std::memcpy(key.address, rule.address, sizeof(key.address));
            if (updateMap(bypass_cidr4_map_.fd(), &key, &enabled, BPF_ANY) != 0) {
                return fail("populate IPv4 CIDR map");
            }
        } else {
            CidrKey6 key{};
            key.prefix_length = rule.prefix_length;
            std::memcpy(key.address, rule.address, sizeof(key.address));
            if (updateMap(bypass_cidr6_map_.fd(), &key, &enabled, BPF_ANY) != 0) {
                return fail("populate IPv6 CIDR map");
            }
        }
    }

    const int program_fd = loadTcp4ConnectProgram(
        redirect_map_.fd(),
        bypass_tgid_map_.fd(),
        listener_port,
        uid_policy_map_.fd(),
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_.fd(),
        bypass_cidr6_map_.fd());
    if (program_fd < 0) {
        return fail("load IPv4 connect program");
    }
    connect4_program_.reset(program_fd);
    if (attachProgram(cgroup_fd_.fd(), connect4_program_.fd(), BPF_CGROUP_INET4_CONNECT) != 0) {
        return fail("attach IPv4 connect program");
    }
    const int udp_sendmsg_fd = loadUdp4SendmsgProgram(
        redirect_map_.fd(),
        bypass_tgid_map_.fd(),
        listener_port,
        uid_policy_map_.fd(),
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_.fd(),
        bypass_cidr6_map_.fd());
    if (udp_sendmsg_fd < 0) {
        return fail("load IPv4 UDP sendmsg program");
    }
    udp4_sendmsg_program_.reset(udp_sendmsg_fd);
    if (attachProgram(cgroup_fd_.fd(), udp4_sendmsg_program_.fd(), BPF_CGROUP_UDP4_SENDMSG) != 0) {
        return fail("attach IPv4 UDP sendmsg program");
    }
    const int udp_recvmsg_fd = loadUdp4RecvmsgProgram(redirect_map_.fd(), listener_port);
    if (udp_recvmsg_fd < 0) {
        return fail("load IPv4 UDP recvmsg program");
    }
    udp4_recvmsg_program_.reset(udp_recvmsg_fd);
    if (attachProgram(cgroup_fd_.fd(), udp4_recvmsg_program_.fd(), BPF_CGROUP_UDP4_RECVMSG) != 0) {
        return fail("attach IPv4 UDP recvmsg program");
    }
    if (enable_ipv6) {
        const int connect6_fd = loadIpv6ConnectProgram(
            redirect_map_.fd(),
            bypass_tgid_map_.fd(),
            listener_port,
            uid_policy_map_.fd(),
            uid_policy_mode,
            dns_mode,
            bypass_cidr4_map_.fd(),
            bypass_cidr6_map_.fd());
        if (connect6_fd < 0) {
            return fail("load IPv6 connect program");
        }
        connect6_program_.reset(connect6_fd);
        if (attachProgram(cgroup_fd_.fd(), connect6_program_.fd(), BPF_CGROUP_INET6_CONNECT) != 0) {
            return fail("attach IPv6 connect program");
        }
        const int udp6_sendmsg_fd = loadUdp6SendmsgProgram(
            redirect_map_.fd(),
            bypass_tgid_map_.fd(),
            listener_port,
            uid_policy_map_.fd(),
            uid_policy_mode,
            dns_mode,
            bypass_cidr4_map_.fd(),
            bypass_cidr6_map_.fd());
        if (udp6_sendmsg_fd < 0) {
            return fail("load IPv6 UDP sendmsg program");
        }
        udp6_sendmsg_program_.reset(udp6_sendmsg_fd);
        if (attachProgram(cgroup_fd_.fd(), udp6_sendmsg_program_.fd(), BPF_CGROUP_UDP6_SENDMSG) != 0) {
            return fail("attach IPv6 UDP sendmsg program");
        }
        const int udp6_recvmsg_fd = loadUdp6RecvmsgProgram(redirect_map_.fd(), listener_port);
        if (udp6_recvmsg_fd < 0) {
            return fail("load IPv6 UDP recvmsg program");
        }
        udp6_recvmsg_program_.reset(udp6_recvmsg_fd);
        if (attachProgram(cgroup_fd_.fd(), udp6_recvmsg_program_.fd(), BPF_CGROUP_UDP6_RECVMSG) != 0) {
            return fail("attach IPv6 UDP recvmsg program");
        }
    }
    attached_ = true;
    return true;
}

void CgroupRuntime::stop() {
    // A later program may fail to attach after an earlier one succeeded. Use
    // the fd presence as the cleanup guard so partial startup never leaves a
    // cgroup hook behind.
    if (cgroup_fd_.valid()) {
        const auto detach = [this](int program_fd, enum bpf_attach_type attach_type) {
            if (detachProgram(cgroup_fd_.fd(), program_fd, attach_type) != 0) {
                std::fprintf(
                    stderr,
                    "eBPF bridge: detach cgroup hook type=%d failed: errno=%d (%s)\n",
                    static_cast<int>(attach_type),
                    errno,
                    std::strerror(errno));
            }
        };
        if (udp6_recvmsg_program_.valid()) {
            detach(udp6_recvmsg_program_.fd(), BPF_CGROUP_UDP6_RECVMSG);
        }
        if (udp6_sendmsg_program_.valid()) {
            detach(udp6_sendmsg_program_.fd(), BPF_CGROUP_UDP6_SENDMSG);
        }
        if (connect6_program_.valid()) {
            detach(connect6_program_.fd(), BPF_CGROUP_INET6_CONNECT);
        }
        if (udp4_recvmsg_program_.valid()) {
            detach(udp4_recvmsg_program_.fd(), BPF_CGROUP_UDP4_RECVMSG);
        }
        if (udp4_sendmsg_program_.valid()) {
            detach(udp4_sendmsg_program_.fd(), BPF_CGROUP_UDP4_SENDMSG);
        }
        if (connect4_program_.valid()) {
            detach(connect4_program_.fd(), BPF_CGROUP_INET4_CONNECT);
        }
    }
    attached_ = false;
    udp4_recvmsg_program_.reset();
    udp4_sendmsg_program_.reset();
    connect4_program_.reset();
    uid_policy_map_.reset();
    bypass_cidr4_map_.reset();
    bypass_cidr6_map_.reset();
    udp6_recvmsg_program_.reset();
    udp6_sendmsg_program_.reset();
    connect6_program_.reset();
    bypass_tgid_map_.reset();
    redirect_map_.reset();
    cgroup_fd_.reset();
}

}  // namespace yumebox::ebpf
