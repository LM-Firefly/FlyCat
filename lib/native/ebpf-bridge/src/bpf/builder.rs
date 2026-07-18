// eBPF bytecode builder — generates socket-address programs at runtime.
//
// Mirrors the C++ bpf_program_builder.cpp instruction-for-instruction.

use std::os::unix::io::RawFd;
use super::syscall::*;

// ── Constants ───────────────────────────────────────────────────────────────

const MAX_INSNS: usize = 256;
const MAX_ALLOW_JUMPS: usize = 24;

// Stack offsets (negative from r10/fp)
const TGID_STACK_OFFSET: i16 = -4;
const UID_STACK_OFFSET: i16 = -8;
const KEY_STACK_OFFSET: i16 = -32;
const VALUE_STACK_OFFSET: i16 = -80;

// sock_addr context field byte offsets (struct bpf_sock_addr ABI)
const SOCK_ADDR_USER_IP4_OFFSET: i16 = 4;
const SOCK_ADDR_USER_IP6_OFFSET: i16 = 8;
const SOCK_ADDR_USER_PORT_OFFSET: i16 = 24;
const SOCK_ADDR_PROTOCOL_OFFSET: i16 = 36;

// Protocol numbers
const PROTOCOL_TCP: i32 = 6;
const PROTOCOL_UDP: i32 = 17;

// Address family
const AF_INET: i32 = 2;
const AF_INET6: i32 = 10;

// Token IP constants (127.128.0.0/9)
const TOKEN_PREFIX_HOST: i32 = 0x7f800000u32 as i32;
const TOKEN_HOST_MASK: i32 = 0x007fffffu32 as i32;

// BPF endian extension opcodes
const BPF_END: u8 = 0xd0;
const BPF_TO_BE: u8 = 0x00;

// BPF_JNE is missing from the standard constant set
const BPF_JNE: u8 = 0x50;

// DNS mode constants (mirrors redirect_types.hpp)
const DNS_MODE_HIJACK: u8 = 0;
const DNS_MODE_BYPASS: u8 = 1;
const DNS_PLAIN_PORT: i32 = 53;

// ── Jump instruction constructors ───────────────────────────────────────────

#[inline]
fn jmp_imm(op: u8, dst: u8, imm: i32, off: i16) -> BpfInsn {
    BpfInsn { code: BPF_JMP | op | BPF_K, dst_reg: dst, src_reg: 0, off, imm }
}

#[inline]
fn jmp_always(off: i16) -> BpfInsn {
    BpfInsn { code: BPF_JMP | BPF_JA, dst_reg: 0, src_reg: 0, off, imm: 0 }
}

// ── Builder ─────────────────────────────────────────────────────────────────

struct Builder {
    insns: Vec<BpfInsn>,
    allow_jumps: Vec<usize>,
}

impl Builder {
    fn new() -> Self {
        Self {
            insns: Vec::with_capacity(MAX_INSNS),
            allow_jumps: Vec::with_capacity(MAX_ALLOW_JUMPS),
        }
    }

    fn emit(&mut self, insn: BpfInsn) {
        self.insns.push(insn);
    }

    fn emit_jump(&mut self, insn: BpfInsn) -> usize {
        let index = self.insns.len();
        self.insns.push(insn);
        index
    }

    fn patch_jump(&mut self, jump_index: usize) {
        let target = self.insns.len();
        self.insns[jump_index].off = (target - jump_index - 1) as i16;
    }

    fn add_allow_jump(&mut self, jump_index: usize) {
        self.allow_jumps.push(jump_index);
    }

    /// Emit a jump instruction and record it as an allow-jump (combined to avoid double borrow).
    fn emit_jump_to_allow(&mut self, insn: BpfInsn) {
        let idx = self.insns.len();
        self.insns.push(insn);
        self.allow_jumps.push(idx);
    }

    fn patch_all_allow_jumps(&mut self, target: usize) {
        for &idx in &self.allow_jumps {
            self.insns[idx].off = (target - idx - 1) as i16;
        }
    }

    // ── Instruction constructors (match C++ helpers exactly) ─────────────

    fn alu64_imm(&mut self, op: u8, dst: u8, imm: i32) {
        self.emit(BpfInsn { code: BPF_ALU64 | op | BPF_K, dst_reg: dst, src_reg: 0, off: 0, imm });
    }

