#include "bpf_program_builder.hpp"

#include "bpf_syscall.hpp"
#include "redirect_types.hpp"

#include <arpa/inet.h>

#include <array>
#include <cerrno>
#include <cstddef>
#include <cstdint>

#include <unistd.h>

namespace yumebox::ebpf {
namespace {

constexpr std::size_t kMaxInstructions = 256;
constexpr int kTgidStackOffset = -4;
constexpr int kUidStackOffset = -8;
constexpr int kKeyStackOffset = -32;
constexpr int kValueStackOffset = -80;
// struct bpf_sock_addr is an eBPF context ABI, not a host C++ layout. Some
// Android NDK linux/bpf.h revisions describe user_ip6 with a different shape,
// which shifts user_port/protocol and makes the kernel reject the program.
constexpr int kSockAddrUserIp4Offset = 4;
constexpr int kSockAddrUserIp6Offset = 8;
constexpr int kSockAddrUserPortOffset = 24;
constexpr int kSockAddrProtocolOffset = 36;
constexpr std::uint32_t kTokenPrefixHost = 0x7f800000U;
constexpr std::uint32_t kTokenHostMask = 0x007fffffU;

class Builder final {
public:
    void emit(const struct bpf_insn& instruction) {
        if (count_ >= instructions_.size()) {
            overflow_ = true;
            return;
        }
        instructions_[count_++] = instruction;
    }

    [[nodiscard]] std::size_t emitJump(const struct bpf_insn& instruction) {
        const std::size_t index = count_;
        emit(instruction);
        return index;
    }

    void patchJump(std::size_t jump_index, std::size_t target_index) {
        instructions_[jump_index].off = static_cast<short>(target_index - jump_index - 1U);
    }

    [[nodiscard]] bool overflowed() const { return overflow_; }
    [[nodiscard]] const struct bpf_insn* data() const { return instructions_.data(); }
    [[nodiscard]] std::size_t size() const { return count_; }

private:
    std::array<struct bpf_insn, kMaxInstructions> instructions_{};
    std::size_t count_ = 0;
    bool overflow_ = false;
};

struct bpf_insn alu64Imm(std::uint8_t operation, std::uint8_t destination, std::int32_t immediate) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ALU64 | BPF_OP(operation) | BPF_K);
    instruction.dst_reg = destination;
    instruction.imm = immediate;
    return instruction;
}

struct bpf_insn alu32Imm(std::uint8_t operation, std::uint8_t destination, std::int32_t immediate) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ALU | BPF_OP(operation) | BPF_K);
    instruction.dst_reg = destination;
    instruction.imm = immediate;
    return instruction;
}

struct bpf_insn alu64Reg(std::uint8_t operation, std::uint8_t destination, std::uint8_t source) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ALU64 | BPF_OP(operation) | BPF_X);
    instruction.dst_reg = destination;
    instruction.src_reg = source;
    return instruction;
}

struct bpf_insn alu32Reg(std::uint8_t operation, std::uint8_t destination, std::uint8_t source) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ALU | BPF_OP(operation) | BPF_X);
    instruction.dst_reg = destination;
    instruction.src_reg = source;
    return instruction;
}

struct bpf_insn loadX(std::uint8_t size, std::uint8_t destination, std::uint8_t source, short offset) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_LDX | BPF_SIZE(size) | BPF_MEM);
    instruction.dst_reg = destination;
    instruction.src_reg = source;
    instruction.off = offset;
    return instruction;
}

struct bpf_insn storeX(std::uint8_t size, std::uint8_t destination, std::uint8_t source, short offset) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_STX | BPF_SIZE(size) | BPF_MEM);
    instruction.dst_reg = destination;
    instruction.src_reg = source;
    instruction.off = offset;
    return instruction;
}

struct bpf_insn storeImm(std::uint8_t size, std::uint8_t destination, short offset, std::int32_t immediate) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ST | BPF_SIZE(size) | BPF_MEM);
    instruction.dst_reg = destination;
    instruction.off = offset;
    instruction.imm = immediate;
    return instruction;
}

