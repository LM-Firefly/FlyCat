// Cgroup runtime — creates BPF maps, loads and attaches all 6 programs to cgroup v2.
//
// Mirrors the C++ cgroup_runtime.hpp / cgroup_runtime.cpp with exact struct layouts matching redirect_types.hpp static_asserts.

use std::os::unix::io::RawFd;
use super::builder;
use super::syscall::*;

// ── Protocol constants (must match redirect_types.hpp) ──────────────────────

pub const PROTOCOL_TCP: u8 = 6;
pub const PROTOCOL_UDP: u8 = 17;

// DNS mode constants (mirrors redirect_types.hpp)
pub const DNS_MODE_HIJACK: u8 = 0;
pub const DNS_MODE_BYPASS: u8 = 1;
pub const DNS_PLAIN_PORT: u16 = 53;

// ── Map capacities ─────────────────────────────────────────────────────────

const REDIRECT_MAP_CAPACITY: u32 = 65536;
const BYPASS_TGID_MAP_CAPACITY: u32 = 64;
const UID_POLICY_MAP_CAPACITY: u32 = 4096;
const BYPASS_CIDR_MAP_CAPACITY: u32 = 256;

// ── Shared BPF map types (must match redirect_types.hpp) ───────────────────

/// Redirect map key — 20 bytes, packed.
/// Matches C++ `yumebox::ebpf::RedirectKey` (static_assert 20 bytes).
#[repr(C, packed)]
#[derive(Clone, Copy, Default)]
pub struct RedirectKey {
    pub family: u8,
    pub protocol: u8,
    pub listener_port: u16, // native endian
    pub token_addr: [u8; 16],
}

const _: () = assert!(std::mem::size_of::<RedirectKey>() == 20, "RedirectKey must be 20 bytes");

/// Redirect map value — 40 bytes, packed.
/// Matches C++ `yumebox::ebpf::OriginalDestination` (static_assert 40 bytes, socket_cookie @ 24, uid @ 32).
#[repr(C, packed)]
#[derive(Clone, Copy, Default)]
pub struct OriginalDestination {
    pub family: u8,
    pub protocol: u8,
    pub port: u16,
    pub addr: [u8; 16],
    pub flags: u8,
    pub reserved: [u8; 3],
    pub socket_cookie: u64,
    pub uid: u32,
    pub reserved_tail: u32,
}

const _: () = assert!(std::mem::size_of::<OriginalDestination>() == 40, "OriginalDestination must be 40 bytes");
const _: () = assert!(std::mem::offset_of!(OriginalDestination, socket_cookie) == 24, "socket_cookie must be at offset 24");
const _: () = assert!(std::mem::offset_of!(OriginalDestination, uid) == 32, "uid must be at offset 32");

/// CIDR bypass rule — 18 bytes, packed.
/// Matches C++ `yumebox::ebpf::CidrRule` (family, prefix_length, address[16]).
#[repr(C, packed)]
#[derive(Clone, Copy, Debug, Default)]
pub struct CidrRule {
    pub family: u8,
    pub prefix_length: u8,
    pub address: [u8; 16],
}

// ── Internal LPM trie keys (match C++ CidrKey4 / CidrKey6) ────────────────

#[repr(C)]
#[derive(Clone, Copy)]
struct CidrKey4 {
    prefix_length: u32,
    address: [u8; 4],
}

#[repr(C)]
#[derive(Clone, Copy)]
struct CidrKey6 {
    prefix_length: u32,
    address: [u8; 16],
}

// ── CgroupRuntime ──────────────────────────────────────────────────────────