    fn alu64_reg(&mut self, op: u8, dst: u8, src: u8) {
        self.emit(BpfInsn { code: BPF_ALU64 | op | BPF_X, dst_reg: dst, src_reg: src, off: 0, imm: 0 });
    }

    fn alu32_imm(&mut self, op: u8, dst: u8, imm: i32) {
        self.emit(BpfInsn { code: BPF_ALU | op | BPF_K, dst_reg: dst, src_reg: 0, off: 0, imm });
    }

    fn alu32_reg(&mut self, op: u8, dst: u8, src: u8) {
        self.emit(BpfInsn { code: BPF_ALU | op | BPF_X, dst_reg: dst, src_reg: src, off: 0, imm: 0 });
    }

    fn load_x(&mut self, size: u8, dst: u8, src: u8, off: i16) {
        self.emit(BpfInsn { code: BPF_LDX | size | BPF_MEM, dst_reg: dst, src_reg: src, off, imm: 0 });
    }

    fn store_x(&mut self, size: u8, dst: u8, src: u8, off: i16) {
        self.emit(BpfInsn { code: BPF_STX | size | BPF_MEM, dst_reg: dst, src_reg: src, off, imm: 0 });
    }

    fn store_imm(&mut self, size: u8, dst: u8, off: i16, imm: i32) {
        self.emit(BpfInsn { code: BPF_ST | size | BPF_MEM, dst_reg: dst, src_reg: 0, off, imm });
    }

    fn call_helper(&mut self, func: i32) {
        self.emit(BpfInsn { code: BPF_JMP | BPF_CALL, dst_reg: 0, src_reg: 0, off: 0, imm: func });
    }

    fn exit(&mut self) {
        self.emit(BpfInsn { code: BPF_JMP | BPF_EXIT, dst_reg: 0, src_reg: 0, off: 0, imm: 0 });
    }

    fn endian_to_big(&mut self, dst: u8, size: i32) {
        self.emit(BpfInsn { code: BPF_ALU | BPF_END | BPF_TO_BE, dst_reg: dst, src_reg: 0, off: 0, imm: size });
    }

    fn emit_map_fd(&mut self, dst: u8, map_fd: RawFd) {
        self.emit(BpfInsn {
            code: BPF_LD | BPF_DW | BPF_IMM,
            dst_reg: dst,
            src_reg: BPF_PSEUDO_MAP_FD,
            off: 0,
            imm: map_fd,
        });
        self.emit(BpfInsn::default()); // padding (second half of 16-byte LD_IMM64)
    }

    fn emit_return(&mut self, result: i32) -> usize {
        let label = self.insns.len();
        self.alu64_imm(BPF_MOV, 0, result);
        self.exit();
        label
    }

    fn emit_zero(&mut self, base_offset: i16, size: usize) {
        for offset in (0..size).step_by(4) {
            self.store_imm(BPF_W, 10, base_offset + offset as i16, 0);
        }
    }

    /// Emit allow(1)/drop(0), patch jumps, load program.
    fn finish_redirect_program(
        &mut self,
        map_update_failed: usize,
        program_name: &str,
        attach_type: u32,
    ) -> Result<RawFd, String> {
        let allow_label = self.emit_return(1);
        let drop_label = self.emit_return(0);
        self.insns[map_update_failed].off = (drop_label - map_update_failed - 1) as i16;
        self.patch_all_allow_jumps(allow_label);
        if self.insns.len() > MAX_INSNS {
            return Err("eBPF program overflow".to_string());
        }
        load_program_for_attach_type(
            &self.insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, attach_type, program_name,
        )
    }
}

// ── Shared helpers ──────────────────────────────────────────────────────────