struct bpf_insn jumpImm(std::uint8_t operation, std::uint8_t destination, std::int32_t immediate, short offset) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_JMP | BPF_OP(operation) | BPF_K);
    instruction.dst_reg = destination;
    instruction.off = offset;
    instruction.imm = immediate;
    return instruction;
}

struct bpf_insn jumpAlways(short offset) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_JMP | BPF_JA);
    instruction.off = offset;
    return instruction;
}

struct bpf_insn callHelper(std::int32_t helper) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_JMP | BPF_CALL);
    instruction.imm = helper;
    return instruction;
}

struct bpf_insn exitInstruction() {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_JMP | BPF_EXIT);
    return instruction;
}

struct bpf_insn endianToBig(std::uint8_t destination, std::int32_t size) {
    struct bpf_insn instruction{};
    instruction.code = static_cast<std::uint8_t>(BPF_ALU | BPF_END | BPF_TO_BE);
    instruction.dst_reg = destination;
    instruction.imm = size;
    return instruction;
}

void emitMapFd(Builder& builder, int destination, int map_fd) {
    struct bpf_insn load_instruction{};
    load_instruction.code = static_cast<std::uint8_t>(BPF_LD | BPF_DW | BPF_IMM);
    load_instruction.dst_reg = static_cast<std::uint8_t>(destination);
    load_instruction.src_reg = BPF_PSEUDO_MAP_FD;
    load_instruction.imm = map_fd;
    builder.emit(load_instruction);
    struct bpf_insn continuation{};
    builder.emit(continuation);
}

std::size_t emitReturn(Builder& builder, std::int32_t result) {
    const std::size_t label = builder.size();
    builder.emit(alu64Imm(BPF_MOV, BPF_REG_0, result));
    builder.emit(exitInstruction());
    return label;
}

void emitZero(Builder& builder, int base_offset, std::size_t size) {
    for (std::size_t offset = 0; offset < size; offset += sizeof(std::uint32_t)) {
        builder.emit(storeImm(
            BPF_W,
            BPF_REG_10,
            static_cast<short>(base_offset + static_cast<int>(offset)),
            0));
    }
}

void emitUidPolicy(
    Builder& builder,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::array<std::size_t, 24>* allow_jumps,
    std::size_t* allow_jump_count) {
    if (uid_policy_map_fd < 0 || uid_policy_mode == 0) {
        return;
    }
    builder.emit(callHelper(BPF_FUNC_get_current_uid_gid));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_0, kUidStackOffset));
    emitMapFd(builder, BPF_REG_1, uid_policy_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kUidStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    if (uid_policy_mode == 1) {
        (*allow_jumps)[(*allow_jump_count)++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_0, 0, 0));
    } else if (uid_policy_mode == 2) {
        (*allow_jumps)[(*allow_jump_count)++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0, 0));
    }
}

void emitIpv4CidrBypass(
    Builder& builder,
    int cidr_map_fd,
    std::uint32_t address,
    std::array<std::size_t, 24>* allow_jumps,
    std::size_t* allow_jump_count) {
    if (cidr_map_fd < 0) return;
    emitZero(builder, kKeyStackOffset, 8);
    builder.emit(storeImm(BPF_W, BPF_REG_10, kKeyStackOffset, 32));
    builder.emit(storeX(BPF_W, BPF_REG_10, address, kKeyStackOffset + 4));
    emitMapFd(builder, BPF_REG_1, cidr_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    (*allow_jumps)[(*allow_jump_count)++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0, 0));
}

void emitIpv6CidrBypass(
    Builder& builder,
    int cidr_map_fd,
    std::uint32_t word0,
    std::uint32_t word1,
    std::uint32_t word2,
    std::uint32_t word3,
    std::array<std::size_t, 24>* allow_jumps,
    std::size_t* allow_jump_count) {
    if (cidr_map_fd < 0) return;
    emitZero(builder, kKeyStackOffset, 20);
    builder.emit(storeImm(BPF_W, BPF_REG_10, kKeyStackOffset, 128));
    builder.emit(storeX(BPF_W, BPF_REG_10, word0, kKeyStackOffset + 4));
    builder.emit(storeX(BPF_W, BPF_REG_10, word1, kKeyStackOffset + 8));
    builder.emit(storeX(BPF_W, BPF_REG_10, word2, kKeyStackOffset + 12));
    builder.emit(storeX(BPF_W, BPF_REG_10, word3, kKeyStackOffset + 16));
    emitMapFd(builder, BPF_REG_1, cidr_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    (*allow_jumps)[(*allow_jump_count)++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0, 0));
}

}  // namespace