pub struct CgroupRuntime {
    cgroup_fd: RawFd,
    // Maps
    redirect_map: Option<BpfMap>,
    bypass_tgid_map: Option<BpfMap>,
    uid_policy_map: Option<BpfMap>,
    bypass_cidr4_map: Option<BpfMap>,
    bypass_cidr6_map: Option<BpfMap>,
    // Programs — stored individually (matches C++ per-program BpfMap fields)
    connect4_program: Option<BpfMap>,
    connect6_program: Option<BpfMap>,
    udp4_sendmsg_program: Option<BpfMap>,
    udp4_recvmsg_program: Option<BpfMap>,
    udp6_sendmsg_program: Option<BpfMap>,
    udp6_recvmsg_program: Option<BpfMap>,
    attached: bool,
}

impl CgroupRuntime {
    pub fn new() -> Self {
        Self {
            cgroup_fd: -1,
            redirect_map: None,
            bypass_tgid_map: None,
            uid_policy_map: None,
            bypass_cidr4_map: None,
            bypass_cidr6_map: None,
            connect4_program: None,
            connect6_program: None,
            udp4_sendmsg_program: None,
            udp4_recvmsg_program: None,
            udp6_sendmsg_program: None,
            udp6_recvmsg_program: None,
            attached: false,
        }
    }