/// Save ctx to r6, check TGID bypass map, then UID policy.
fn emit_redirect_preamble(
    b: &mut Builder,
    bypass_tgid_map_fd: RawFd,
    uid_policy_map_fd: RawFd,
    uid_policy_mode: u8,
) {
    // r6 = ctx (arg1)
    b.alu64_reg(BPF_MOV, 6, 1);
    // get_current_pid_tgid → r0 = (tgid << 32 | pid)
    b.call_helper(helper_get_current_pid_tgid());
    // r7 = r0 >> 32  (TGID, the process-group ID)
    b.alu64_reg(BPF_MOV, 7, 0);
    b.alu64_imm(BPF_RSH, 7, 32);
    // stack[-4] = TGID
    b.store_x(BPF_W, 10, 7, TGID_STACK_OFFSET);
    // Lookup bypass map: key = &TGID
    b.emit_map_fd(1, bypass_tgid_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, TGID_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    // if r0 == 0 (not found) → skip bypass
    let no_bypass = b.emit_jump(jmp_imm(BPF_JEQ, 0, 0, 0));
    // if found → allow
    b.emit_jump_to_allow(jmp_always(0));
    b.patch_jump(no_bypass);
    // UID policy check
    emit_uid_policy(b, uid_policy_map_fd, uid_policy_mode);
}

/// UID policy: UID < 10000 → allow; otherwise check policy map.
fn emit_uid_policy(b: &mut Builder, uid_policy_map_fd: RawFd, uid_policy_mode: u8) {
    b.call_helper(helper_get_current_uid_gid());
    b.store_x(BPF_W, 10, 0, UID_STACK_OFFSET);
    b.load_x(BPF_W, 7, 10, UID_STACK_OFFSET);
    // Android app UIDs start at 10000; system UIDs → allow
    let application_uid = b.emit_jump(jmp_imm(BPF_JGE, 7, 10000, 0));
    b.emit_jump_to_allow(jmp_always(0));
    b.patch_jump(application_uid);

    if uid_policy_map_fd < 0 || uid_policy_mode == 0 { return; }
    b.emit_map_fd(1, uid_policy_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, UID_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    if uid_policy_mode == 1 {
        // allowlist: not found (r0==0) → allow
        b.emit_jump_to_allow(jmp_imm(BPF_JEQ, 0, 0, 0));
    } else if uid_policy_mode == 2 {
        // blocklist: found (r0!=0) → allow
        b.emit_jump_to_allow(jmp_imm(BPF_JNE, 0, 0, 0));
    }
}

/// Check protocol: protocol_filter==0 means check TCP/UDP from ctx; otherwise set directly.
/// Stores protocol byte on stack at TGID_STACK_OFFSET for later use.
fn emit_protocol_filter(b: &mut Builder, protocol_filter: u8) {
    if protocol_filter == 0 {
        b.load_x(BPF_W, 5, 6, SOCK_ADDR_PROTOCOL_OFFSET);
        let is_tcp = b.emit_jump(jmp_imm(BPF_JEQ, 5, PROTOCOL_TCP, 0));
        b.emit_jump_to_allow(jmp_imm(BPF_JNE, 5, PROTOCOL_UDP, 0));
        b.patch_jump(is_tcp);
    } else {
        b.alu64_imm(BPF_MOV, 5, protocol_filter as i32);
    }
    b.store_x(BPF_B, 10, 5, TGID_STACK_OFFSET);
}

/// IPv4 CIDR bypass using LPM trie. address_reg holds the IPv4 address word.
fn emit_ipv4_cidr_bypass(b: &mut Builder, cidr_map_fd: RawFd, address_reg: u8) {
    if cidr_map_fd < 0 { return; }
    b.emit_zero(KEY_STACK_OFFSET, 8);
    b.store_imm(BPF_W, 10, KEY_STACK_OFFSET, 32);
    b.store_x(BPF_W, 10, address_reg, KEY_STACK_OFFSET + 4);
    b.emit_map_fd(1, cidr_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    b.emit_jump_to_allow(jmp_imm(BPF_JNE, 0, 0, 0));
}

/// IPv6 CIDR bypass using LPM trie. w0..w3 are registers holding the 4 IPv6 words.
fn emit_ipv6_cidr_bypass(
    b: &mut Builder,
    cidr_map_fd: RawFd,
    w0: u8, w1: u8, w2: u8, w3: u8,
) {
    if cidr_map_fd < 0 { return; }
    b.emit_zero(KEY_STACK_OFFSET, 20);
    b.store_imm(BPF_W, 10, KEY_STACK_OFFSET, 128);
    b.store_x(BPF_W, 10, w0, KEY_STACK_OFFSET + 4);
    b.store_x(BPF_W, 10, w1, KEY_STACK_OFFSET + 8);
    b.store_x(BPF_W, 10, w2, KEY_STACK_OFFSET + 12);
    b.store_x(BPF_W, 10, w3, KEY_STACK_OFFSET + 16);
    b.emit_map_fd(1, cidr_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    b.emit_jump_to_allow(jmp_imm(BPF_JNE, 0, 0, 0));
}

// ── Unified IPv4/IPv6 redirect program generators ───────────────────────────

fn load_ipv4_redirect_program(
    redirect_map_fd: RawFd,
    bypass_tgid_map_fd: RawFd,
    listener_port: u16,
    protocol_filter: u8,
    attach_type: u32,
    program_name: &str,
    uid_policy_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    bypass_cidr4_map_fd: RawFd,
    _bypass_cidr6_map_fd: RawFd,
) -> Result<RawFd, String> {
    if redirect_map_fd < 0 || bypass_tgid_map_fd < 0 || listener_port == 0
        || dns_mode > DNS_MODE_BYPASS
        || (dns_mode == DNS_MODE_HIJACK && dns_listener_port == 0)
    {
        return Err("EINVAL".to_string());
    }

    let mut b = Builder::new();

    emit_redirect_preamble(&mut b, bypass_tgid_map_fd, uid_policy_map_fd, uid_policy_mode);
    emit_protocol_filter(&mut b, protocol_filter);

    // r7 = user_ip4, r8 = user_port
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP4_OFFSET);
    b.load_x(BPF_W, 8, 6, SOCK_ADDR_USER_PORT_OFFSET);

    // DNS mode handling
    if dns_mode == DNS_MODE_BYPASS {
        let dns_port = b.emit_jump(jmp_imm(BPF_JEQ, 8, (DNS_PLAIN_PORT as u16).to_be() as i32, 0));
        b.add_allow_jump(dns_port);
    } else {
        // hijack: rewrite DNS port 53 to 127.0.0.1:<dns_listener_port>
        let not_dns = b.emit_jump(jmp_imm(BPF_JNE, 8, (DNS_PLAIN_PORT as u16).to_be() as i32, 0));
        b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP4_OFFSET, 0x0100007f);
        b.alu64_imm(BPF_MOV, 0, dns_listener_port.to_be() as i32);
        b.store_x(BPF_W, 6, 0, SOCK_ADDR_USER_PORT_OFFSET);
        b.emit_return(1);
        b.patch_jump(not_dns);
    }

    emit_ipv4_cidr_bypass(&mut b, bypass_cidr4_map_fd, 7);

    // Filter out unspecified, loopback, multicast
    b.emit_jump_to_allow(jmp_imm(BPF_JEQ, 7, 0, 0));
    b.alu64_reg(BPF_MOV, 2, 7);
    b.endian_to_big(2, 32);
    b.alu64_reg(BPF_MOV, 3, 2);
    b.alu64_imm(BPF_RSH, 3, 24);
    b.emit_jump_to_allow(jmp_imm(BPF_JEQ, 3, 127, 0));
    b.emit_jump_to_allow(jmp_imm(BPF_JGE, 3, 224, 0));

    // Token generation: random 127.128.x.x IP
    b.call_helper(helper_get_prandom_u32());
    b.alu32_reg(BPF_MOV, 9, 0);                       // 32-bit MOV clears upper 32 bits
    b.alu32_imm(BPF_AND, 9, TOKEN_HOST_MASK);
    b.alu32_imm(BPF_OR, 9, TOKEN_PREFIX_HOST);
    b.endian_to_big(9, 32);

    // Zero key (20 bytes) and value (40 bytes) on stack
    b.emit_zero(KEY_STACK_OFFSET, 20);   // sizeof(RedirectKey)
    b.emit_zero(VALUE_STACK_OFFSET, 40); // sizeof(OriginalDestination)

    // Read protocol byte from stack
    b.load_x(BPF_B, 5, 10, TGID_STACK_OFFSET);

    // Build RedirectKey { family, protocol, listener_port, token_addr[4] }
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET, AF_INET);
    b.store_x(BPF_B, 10, 5, KEY_STACK_OFFSET + 1);
    b.store_imm(BPF_H, 10, KEY_STACK_OFFSET + 2, listener_port as i32);
    b.store_x(BPF_W, 10, 9, KEY_STACK_OFFSET + 4);

    // Build OriginalDestination { family, protocol, port, addr[4] }
    b.store_imm(BPF_B, 10, VALUE_STACK_OFFSET, AF_INET);
    b.store_x(BPF_B, 10, 5, VALUE_STACK_OFFSET + 1);
    b.endian_to_big(8, 16);                             // port → network byte order
    b.store_x(BPF_H, 10, 8, VALUE_STACK_OFFSET + 2);
    b.store_x(BPF_W, 10, 7, VALUE_STACK_OFFSET + 4);   // original IP

    // map_update_elem(redirect_map, &key, &value, BPF_ANY)
    b.emit_map_fd(1, redirect_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.alu64_reg(BPF_MOV, 3, 10);
    b.alu64_imm(BPF_ADD, 3, VALUE_STACK_OFFSET as i32);
    b.alu64_imm(BPF_MOV, 4, BPF_ANY_FLAG as i32);
    b.call_helper(helper_map_update_elem());
    let map_update_failed = b.emit_jump(jmp_imm(BPF_JNE, 0, 0, 0));

    // Rewrite ctx: user_ip4 = token, user_port = htons(listener_port)
    b.store_x(BPF_W, 6, 9, SOCK_ADDR_USER_IP4_OFFSET);
    b.alu64_imm(BPF_MOV, 0, listener_port.to_be() as i32);
    b.store_x(BPF_W, 6, 0, SOCK_ADDR_USER_PORT_OFFSET);

    b.finish_redirect_program(map_update_failed, program_name, attach_type)
}

fn load_ipv6_redirect_program(
    redirect_map_fd: RawFd,
    bypass_tgid_map_fd: RawFd,
    listener_port: u16,
    protocol_filter: u8,
    attach_type: u32,
    program_name: &str,
    uid_policy_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    _bypass_cidr4_map_fd: RawFd,
    bypass_cidr6_map_fd: RawFd,
) -> Result<RawFd, String> {
    if redirect_map_fd < 0 || bypass_tgid_map_fd < 0 || listener_port == 0
        || dns_mode > DNS_MODE_BYPASS
        || (dns_mode == DNS_MODE_HIJACK && dns_listener_port == 0)
    {
        return Err("EINVAL".to_string());
    }

    let mut b = Builder::new();

    emit_redirect_preamble(&mut b, bypass_tgid_map_fd, uid_policy_map_fd, uid_policy_mode);
    emit_protocol_filter(&mut b, protocol_filter);

    // Preserve IPv6 destination before any helper call; store in value area
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET);
    b.store_x(BPF_W, 10, 7, VALUE_STACK_OFFSET + 4);
    b.load_x(BPF_W, 8, 6, SOCK_ADDR_USER_IP6_OFFSET + 4);
    b.store_x(BPF_W, 10, 8, VALUE_STACK_OFFSET + 8);
    b.load_x(BPF_W, 9, 6, SOCK_ADDR_USER_IP6_OFFSET + 8);
    b.store_x(BPF_W, 10, 9, VALUE_STACK_OFFSET + 12);
    b.load_x(BPF_W, 0, 6, SOCK_ADDR_USER_IP6_OFFSET + 12);
    b.store_x(BPF_W, 10, 0, VALUE_STACK_OFFSET + 16);

    // Multicast check: first byte of ip6[0] == 0xff
    b.alu64_reg(BPF_MOV, 2, 7);
    b.endian_to_big(2, 32);
    b.alu64_imm(BPF_RSH, 2, 24);
    b.emit_jump_to_allow(jmp_imm(BPF_JEQ, 2, 255, 0));

    // Unspecified address (::) check — all four words zero
    let first_nonzero = b.emit_jump(jmp_imm(BPF_JNE, 7, 0, 0));
    let second_nonzero = b.emit_jump(jmp_imm(BPF_JNE, 8, 0, 0));
    let third_nonzero = b.emit_jump(jmp_imm(BPF_JNE, 9, 0, 0));
    let not_loopback = b.emit_jump(jmp_imm(BPF_JNE, 0, 0x01000000u32 as i32, 0));
    b.emit_jump_to_allow(jmp_always(0));
    let after_local = b.insns.len();
    b.insns[first_nonzero].off = (after_local - first_nonzero - 1) as i16;
    b.insns[second_nonzero].off = (after_local - second_nonzero - 1) as i16;
    b.insns[third_nonzero].off = (after_local - third_nonzero - 1) as i16;
    b.insns[not_loopback].off = (after_local - not_loopback - 1) as i16;

    // IPv4-mapped loopback check (::ffff:127.x.x.x)
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET + 8);
    let not_mapped = b.emit_jump(jmp_imm(BPF_JNE, 7, 0xffff0000u32 as i32, 0));
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET + 12);
    b.endian_to_big(7, 32);
    b.alu64_imm(BPF_RSH, 7, 24);
    b.emit_jump_to_allow(jmp_imm(BPF_JEQ, 7, 127, 0));
    b.patch_jump(not_mapped);

    // Load port
    b.load_x(BPF_W, 8, 6, SOCK_ADDR_USER_PORT_OFFSET);

    if dns_mode == DNS_MODE_BYPASS {
        let dns_port = b.emit_jump(jmp_imm(BPF_JEQ, 8, (DNS_PLAIN_PORT as u16).to_be() as i32, 0));
        b.add_allow_jump(dns_port);
    } else {
        // hijack: rewrite DNS to ::ffff:127.0.0.1:<dns_listener_port>
        let not_dns = b.emit_jump(jmp_imm(BPF_JNE, 8, (DNS_PLAIN_PORT as u16).to_be() as i32, 0));
        b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET, 0);
        b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET + 4, 0);
        b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET + 8, 0xffff0000u32 as i32);
        b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET + 12, 0x0100007f);
        b.alu64_imm(BPF_MOV, 0, dns_listener_port.to_be() as i32);
        b.store_x(BPF_W, 6, 0, SOCK_ADDR_USER_PORT_OFFSET);
        b.emit_return(1);
        b.patch_jump(not_dns);
    }

    emit_ipv6_cidr_bypass(&mut b, bypass_cidr6_map_fd, 7, 8, 9, 0);

    // Zero key (20 bytes) and value header (4 bytes — addr already stored at value+4..+20)
    b.emit_zero(KEY_STACK_OFFSET, 20);
    b.emit_zero(VALUE_STACK_OFFSET, 4);

    // Read protocol byte
    b.load_x(BPF_B, 5, 10, TGID_STACK_OFFSET);

    // Build RedirectKey { AF_INET, protocol(dynamic), listener_port, ... }
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET, AF_INET);
    b.store_x(BPF_B, 10, 5, KEY_STACK_OFFSET + 1);
    b.store_imm(BPF_H, 10, KEY_STACK_OFFSET + 2, listener_port as i32);

    // Build OriginalDestination header { AF_INET6, protocol, port }
    b.store_imm(BPF_B, 10, VALUE_STACK_OFFSET, AF_INET6);
    b.store_x(BPF_B, 10, 5, VALUE_STACK_OFFSET + 1);
    b.endian_to_big(8, 16);
    b.store_x(BPF_H, 10, 8, VALUE_STACK_OFFSET + 2);

    // Token generation
    b.call_helper(helper_get_prandom_u32());
    b.alu32_reg(BPF_MOV, 9, 0);
    b.alu32_imm(BPF_AND, 9, TOKEN_HOST_MASK);
    b.alu32_imm(BPF_OR, 9, TOKEN_PREFIX_HOST);
    b.endian_to_big(9, 32);
    b.store_x(BPF_W, 10, 9, KEY_STACK_OFFSET + 4);

    // map_update_elem
    b.emit_map_fd(1, redirect_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.alu64_reg(BPF_MOV, 3, 10);
    b.alu64_imm(BPF_ADD, 3, VALUE_STACK_OFFSET as i32);
    b.alu64_imm(BPF_MOV, 4, BPF_ANY_FLAG as i32);
    b.call_helper(helper_map_update_elem());
    let map_update_failed = b.emit_jump(jmp_imm(BPF_JNE, 0, 0, 0));

    // Rewrite ctx to IPv4-mapped IPv6: ::ffff:127.128.x.x
    b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET, 0);
    b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET + 4, 0);
    b.store_imm(BPF_W, 6, SOCK_ADDR_USER_IP6_OFFSET + 8, 0xffff0000u32 as i32);
    b.store_x(BPF_W, 6, 9, SOCK_ADDR_USER_IP6_OFFSET + 12);
    b.alu64_imm(BPF_MOV, 0, listener_port.to_be() as i32);
    b.store_x(BPF_W, 6, 0, SOCK_ADDR_USER_PORT_OFFSET);

    b.finish_redirect_program(map_update_failed, program_name, attach_type)
}

