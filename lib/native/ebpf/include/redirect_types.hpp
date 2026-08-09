#pragma once

#include <cstddef>
#include <cstdint>

namespace yumebox::ebpf {

constexpr std::uint8_t kProtocolTcp = 6;
constexpr std::uint8_t kProtocolUdp = 17;

// These layouts are shared by the userspace bridge and the generated
// BPF instructions. Do not replace them with host-dependent socket structs.
struct RedirectKey {
    std::uint8_t family;
    std::uint8_t protocol;
    std::uint16_t listener_port;
    std::uint8_t token_addr[16];
};

struct OriginalDestination {
    std::uint8_t family;
    std::uint8_t protocol;
    std::uint16_t port;
    std::uint8_t addr[16];
    std::uint8_t flags;
    std::uint8_t reserved[3];
    std::uint64_t socket_cookie;
    std::uint32_t uid;
    std::uint32_t reserved_tail;
};

static_assert(sizeof(RedirectKey) == 20, "unexpected eBPF redirect key layout");
static_assert(sizeof(OriginalDestination) == 40, "unexpected eBPF destination layout");
static_assert(offsetof(OriginalDestination, socket_cookie) == 24, "unexpected cookie offset");
static_assert(offsetof(OriginalDestination, uid) == 32, "unexpected uid offset");

}  // namespace yumebox::ebpf