int loadIpv4RedirectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    std::uint8_t protocol_filter,
    enum bpf_attach_type attach_type,
    const char* program_name,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    (void)bypass_cidr6_map_fd;
    if (redirect_map_fd < 0 || bypass_tgid_map_fd < 0 || listener_port == 0) {
        errno = EINVAL;
        return -1;
    }

    Builder builder;
    std::array<std::size_t, 24> allow_jumps{};
    std::size_t allow_jump_count = 0;

    builder.emit(alu64Reg(BPF_MOV, BPF_REG_6, BPF_REG_1));

    // Root bridge and mihomo are in the same cgroup as intercepted apps. A
    // TGID map is used instead of a single immediate so the launcher can add
    // the mihomo PID after both processes are known.
    builder.emit(callHelper(BPF_FUNC_get_current_pid_tgid));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_7, BPF_REG_0));
    builder.emit(alu64Imm(BPF_RSH, BPF_REG_7, 32));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kTgidStackOffset));
    emitMapFd(builder, BPF_REG_1, bypass_tgid_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kTgidStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    const std::size_t no_bypass = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_0, 0, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpAlways(0));
    builder.patchJump(no_bypass, builder.size());
    emitUidPolicy(builder, uid_policy_map_fd, uid_policy_mode, &allow_jumps, &allow_jump_count);

    if (protocol_filter == 0) {
        // CONNECT is shared by TCP and connected UDP. Keep both protocols in
        // one program so kernels without multi-attach support do not need two
        // programs on BPF_CGROUP_INET4_CONNECT.
        builder.emit(loadX(BPF_W, BPF_REG_5, BPF_REG_6, kSockAddrProtocolOffset));
        const std::size_t is_tcp = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_5, kProtocolTcp, 0));
        allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_5, kProtocolUdp, 0));
        builder.patchJump(is_tcp, builder.size());
    } else {
        builder.emit(alu64Imm(BPF_MOV, BPF_REG_5, protocol_filter));
    }
    // Helpers clobber R1-R5; keep the selected protocol across get_prandom_u32
    // and the map update setup.
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kTgidStackOffset));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp4Offset));
    builder.emit(loadX(BPF_W, BPF_REG_8, BPF_REG_6, kSockAddrUserPortOffset));
    if (dns_mode == 1) {
        const std::size_t dns_port = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_8, htons(53), 0));
        allow_jumps[allow_jump_count++] = dns_port;
    }
    emitIpv4CidrBypass(
        builder,
        bypass_cidr4_map_fd,
        BPF_REG_7,
        &allow_jumps,
        &allow_jump_count);

    // Keep local, unspecified and multicast destinations outside the bridge.
    // This also prevents a manually addressed token connection from creating
    // a second redirect entry.
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_7, 0, 0));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_7));
    builder.emit(endianToBig(BPF_REG_2, 32));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_3, BPF_REG_2));
    builder.emit(alu64Imm(BPF_RSH, BPF_REG_3, 24));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_3, 127, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JGE, BPF_REG_3, 224, 0));

    builder.emit(callHelper(BPF_FUNC_get_prandom_u32));
    builder.emit(alu32Reg(BPF_MOV, BPF_REG_9, BPF_REG_0));
    builder.emit(alu32Imm(BPF_AND, BPF_REG_9, static_cast<std::int32_t>(kTokenHostMask)));
    builder.emit(alu32Imm(BPF_OR, BPF_REG_9, static_cast<std::int32_t>(kTokenPrefixHost)));
    builder.emit(endianToBig(BPF_REG_9, 32));

    emitZero(builder, kKeyStackOffset, sizeof(RedirectKey));
    emitZero(builder, kValueStackOffset, sizeof(OriginalDestination));
    builder.emit(loadX(BPF_B, BPF_REG_5, BPF_REG_10, kTgidStackOffset));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 0, AF_INET));
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kKeyStackOffset + 1));
    builder.emit(storeImm(BPF_H, BPF_REG_10, kKeyStackOffset + 2, listener_port));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_9, kKeyStackOffset + 4));

    builder.emit(storeImm(BPF_B, BPF_REG_10, kValueStackOffset + 0, AF_INET));
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kValueStackOffset + 1));
    builder.emit(endianToBig(BPF_REG_8, 16));
    builder.emit(storeX(BPF_H, BPF_REG_10, BPF_REG_8, kValueStackOffset + 2));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kValueStackOffset + 4));

    emitMapFd(builder, BPF_REG_1, redirect_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_3, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_3, kValueStackOffset));
    builder.emit(alu64Imm(BPF_MOV, BPF_REG_4, BPF_ANY));
    builder.emit(callHelper(BPF_FUNC_map_update_elem));
    const std::size_t map_update_failed = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0, 0));

    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_9, kSockAddrUserIp4Offset));
    builder.emit(alu64Imm(BPF_MOV, BPF_REG_0, htons(listener_port)));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_0, kSockAddrUserPortOffset));
    const std::size_t allow_label = emitReturn(builder, 1);
    const std::size_t drop_label = emitReturn(builder, 0);

    builder.patchJump(map_update_failed, drop_label);
    for (std::size_t index = 0; index < allow_jump_count; ++index) {
        builder.patchJump(allow_jumps[index], allow_label);
    }
    if (builder.overflowed()) {
        errno = EMSGSIZE;
        return -1;
    }
    return loadProgram(
        builder.data(),
        builder.size(),
        program_name,
        BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
        attach_type,
        true);
}