// ── Recvmsg programs ────────────────────────────────────────────────────────

fn load_udp4_recvmsg_inner(redirect_map_fd: RawFd, listener_port: u16) -> Result<RawFd, String> {
    if redirect_map_fd < 0 || listener_port == 0 {
        return Err("EINVAL".to_string());
    }

    let mut b = Builder::new();
    let mut allow_jumps: Vec<usize> = Vec::with_capacity(3);

    b.alu64_reg(BPF_MOV, 6, 1);
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP4_OFFSET);

    // Build RedirectKey
    b.emit_zero(KEY_STACK_OFFSET, 20);
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET, AF_INET);
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET + 1, PROTOCOL_UDP as i32);
    b.store_imm(BPF_H, 10, KEY_STACK_OFFSET + 2, listener_port as i32);
    b.store_x(BPF_W, 10, 7, KEY_STACK_OFFSET + 4);

    // Lookup
    b.emit_map_fd(1, redirect_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JEQ, 0, 0, 0)));

    // Verify family and protocol
    b.load_x(BPF_B, 2, 0, 0);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 2, AF_INET, 0)));
    b.load_x(BPF_B, 2, 0, 1);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 2, PROTOCOL_UDP, 0)));

    // Restore original destination
    b.load_x(BPF_W, 7, 0, 4);                    // value.addr[0..4]
    b.load_x(BPF_H, 8, 0, 2);                    // value.port (u16)
    b.endian_to_big(8, 16);
    b.store_x(BPF_W, 6, 7, SOCK_ADDR_USER_IP4_OFFSET);
    b.store_x(BPF_W, 6, 8, SOCK_ADDR_USER_PORT_OFFSET);

    let allow_label = b.emit_return(1);
    for &idx in &allow_jumps {
        b.insns[idx].off = (allow_label - idx - 1) as i16;
    }
    if b.insns.len() > MAX_INSNS {
        return Err("eBPF program overflow".to_string());
    }
    load_program_for_attach_type(
        &b.insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, ATTACH_UDP4_RECVMSG, "fc_udp4_recv",
    )
}