    /// Open cgroup v2, create BPF maps, load and attach all programs.
    ///
    /// Signature matches C++ `CgroupRuntime::start()`.
    pub fn start(
        &mut self,
        cgroup_path: &str,
        listener_port: u16,
        bridge_tgid: u32,
        mihomo_tgid: u32,
        uid_policy_mode: u8,
        policy_uids: &[u32],
        dns_mode: u8,
        dns_listener_port: u16,
        enable_ipv6: bool,
        bypass_cidrs: &[CidrRule],
    ) -> Result<(), String> {
        self.stop();

        macro_rules! fail {
            ($stage:expr) => {{
                let saved_errno = std::io::Error::last_os_error();
                let code = saved_errno.raw_os_error().unwrap_or(libc::EIO);
                eprintln!(
                    "eBPF bridge: cgroup {} failed: errno={} ({})",
                    $stage, code, saved_errno
                );
                self.stop();
                Err(format!("cgroup {}: errno={} ({})", $stage, code, saved_errno))
            }};
        }

        let path = if cgroup_path.is_empty() { "/sys/fs/cgroup" } else { cgroup_path };

        // Validate arguments (mirrors C++ validation)
        if listener_port == 0
            || bridge_tgid == 0
            || uid_policy_mode > 2
            || dns_mode > DNS_MODE_BYPASS
            || (dns_mode == DNS_MODE_HIJACK && (dns_listener_port == 0 || dns_listener_port == listener_port))
            || policy_uids.len() > UID_POLICY_MAP_CAPACITY as usize
            || bypass_cidrs.len() > BYPASS_CIDR_MAP_CAPACITY as usize
            || !is_cgroup_v2_mount(path)
        {
            return fail!("argument/cgroup validation");
        }

        // Open cgroup directory
        let c_path = std::ffi::CString::new(path).map_err(|_| "invalid cgroup path")?;
        let cgroup_fd = unsafe {
            libc::open(c_path.as_ptr(), libc::O_RDONLY | libc::O_DIRECTORY | libc::O_CLOEXEC)
        };
        if cgroup_fd < 0 {
            return fail!("open target cgroup");
        }
        self.cgroup_fd = cgroup_fd;

        // Create redirect map (LRU hash, fallback to plain hash)
        let redirect_key_size = std::mem::size_of::<RedirectKey>() as u32;       // 20
        let redirect_val_size = std::mem::size_of::<OriginalDestination>() as u32; // 40
        let redirect_fd = create_map(MAP_TYPE_LRU_HASH, redirect_key_size, redirect_val_size, REDIRECT_MAP_CAPACITY, 0)
            .or_else(|e| {
                let errno = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if errno == libc::EINVAL || errno == libc::EOPNOTSUPP {
                    create_map(MAP_TYPE_HASH, redirect_key_size, redirect_val_size, REDIRECT_MAP_CAPACITY, 0)
                } else {
                    Err(e)
                }
            });
        if let Err(ref e) = redirect_fd {
            eprintln!("eBPF bridge: create redirect map: {}", e);
        }
        let redirect_fd = redirect_fd.map_err(|e| { self.stop(); e })?;
        self.redirect_map = Some(BpfMap::new(redirect_fd));

        // Create bypass TGID map (key=u32, value=u8)
        let bypass_fd = create_map(MAP_TYPE_HASH, 4, 1, BYPASS_TGID_MAP_CAPACITY, 0)
            .map_err(|e| { eprintln!("eBPF bridge: create bypass map: {}", e); self.stop(); e })?;
        self.bypass_tgid_map = Some(BpfMap::new(bypass_fd));
        if !self.add_bypass_tgid(bridge_tgid) || (mihomo_tgid != 0 && !self.add_bypass_tgid(mihomo_tgid)) {
            return fail!("populate bypass map");
        }

        // Create UID policy map (only when mode != 0)
        if uid_policy_mode != 0 {
            let uid_map_fd = create_map(MAP_TYPE_HASH, 4, 1, UID_POLICY_MAP_CAPACITY, 0)
                .map_err(|e| { eprintln!("eBPF bridge: create UID policy map: {}", e); self.stop(); e })?;
            self.uid_policy_map = Some(BpfMap::new(uid_map_fd));
            let enabled: u8 = 1;
            for &uid in policy_uids {
                let key_bytes = uid.to_ne_bytes();
                if update_map(uid_map_fd, &key_bytes, &[enabled], BPF_ANY_FLAG).is_err() {
                    return fail!("populate UID policy map");
                }
            }
        }

        // Validate and count CIDRs
        let mut cidr4_count: usize = 0;
        let mut cidr6_count: usize = 0;
        for rule in bypass_cidrs {
            if rule.family == libc::AF_INET as u8 && rule.prefix_length <= 32 {
                cidr4_count += 1;
            } else if rule.family == libc::AF_INET6 as u8 && rule.prefix_length <= 128 {
                cidr6_count += 1;
            } else {
                return fail!("validate bypass CIDR");
            }
        }

        // Create CIDR maps (only when needed)
        if cidr4_count != 0 {
            let map_fd = create_map(MAP_TYPE_LPM_TRIE, 8, 1, BYPASS_CIDR_MAP_CAPACITY, BPF_F_NO_PREALLOC)
                .map_err(|e| { eprintln!("eBPF bridge: create IPv4 CIDR map: {}", e); self.stop(); e })?;
            self.bypass_cidr4_map = Some(BpfMap::new(map_fd));
        }
        if cidr6_count != 0 {
            let map_fd = create_map(MAP_TYPE_LPM_TRIE, 20, 1, BYPASS_CIDR_MAP_CAPACITY, BPF_F_NO_PREALLOC)
                .map_err(|e| { eprintln!("eBPF bridge: create IPv6 CIDR map: {}", e); self.stop(); e })?;
            self.bypass_cidr6_map = Some(BpfMap::new(map_fd));
        }

        // Populate CIDR maps
        let enabled: u8 = 1;
        for rule in bypass_cidrs {
            if rule.family == libc::AF_INET as u8 {
                if let Some(ref map) = self.bypass_cidr4_map {
                    let key = CidrKey4 {
                        prefix_length: rule.prefix_length as u32,
                        address: [rule.address[0], rule.address[1], rule.address[2], rule.address[3]],
                    };
                    let key_bytes = unsafe {
                        std::slice::from_raw_parts(&key as *const _ as *const u8, std::mem::size_of::<CidrKey4>())
                    };
                    if update_map(map.fd(), key_bytes, &[enabled], BPF_ANY_FLAG).is_err() {
                        return fail!("populate IPv4 CIDR map");
                    }
                }
            } else {
                if let Some(ref map) = self.bypass_cidr6_map {
                    let key = CidrKey6 {
                        prefix_length: rule.prefix_length as u32,
                        address: rule.address,
                    };
                    let key_bytes = unsafe {
                        std::slice::from_raw_parts(&key as *const _ as *const u8, std::mem::size_of::<CidrKey6>())
                    };
                    if update_map(map.fd(), key_bytes, &[enabled], BPF_ANY_FLAG).is_err() {
                        return fail!("populate IPv6 CIDR map");
                    }
                }
            }
        }

        // Collect map fds for program builders
        let rfd = self.redirect_map.as_ref().unwrap().fd();
        let bfd = self.bypass_tgid_map.as_ref().unwrap().fd();
        let ufd = self.uid_policy_map.as_ref().map_or(-1, |m| m.fd());
        let c4fd = self.bypass_cidr4_map.as_ref().map_or(-1, |m| m.fd());
        let c6fd = self.bypass_cidr6_map.as_ref().map_or(-1, |m| m.fd());

        // --- Load and attach IPv4 connect program ---
        let prog_fd = builder::load_tcp4_connect_program(rfd, bfd, listener_port, ufd, uid_policy_mode, dns_mode, dns_listener_port, c4fd, c6fd)
            .map_err(|e| { eprintln!("eBPF bridge: load IPv4 connect program: {}", e); self.stop(); e })?;
        if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_INET4_CONNECT) {
            unsafe { libc::close(prog_fd); }
            eprintln!("eBPF bridge: attach IPv4 connect program: {}", e);
            self.stop();
            return Err(e);
        }
        self.connect4_program = Some(BpfMap::new(prog_fd));