int loadTcp4ConnectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    (void)bypass_cidr4_map_fd;
    return loadIpv4RedirectProgram(
        redirect_map_fd,
        bypass_tgid_map_fd,
        listener_port,
        0,
        BPF_CGROUP_INET4_CONNECT,
        "yb_sock4",
        uid_policy_map_fd,
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_fd,
        bypass_cidr6_map_fd);
}


int loadIpv6RedirectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    std::uint8_t protocol_filter,
    enum bpf_attach_type attach_type,
    const char* program_name,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    (void)bypass_cidr4_map_fd;
    if (redirect_map_fd < 0 || bypass_tgid_map_fd < 0 || listener_port == 0) {
        errno = EINVAL;
        return -1;
    }

    Builder builder;
    std::array<std::size_t, 24> allow_jumps{};
    std::size_t allow_jump_count = 0;
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_6, BPF_REG_1));

    builder.emit(callHelper(BPF_FUNC_get_current_pid_tgid));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_7, BPF_REG_0));
    builder.emit(alu64Imm(BPF_RSH, BPF_REG_7, 32));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kTgidStackOffset));
    emitMapFd(builder, BPF_REG_1, bypass_tgid_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kTgidStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    const std::size_t no_bypass = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_0, 0, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpAlways(0));
    builder.patchJump(no_bypass, builder.size());
    emitUidPolicy(builder, uid_policy_map_fd, uid_policy_mode, &allow_jumps, &allow_jump_count);

    if (protocol_filter == 0) {
        builder.emit(loadX(BPF_W, BPF_REG_5, BPF_REG_6, kSockAddrProtocolOffset));
        const std::size_t is_tcp = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_5, kProtocolTcp, 0));
        allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_5, kProtocolUdp, 0));
        builder.patchJump(is_tcp, builder.size());
    } else {
        builder.emit(alu64Imm(BPF_MOV, BPF_REG_5, protocol_filter));
    }
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kTgidStackOffset));

    // Preserve the IPv6 destination before any helper call. The address fields
    // are network-byte-order words in struct bpf_sock_addr.
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kValueStackOffset + 4));
    builder.emit(loadX(BPF_W, BPF_REG_8, BPF_REG_6, kSockAddrUserIp6Offset + 4));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_8, kValueStackOffset + 8));
    builder.emit(loadX(BPF_W, BPF_REG_9, BPF_REG_6, kSockAddrUserIp6Offset + 8));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_9, kValueStackOffset + 12));
    builder.emit(loadX(BPF_W, BPF_REG_0, BPF_REG_6, kSockAddrUserIp6Offset + 12));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_0, kValueStackOffset + 16));

    // Do not redirect unspecified, loopback, multicast, or an IPv4-mapped
    // loopback address. The latter also prevents a manually addressed token
    // from recursively entering the bridge.
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_7));
    builder.emit(endianToBig(BPF_REG_2, 32));
    builder.emit(alu64Imm(BPF_RSH, BPF_REG_2, 24));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_2, 255, 0));

    const std::size_t first_nonzero = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_7, 0, 0));
    const std::size_t second_nonzero = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_8, 0, 0));
    const std::size_t third_nonzero = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_9, 0, 0));
    const std::size_t not_loopback = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0x01000000, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpAlways(0));
    const std::size_t after_local = builder.size();
    builder.patchJump(first_nonzero, after_local);
    builder.patchJump(second_nonzero, after_local);
    builder.patchJump(third_nonzero, after_local);
    builder.patchJump(not_loopback, after_local);

    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset + 8));
    const std::size_t not_mapped = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_7, static_cast<std::int32_t>(0xffff0000U), 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset + 12));
    builder.emit(endianToBig(BPF_REG_7, 32));
    builder.emit(alu64Imm(BPF_RSH, BPF_REG_7, 24));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_7, 127, 0));
    builder.patchJump(not_mapped, builder.size());

    builder.emit(loadX(BPF_W, BPF_REG_8, BPF_REG_6, kSockAddrUserPortOffset));
    if (dns_mode == 1) {
        const std::size_t dns_port = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_8, htons(53), 0));
        allow_jumps[allow_jump_count++] = dns_port;
    }
    emitIpv6CidrBypass(
        builder,
        bypass_cidr6_map_fd,
        BPF_REG_7,
        BPF_REG_8,
        BPF_REG_9,
        BPF_REG_0,
        &allow_jumps,
        &allow_jump_count);
    emitZero(builder, kKeyStackOffset, sizeof(RedirectKey));
    emitZero(builder, kValueStackOffset, sizeof(OriginalDestination));
    builder.emit(loadX(BPF_B, BPF_REG_5, BPF_REG_10, kTgidStackOffset));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 0, AF_INET));
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kKeyStackOffset + 1));
    builder.emit(storeImm(BPF_H, BPF_REG_10, kKeyStackOffset + 2, listener_port));

    builder.emit(storeImm(BPF_B, BPF_REG_10, kValueStackOffset + 0, AF_INET6));
    builder.emit(storeX(BPF_B, BPF_REG_10, BPF_REG_5, kValueStackOffset + 1));
    builder.emit(endianToBig(BPF_REG_8, 16));
    builder.emit(storeX(BPF_H, BPF_REG_10, BPF_REG_8, kValueStackOffset + 2));

    builder.emit(callHelper(BPF_FUNC_get_prandom_u32));
    builder.emit(alu32Reg(BPF_MOV, BPF_REG_9, BPF_REG_0));
    builder.emit(alu32Imm(BPF_AND, BPF_REG_9, static_cast<std::int32_t>(kTokenHostMask)));
    builder.emit(alu32Imm(BPF_OR, BPF_REG_9, static_cast<std::int32_t>(kTokenPrefixHost)));
    builder.emit(endianToBig(BPF_REG_9, 32));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_9, kKeyStackOffset + 4));

    emitMapFd(builder, BPF_REG_1, redirect_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_3, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_3, kValueStackOffset));
    builder.emit(alu64Imm(BPF_MOV, BPF_REG_4, BPF_ANY));
    builder.emit(callHelper(BPF_FUNC_map_update_elem));
    const std::size_t map_update_failed = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_0, 0, 0));

    builder.emit(storeImm(BPF_W, BPF_REG_6, kSockAddrUserIp6Offset, 0));
    builder.emit(storeImm(BPF_W, BPF_REG_6, kSockAddrUserIp6Offset + 4, 0));
    builder.emit(storeImm(BPF_W, BPF_REG_6, kSockAddrUserIp6Offset + 8, static_cast<std::int32_t>(0xffff0000U)));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_9, kSockAddrUserIp6Offset + 12));
    builder.emit(alu64Imm(BPF_MOV, BPF_REG_0, htons(listener_port)));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_0, kSockAddrUserPortOffset));
    const std::size_t allow_label = emitReturn(builder, 1);
    const std::size_t drop_label = emitReturn(builder, 0);

    builder.patchJump(map_update_failed, drop_label);
    for (std::size_t index = 0; index < allow_jump_count; ++index) {
        builder.patchJump(allow_jumps[index], allow_label);
    }
    if (builder.overflowed()) {
        errno = EMSGSIZE;
        return -1;
    }
    return loadProgram(
        builder.data(),
        builder.size(),
        program_name,
        BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
        attach_type,
        true);
}