fn load_udp6_recvmsg_inner(redirect_map_fd: RawFd, listener_port: u16) -> Result<RawFd, String> {
    if redirect_map_fd < 0 || listener_port == 0 {
        return Err("EINVAL".to_string());
    }

    let mut b = Builder::new();
    let mut allow_jumps: Vec<usize> = Vec::with_capacity(6);

    b.alu64_reg(BPF_MOV, 6, 1);

    // Check for IPv4-mapped address (::ffff:x.x.x.x)
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 7, 0, 0)));
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET + 4);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 7, 0, 0)));
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET + 8);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 7, 0xffff0000u32 as i32, 0)));
    b.load_x(BPF_W, 7, 6, SOCK_ADDR_USER_IP6_OFFSET + 12);

    // Build RedirectKey { AF_INET, UDP, port, token_addr=ip6[3] }
    b.emit_zero(KEY_STACK_OFFSET, 20);
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET, AF_INET);
    b.store_imm(BPF_B, 10, KEY_STACK_OFFSET + 1, PROTOCOL_UDP as i32);
    b.store_imm(BPF_H, 10, KEY_STACK_OFFSET + 2, listener_port as i32);
    b.store_x(BPF_W, 10, 7, KEY_STACK_OFFSET + 4);

    // Lookup
    b.emit_map_fd(1, redirect_map_fd);
    b.alu64_reg(BPF_MOV, 2, 10);
    b.alu64_imm(BPF_ADD, 2, KEY_STACK_OFFSET as i32);
    b.call_helper(helper_map_lookup_elem());
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JEQ, 0, 0, 0)));

    // Verify family and protocol
    b.load_x(BPF_B, 2, 0, 0);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 2, AF_INET6, 0)));
    b.load_x(BPF_B, 2, 0, 1);
    allow_jumps.push(b.emit_jump(jmp_imm(BPF_JNE, 2, PROTOCOL_UDP, 0)));

    // Restore original IPv6 destination
    b.load_x(BPF_W, 7, 0, 4);
    b.load_x(BPF_W, 8, 0, 8);
    b.load_x(BPF_W, 9, 0, 12);
    b.load_x(BPF_W, 5, 0, 16);
    b.store_x(BPF_W, 6, 7, SOCK_ADDR_USER_IP6_OFFSET);
    b.store_x(BPF_W, 6, 8, SOCK_ADDR_USER_IP6_OFFSET + 4);
    b.store_x(BPF_W, 6, 9, SOCK_ADDR_USER_IP6_OFFSET + 8);
    b.store_x(BPF_W, 6, 5, SOCK_ADDR_USER_IP6_OFFSET + 12);

    // Restore port
    b.load_x(BPF_H, 8, 0, 2);
    b.endian_to_big(8, 16);
    b.store_x(BPF_W, 6, 8, SOCK_ADDR_USER_PORT_OFFSET);

    let allow_label = b.emit_return(1);
    for &idx in &allow_jumps {
        b.insns[idx].off = (allow_label - idx - 1) as i16;
    }
    if b.insns.len() > MAX_INSNS {
        return Err("eBPF program overflow".to_string());
    }
    load_program_for_attach_type(
        &b.insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, ATTACH_UDP6_RECVMSG, "fc_udp6_recv",
    )
}