        // --- Load and attach IPv4 UDP sendmsg program ---
        let prog_fd = builder::load_udp4_sendmsg_program(rfd, bfd, listener_port, ufd, uid_policy_mode, dns_mode, dns_listener_port, c4fd, c6fd)
            .map_err(|e| { eprintln!("eBPF bridge: load IPv4 UDP sendmsg program: {}", e); self.stop(); e })?;
        if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_UDP4_SENDMSG) {
            unsafe { libc::close(prog_fd); }
            eprintln!("eBPF bridge: attach IPv4 UDP sendmsg program: {}", e);
            self.stop();
            return Err(e);
        }
        self.udp4_sendmsg_program = Some(BpfMap::new(prog_fd));

        // --- Load and attach IPv4 UDP recvmsg program ---
        let prog_fd = builder::load_udp4_recvmsg_program(rfd, listener_port)
            .map_err(|e| { eprintln!("eBPF bridge: load IPv4 UDP recvmsg program: {}", e); self.stop(); e })?;
        if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_UDP4_RECVMSG) {
            unsafe { libc::close(prog_fd); }
            eprintln!("eBPF bridge: attach IPv4 UDP recvmsg program: {}", e);
            self.stop();
            return Err(e);
        }
        self.udp4_recvmsg_program = Some(BpfMap::new(prog_fd));

        // --- IPv6 programs (conditional on enable_ipv6) ---
        if enable_ipv6 {
            // IPv6 connect
            let prog_fd = builder::load_ipv6_connect_program(rfd, bfd, listener_port, ufd, uid_policy_mode, dns_mode, dns_listener_port, c4fd, c6fd)
                .map_err(|e| { eprintln!("eBPF bridge: load IPv6 connect program: {}", e); self.stop(); e })?;
            if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_INET6_CONNECT) {
                unsafe { libc::close(prog_fd); }
                eprintln!("eBPF bridge: attach IPv6 connect program: {}", e);
                self.stop();
                return Err(e);
            }
            self.connect6_program = Some(BpfMap::new(prog_fd));

            // IPv6 UDP sendmsg
            let prog_fd = builder::load_udp6_sendmsg_program(rfd, bfd, listener_port, ufd, uid_policy_mode, dns_mode, dns_listener_port, c4fd, c6fd)
                .map_err(|e| { eprintln!("eBPF bridge: load IPv6 UDP sendmsg program: {}", e); self.stop(); e })?;
            if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_UDP6_SENDMSG) {
                unsafe { libc::close(prog_fd); }
                eprintln!("eBPF bridge: attach IPv6 UDP sendmsg program: {}", e);
                self.stop();
                return Err(e);
            }
            self.udp6_sendmsg_program = Some(BpfMap::new(prog_fd));

            // IPv6 UDP recvmsg
            let prog_fd = builder::load_udp6_recvmsg_program(rfd, listener_port)
                .map_err(|e| { eprintln!("eBPF bridge: load IPv6 UDP recvmsg program: {}", e); self.stop(); e })?;
            if let Err(e) = attach_program(cgroup_fd, prog_fd, ATTACH_UDP6_RECVMSG) {
                unsafe { libc::close(prog_fd); }
                eprintln!("eBPF bridge: attach IPv6 UDP recvmsg program: {}", e);
                self.stop();
                return Err(e);
            }
            self.udp6_recvmsg_program = Some(BpfMap::new(prog_fd));
        }

        self.attached = true;
        Ok(())
    }

    /// Detach all programs and release all resources.
    ///
    /// Each program is detached exactly once (6 detaches max), NOT 36.
    /// Uses the per-program fd presence as the cleanup guard so partial startup never leaves a cgroup hook behind.
    pub fn stop(&mut self) {
        if self.cgroup_fd >= 0 {
            // Detach in reverse load order (matches C++)
            self.detach_program_inner(&self.udp6_recvmsg_program, ATTACH_UDP6_RECVMSG);
            self.detach_program_inner(&self.udp6_sendmsg_program, ATTACH_UDP6_SENDMSG);
            self.detach_program_inner(&self.connect6_program, ATTACH_INET6_CONNECT);
            self.detach_program_inner(&self.udp4_recvmsg_program, ATTACH_UDP4_RECVMSG);
            self.detach_program_inner(&self.udp4_sendmsg_program, ATTACH_UDP4_SENDMSG);
            self.detach_program_inner(&self.connect4_program, ATTACH_INET4_CONNECT);
        }
        self.attached = false;
        // Release in reverse order (matches C++)
        self.udp4_recvmsg_program = None;
        self.udp4_sendmsg_program = None;
        self.connect4_program = None;
        self.uid_policy_map = None;
        self.bypass_cidr4_map = None;
        self.bypass_cidr6_map = None;
        self.udp6_recvmsg_program = None;
        self.udp6_sendmsg_program = None;
        self.connect6_program = None;
        self.bypass_tgid_map = None;
        self.redirect_map = None;
        if self.cgroup_fd >= 0 {
            unsafe { libc::close(self.cgroup_fd); }
            self.cgroup_fd = -1;
        }
    }

    /// Look up and delete a TCP destination from the redirect map.
    ///
    /// Builds a full `RedirectKey` from `listener_port` and `token_addr`, matching the C++ `takeTcpDestination` semantics.
    pub fn take_tcp_destination(
        &self,
        listener_port: u16,
        token_addr: &[u8; 4],
    ) -> Option<OriginalDestination> {
        if !self.attached {
            return None;
        }
        let map = self.redirect_map.as_ref()?;

        let mut key = RedirectKey::default();
        key.family = libc::AF_INET as u8;
        key.protocol = PROTOCOL_TCP;
        key.listener_port = listener_port;
        key.token_addr[..4].copy_from_slice(token_addr);

        let key_bytes = unsafe {
            std::slice::from_raw_parts(&key as *const _ as *const u8, std::mem::size_of::<RedirectKey>())
        };
        let mut value = OriginalDestination::default();
        let val_bytes = unsafe {
            std::slice::from_raw_parts_mut(
                &mut value as *mut _ as *mut u8,
                std::mem::size_of::<OriginalDestination>(),
            )
        };

        if !lookup_map(map.fd(), key_bytes, val_bytes).ok()? {
            return None;
        }
        // Delete consumed entry (ENOENT is acceptable)
        let _ = delete_map(map.fd(), key_bytes);

        // Validate destination
        if (value.family != libc::AF_INET as u8 && value.family != libc::AF_INET6 as u8)
            || value.protocol != PROTOCOL_TCP
            || value.port == 0
        {
            return None;
        }
        Some(value)
    }

    /// Look up a UDP destination from the redirect map (does NOT delete).
    ///
    /// Builds a full `RedirectKey` from `listener_port` and `token_addr`, matching the C++ `takeUdpDestination` semantics.
    pub fn take_udp_destination(
        &self,
        listener_port: u16,
        token_addr: &[u8; 4],
    ) -> Option<OriginalDestination> {
        if !self.attached {
            return None;
        }
        let map = self.redirect_map.as_ref()?;

        let mut key = RedirectKey::default();
        key.family = libc::AF_INET as u8;
        key.protocol = PROTOCOL_UDP;
        key.listener_port = listener_port;
        key.token_addr[..4].copy_from_slice(token_addr);

        let key_bytes = unsafe {
            std::slice::from_raw_parts(&key as *const _ as *const u8, std::mem::size_of::<RedirectKey>())
        };
        let mut value = OriginalDestination::default();
        let val_bytes = unsafe {
            std::slice::from_raw_parts_mut(
                &mut value as *mut _ as *mut u8,
                std::mem::size_of::<OriginalDestination>(),
            )
        };

        if !lookup_map(map.fd(), key_bytes, val_bytes).ok()? {
            return None;
        }

        // Validate destination
        if (value.family != libc::AF_INET as u8 && value.family != libc::AF_INET6 as u8)
            || value.protocol != PROTOCOL_UDP
            || value.port == 0
        {
            return None;
        }
        Some(value)
    }

    /// Delete a UDP redirect entry from the redirect map.
    ///
    /// Builds a full `RedirectKey` from `listener_port` and `token_addr`, matching the C++ `releaseUdpDestination` semantics.
    pub fn release_udp_destination(&self, listener_port: u16, token_addr: &[u8; 4]) {
        let map = match &self.redirect_map {
            Some(m) => m,
            None => return,
        };

        let mut key = RedirectKey::default();
        key.family = libc::AF_INET as u8;
        key.protocol = PROTOCOL_UDP;
        key.listener_port = listener_port;
        key.token_addr[..4].copy_from_slice(token_addr);

        let key_bytes = unsafe {
            std::slice::from_raw_parts(&key as *const _ as *const u8, std::mem::size_of::<RedirectKey>())
        };
        // Accept success or ENOENT
        let _ = delete_map(map.fd(), key_bytes);
    }

    /// Add a TGID to the bypass map (public, matching user request).
    pub fn add_bypass_tgid(&self, tgid: u32) -> bool {
        let map = match &self.bypass_tgid_map {
            Some(m) => m,
            None => return false,
        };
        if tgid == 0 {
            return false;
        }
        let key_bytes = tgid.to_ne_bytes();
        update_map(map.fd(), &key_bytes, &[1u8], BPF_ANY_FLAG).is_ok()
    }

    /// Get the redirect map file descriptor.
    #[inline]
    pub fn redirect_map_fd(&self) -> RawFd {
        self.redirect_map.as_ref().map_or(-1, |m| m.fd())
    }

    /// Whether the runtime is currently attached to a cgroup.
    #[inline]
    pub fn active(&self) -> bool {
        self.attached
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /// Detach a single program by its stored fd and matching attach type.
    fn detach_program_inner(&self, prog: &Option<BpfMap>, attach_type: u32) {
        if let Some(p) = prog {
            if p.valid() {
                let _ = detach_program(self.cgroup_fd, p.fd(), attach_type);
            }
        }
    }
}

impl Drop for CgroupRuntime {
    fn drop(&mut self) {
        self.stop();
    }
}