int loadIpv6ConnectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    return loadIpv6RedirectProgram(
        redirect_map_fd,
        bypass_tgid_map_fd,
        listener_port,
        0,
        BPF_CGROUP_INET6_CONNECT,
        "yb_sock6",
        uid_policy_map_fd,
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_fd,
        bypass_cidr6_map_fd);
}

int loadUdp6SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    return loadIpv6RedirectProgram(
        redirect_map_fd,
        bypass_tgid_map_fd,
        listener_port,
        kProtocolUdp,
        BPF_CGROUP_UDP6_SENDMSG,
        "yb_udp6_send",
        uid_policy_map_fd,
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_fd,
        bypass_cidr6_map_fd);
}

int loadUdp6RecvmsgProgram(int redirect_map_fd, std::uint16_t listener_port) {
    if (redirect_map_fd < 0 || listener_port == 0) {
        errno = EINVAL;
        return -1;
    }

    Builder builder;
    std::array<std::size_t, 6> allow_jumps{};
    std::size_t allow_jump_count = 0;
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_6, BPF_REG_1));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_7, 0, 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset + 4));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_7, 0, 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset + 8));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_7, static_cast<std::int32_t>(0xffff0000U), 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp6Offset + 12));

    emitZero(builder, kKeyStackOffset, sizeof(RedirectKey));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 0, AF_INET));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 1, kProtocolUdp));
    builder.emit(storeImm(BPF_H, BPF_REG_10, kKeyStackOffset + 2, listener_port));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kKeyStackOffset + 4));
    emitMapFd(builder, BPF_REG_1, redirect_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_0, 0, 0));
    builder.emit(loadX(BPF_B, BPF_REG_2, BPF_REG_0, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_2, AF_INET6, 0));
    builder.emit(loadX(BPF_B, BPF_REG_2, BPF_REG_0, 1));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_2, kProtocolUdp, 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_0, 4));
    builder.emit(loadX(BPF_W, BPF_REG_8, BPF_REG_0, 8));
    builder.emit(loadX(BPF_W, BPF_REG_9, BPF_REG_0, 12));
    builder.emit(loadX(BPF_W, BPF_REG_5, BPF_REG_0, 16));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_7, kSockAddrUserIp6Offset));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_8, kSockAddrUserIp6Offset + 4));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_9, kSockAddrUserIp6Offset + 8));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_5, kSockAddrUserIp6Offset + 12));
    builder.emit(loadX(BPF_H, BPF_REG_8, BPF_REG_0, 2));
    builder.emit(endianToBig(BPF_REG_8, 16));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_8, kSockAddrUserPortOffset));
    const std::size_t allow_label = emitReturn(builder, 1);
    for (std::size_t index = 0; index < allow_jump_count; ++index) {
        builder.patchJump(allow_jumps[index], allow_label);
    }
    if (builder.overflowed()) {
        errno = EMSGSIZE;
        return -1;
    }
    return loadProgram(
        builder.data(),
        builder.size(),
        "yb_udp6_recv",
        BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
        BPF_CGROUP_UDP6_RECVMSG,
        true);
}