// ── Public API ──────────────────────────────────────────────────────────────

pub fn load_tcp4_connect_program(
    redirect_fd: RawFd,
    bypass_fd: RawFd,
    listener_port: u16,
    uid_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    cidr4_fd: RawFd,
    cidr6_fd: RawFd,
) -> Result<RawFd, String> {
    load_ipv4_redirect_program(
        redirect_fd, bypass_fd, listener_port,
        0, ATTACH_INET4_CONNECT, "fc_sock4",
        uid_map_fd, uid_policy_mode, dns_mode, dns_listener_port, cidr4_fd, cidr6_fd,
    )
}

pub fn load_udp4_sendmsg_program(
    redirect_fd: RawFd,
    bypass_fd: RawFd,
    listener_port: u16,
    uid_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    cidr4_fd: RawFd,
    cidr6_fd: RawFd,
) -> Result<RawFd, String> {
    load_ipv4_redirect_program(
        redirect_fd, bypass_fd, listener_port,
        PROTOCOL_UDP as u8, ATTACH_UDP4_SENDMSG, "fc_udp4_send",
        uid_map_fd, uid_policy_mode, dns_mode, dns_listener_port, cidr4_fd, cidr6_fd,
    )
}

pub fn load_udp4_recvmsg_program(
    redirect_fd: RawFd,
    listener_port: u16,
) -> Result<RawFd, String> {
    load_udp4_recvmsg_inner(redirect_fd, listener_port)
}

pub fn load_ipv6_connect_program(
    redirect_fd: RawFd,
    bypass_fd: RawFd,
    listener_port: u16,
    uid_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    cidr4_fd: RawFd,
    cidr6_fd: RawFd,
) -> Result<RawFd, String> {
    load_ipv6_redirect_program(
        redirect_fd, bypass_fd, listener_port,
        0, ATTACH_INET6_CONNECT, "fc_sock6",
        uid_map_fd, uid_policy_mode, dns_mode, dns_listener_port, cidr4_fd, cidr6_fd,
    )
}

pub fn load_udp6_sendmsg_program(
    redirect_fd: RawFd,
    bypass_fd: RawFd,
    listener_port: u16,
    uid_map_fd: RawFd,
    uid_policy_mode: u8,
    dns_mode: u8,
    dns_listener_port: u16,
    cidr4_fd: RawFd,
    cidr6_fd: RawFd,
) -> Result<RawFd, String> {
    load_ipv6_redirect_program(
        redirect_fd, bypass_fd, listener_port,
        PROTOCOL_UDP as u8, ATTACH_UDP6_SENDMSG, "fc_udp6_send",
        uid_map_fd, uid_policy_mode, dns_mode, dns_listener_port, cidr4_fd, cidr6_fd,
    )
}

pub fn load_udp6_recvmsg_program(
    redirect_fd: RawFd,
    listener_port: u16,
) -> Result<RawFd, String> {
    load_udp6_recvmsg_inner(redirect_fd, listener_port)
}

/// Probe all 6 socket-address attach types individually.
pub fn probe_sock_addr_programs_all() -> bool {
    let insns = [
        BpfInsn { code: BPF_LDX | BPF_W | BPF_MEM, dst_reg: 0, src_reg: 1, off: SOCK_ADDR_USER_PORT_OFFSET, imm: 0 },
        BpfInsn { code: BPF_ALU64 | BPF_MOV | BPF_K, dst_reg: 0, src_reg: 0, off: 0, imm: 1 },
        BpfInsn { code: BPF_JMP | BPF_EXIT, dst_reg: 0, src_reg: 0, off: 0, imm: 0 },
    ];
    for &attach_type in &ALL_ATTACH_TYPES {
        match load_program_for_attach_type(&insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, attach_type, "fc_probe") {
            Ok(fd) => { unsafe { libc::close(fd); } }
            Err(_) => return false,
        }
    }
    true
}