int loadUdp4SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd,
    std::uint8_t uid_policy_mode,
    std::uint8_t dns_mode,
    int bypass_cidr4_map_fd,
    int bypass_cidr6_map_fd) {
    return loadIpv4RedirectProgram(
        redirect_map_fd,
        bypass_tgid_map_fd,
        listener_port,
        kProtocolUdp,
        BPF_CGROUP_UDP4_SENDMSG,
        "yb_udp4_send",
        uid_policy_map_fd,
        uid_policy_mode,
        dns_mode,
        bypass_cidr4_map_fd,
        bypass_cidr6_map_fd);
}

int loadUdp4RecvmsgProgram(int redirect_map_fd, std::uint16_t listener_port) {
    if (redirect_map_fd < 0 || listener_port == 0) {
        errno = EINVAL;
        return -1;
    }

    Builder builder;
    std::array<std::size_t, 3> allow_jumps{};
    std::size_t allow_jump_count = 0;
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_6, BPF_REG_1));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_6, kSockAddrUserIp4Offset));

    emitZero(builder, kKeyStackOffset, sizeof(RedirectKey));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 0, AF_INET));
    builder.emit(storeImm(BPF_B, BPF_REG_10, kKeyStackOffset + 1, kProtocolUdp));
    builder.emit(storeImm(BPF_H, BPF_REG_10, kKeyStackOffset + 2, listener_port));
    builder.emit(storeX(BPF_W, BPF_REG_10, BPF_REG_7, kKeyStackOffset + 4));

    emitMapFd(builder, BPF_REG_1, redirect_map_fd);
    builder.emit(alu64Reg(BPF_MOV, BPF_REG_2, BPF_REG_10));
    builder.emit(alu64Imm(BPF_ADD, BPF_REG_2, kKeyStackOffset));
    builder.emit(callHelper(BPF_FUNC_map_lookup_elem));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JEQ, BPF_REG_0, 0, 0));
    builder.emit(loadX(BPF_B, BPF_REG_2, BPF_REG_0, 0));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_2, AF_INET, 0));
    builder.emit(loadX(BPF_B, BPF_REG_2, BPF_REG_0, 1));
    allow_jumps[allow_jump_count++] = builder.emitJump(jumpImm(BPF_JNE, BPF_REG_2, kProtocolUdp, 0));
    builder.emit(loadX(BPF_W, BPF_REG_7, BPF_REG_0, 4));
    builder.emit(loadX(BPF_H, BPF_REG_8, BPF_REG_0, 2));
    builder.emit(endianToBig(BPF_REG_8, 16));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_7, kSockAddrUserIp4Offset));
    builder.emit(storeX(BPF_W, BPF_REG_6, BPF_REG_8, kSockAddrUserPortOffset));

    const std::size_t allow_label = emitReturn(builder, 1);
    for (std::size_t index = 0; index < allow_jump_count; ++index) {
        builder.patchJump(allow_jumps[index], allow_label);
    }
    if (builder.overflowed()) {
        errno = EMSGSIZE;
        return -1;
    }
    return loadProgram(
        builder.data(),
        builder.size(),
        "yb_udp4_recv",
        BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
        BPF_CGROUP_UDP4_RECVMSG,
        true);
}

bool probeSocketAddressPrograms() {
    const struct bpf_insn instructions[] = {
        loadX(BPF_W, BPF_REG_0, BPF_REG_1, kSockAddrUserPortOffset),
        alu64Imm(BPF_MOV, BPF_REG_0, 1),
        exitInstruction(),
    };
    const enum bpf_attach_type attach_types[] = {
        BPF_CGROUP_INET4_CONNECT,
        BPF_CGROUP_UDP4_SENDMSG,
        BPF_CGROUP_UDP4_RECVMSG,
        BPF_CGROUP_INET6_CONNECT,
        BPF_CGROUP_UDP6_SENDMSG,
        BPF_CGROUP_UDP6_RECVMSG,
    };
    for (const enum bpf_attach_type attach_type : attach_types) {
        const int program_fd = loadProgram(
            instructions,
            sizeof(instructions) / sizeof(instructions[0]),
            "yb_probe",
            BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
            attach_type,
            false);
        if (program_fd < 0) return false;
        ::close(program_fd);
    }
    return true;
}

}  // namespace yumebox::ebpf
