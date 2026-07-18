// BPF syscall wrappers — raw Linux BPF API via SYS_bpf.
//
// Mirrors the C++ bpf_syscall.cpp: creates maps, loads/attaches/detaches programs, and provides probe helpers for capability detection.

use std::ffi::CStr;
use std::mem::size_of;
use std::os::unix::io::RawFd;

// ── BPF constants ───────────────────────────────────────────────────────────

pub const BPF_MAP_CREATE: u32 = 0;
pub const BPF_MAP_LOOKUP_ELEM: u32 = 1;
pub const BPF_MAP_UPDATE_ELEM: u32 = 2;
pub const BPF_MAP_DELETE_ELEM: u32 = 3;
pub const BPF_PROG_LOAD: u32 = 5;
pub const BPF_PROG_ATTACH: u32 = 8;
pub const BPF_PROG_DETACH: u32 = 9;
pub const BPF_OBJ_GET_INFO_BY_FD: u32 = 15;
pub const BPF_PROG_QUERY: u32 = 10;

pub const BPF_PROG_TYPE_SK_SKB: u32 = 3;
pub const BPF_PROG_TYPE_CGROUP_SOCK_ADDR: u32 = 9;

pub const BPF_CGROUP_INET4_CONNECT: u32 = 2;
pub const BPF_CGROUP_INET6_CONNECT: u32 = 3;
pub const BPF_CGROUP_UDP4_SENDMSG: u32 = 6;
pub const BPF_CGROUP_UDP6_SENDMSG: u32 = 7;
pub const BPF_CGROUP_UDP4_RECVMSG: u32 = 10;
pub const BPF_CGROUP_UDP6_RECVMSG: u32 = 11;

pub const BPF_MAP_TYPE_HASH: u32 = 1;
pub const BPF_MAP_TYPE_ARRAY: u32 = 2;
pub const BPF_MAP_TYPE_LRU_HASH: u32 = 9;
pub const BPF_MAP_TYPE_LPM_TRIE: u32 = 11;

pub const BPF_F_NO_PREALLOC: u32 = 1;
pub const BPF_F_ALLOW_MULTI: u32 = 2; // not available on older kernels

pub const BPF_LD: u8 = 0x00;
pub const BPF_LDX: u8 = 0x01;
pub const BPF_ST: u8 = 0x02;
pub const BPF_STX: u8 = 0x03;
pub const BPF_ALU: u8 = 0x04;
pub const BPF_JMP: u8 = 0x05;
pub const BPF_ALU64: u8 = 0x07;

pub const BPF_K: u8 = 0x00;
pub const BPF_X: u8 = 0x08;

pub const BPF_ADD: u8 = 0x00;
pub const BPF_SUB: u8 = 0x10;
pub const BPF_MUL: u8 = 0x20;
pub const BPF_DIV: u8 = 0x30;
pub const BPF_OR: u8 = 0x40;
pub const BPF_AND: u8 = 0x50;
pub const BPF_LSH: u8 = 0x60;
pub const BPF_RSH: u8 = 0x70;
pub const BPF_MOV: u8 = 0xb0;
pub const BPF_ARSH: u8 = 0xc0;

pub const BPF_JA: u8 = 0x00;
pub const BPF_JEQ: u8 = 0x10;
pub const BPF_JGT: u8 = 0x20;
pub const BPF_JGE: u8 = 0x30;
pub const BPF_JSET: u8 = 0x40;

pub const BPF_CALL: u8 = 0x80;
pub const BPF_EXIT: u8 = 0x90;

pub const BPF_W: u8 = 0x00;
pub const BPF_H: u8 = 0x08;
pub const BPF_B: u8 = 0x10;
pub const BPF_DW: u8 = 0x18;

pub const BPF_IMM: u8 = 0x00;
pub const BPF_MEM: u8 = 0x60;

pub const BPF_PSEUDO_MAP_FD: u8 = 1;

pub const BPF_FUNC_map_lookup_elem: i32 = 1;
pub const BPF_FUNC_map_update_elem: i32 = 2;
pub const BPF_FUNC_map_delete_elem: i32 = 3;
pub const BPF_FUNC_get_current_pid_tgid: i32 = 14;
pub const BPF_FUNC_get_current_uid_gid: i32 = 15;
pub const BPF_FUNC_get_socket_cookie: i32 = 37;
pub const BPF_FUNC_get_prandom_u32: i32 = 7;
pub const BPF_FUNC_trace_printk: i32 = 6;

pub const BPF_ANY: u64 = 0;

pub const CGROUP2_SUPER_MAGIC: u64 = 0x63677270;

// ── BPF instruction ─────────────────────────────────────────────────────────

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct BpfInsn {
    pub code: u8,
    pub dst_reg: u8,
    pub src_reg: u8,
    pub off: i16,
    pub imm: i32,
}

impl BpfInsn {
    pub fn size() -> usize { size_of::<Self>() } // 8 bytes
}

// ── BPF map fd (RAII) ──────────────────────────────────────────────────────

pub struct BpfMap {
    fd: RawFd,
}

impl BpfMap {
    pub fn new(fd: RawFd) -> Self {
        Self { fd }
    }

    pub fn fd(&self) -> RawFd {
        self.fd
    }

    pub fn valid(&self) -> bool {
        self.fd >= 0
    }

    pub fn reset(&mut self) {
        if self.fd >= 0 {
            unsafe { libc::close(self.fd); }
            self.fd = -1;
        }
    }
}

impl Drop for BpfMap {
    fn drop(&mut self) {
        if self.fd >= 0 {
            unsafe { libc::close(self.fd); }
        }
    }
}

// Prevent accidental double-close; BpfMap is movable but not copyable.
// ── Raw bpf_attr union (largest member = map_create at ~72 bytes) ───────────

pub const BPF_ATTR_SIZE: usize = 128;

#[repr(C)]
struct BpfAttrMapCreate {
    map_type: u32,
    key_size: u32,
    value_size: u32,
    max_entries: u32,
    map_flags: u32,
    inner_map_fd: u32,
    numa_node: u32,
    map_name: [u8; 16],
    map_ifindex: u32,
    btf_fd: u32,
    btf_key_type_id: u32,
    btf_value_type_id: u32,
    btf_vmlinux_value_type_id: u32,
    map_extra: u64,
}

#[repr(C)]
struct BpfAttrMapOp {
    map_fd: u32,
    key: u64,    // __aligned_u64
    value: u64,  // __aligned_u64
    flags: u64,
}

#[repr(C)]
struct BpfAttrProgLoad {
    prog_type: u32,
    insn_cnt: u32,
    insns: u64,         // __aligned_u64
    license: u64,       // __aligned_u64
    log_level: u32,
    log_size: u32,
    log_buf: u64,       // __aligned_u64
    kern_version: u32,
    prog_flags: u32,
    prog_name: [u8; 16],
    prog_ifindex: u32,
    expected_attach_type: u32,
    prog_btf_fd: u32,
    func_info_rec_size: u32,
    func_info: u64,
    func_info_cnt: u32,
    line_info_rec_size: u32,
    line_info: u64,
    line_info_cnt: u32,
    attach_btf_id: u32,
    attach_prog_fd: u32,
}

#[repr(C)]
struct BpfAttrProgAttach {
    target_fd: u32,
    attach_bpf_fd: u32,
    attach_type: u32,
    attach_flags: u32,
    replace_bpf_fd: u32,
}

#[repr(C)]
struct BpfAttrProgDetach {
    target_fd: u32,
    detach_bpf_fd: u32,
    detach_type: u32,
}

#[repr(C)]
struct BpfAttrProgQuery {
    target_fd: u32,
    attach_type: u32,
    query_flags: u32,
    attach_flags: u32,
    prog_ids: u64,      // __aligned_u64
    prog_cnt: u32,
}

#[repr(C)]
struct BpfAttrObjInfo {
    bpf_fd: u32,
    info_len: u32,
    info: u64,          // __aligned_u64
}

#[repr(C)]
#[derive(Clone, Copy, Default)]
struct BpfProgInfo {
    _type: u32,
    id: u32,
    tag: [u8; 8],
    jited_prog_len: u32,
    xlated_prog_len: u32,
    jited_prog_insns: u64,
    xlated_prog_insns: u64,
    load_time: u64,
    created_by_uid: u32,
    nr_map_ids: u32,
    map_ids: u64,
    name: [u8; 16],
    ifindex: u32,
    _gpl_compatible: u32, // bitfield — packed as u32
    netns_dev: u64,
    netns_ino: u64,
    nr_jited_ksyms: u32,
    nr_jited_func_lens: u32,
    jited_ksyms: u64,
    jited_func_lens: u64,
    btf_id: u32,
    func_info_rec_size: u32,
    func_info: u64,
    nr_func_info: u32,
    line_info_rec_size: u32,
    line_info: u64,
    nr_line_info: u32,
    jited_line_info: u64,
    nr_jited_line_info: u32,
    line_info_rec_size_jited: u32,
    jited_func_lens_copy: u32,
}

// ── syscall wrapper ─────────────────────────────────────────────────────────

fn bpf_syscall(cmd: u32, attr: &mut [u8; BPF_ATTR_SIZE]) -> i64 {
    unsafe { libc::syscall(libc::SYS_bpf, cmd, attr.as_mut_ptr(), attr.len()) as i64 }
}

fn last_os_error() -> String {
    std::io::Error::last_os_error().to_string()
}

// ── Map operations ──────────────────────────────────────────────────────────

pub fn create_map(map_type: u32, key_size: u32, value_size: u32, max_entries: u32, flags: u32) -> Result<RawFd, String> {
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let mc: &mut BpfAttrMapCreate = unsafe { std::mem::transmute(&mut attr) };
    mc.map_type = map_type;
    mc.key_size = key_size;
    mc.value_size = value_size;
    mc.max_entries = max_entries;
    mc.map_flags = flags;
    let name = b"fc_ebpf_map\0";
    mc.map_name[..name.len()].copy_from_slice(name);
    let fd = bpf_syscall(BPF_MAP_CREATE, &mut attr);
    if fd < 0 {
        return Err(format!("BPF_MAP_CREATE failed: {}", last_os_error()));
    }
    Ok(fd as RawFd)
}

pub fn update_map(map_fd: RawFd, key: &[u8], value: &[u8], flags: u64) -> Result<(), String> {
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let op: &mut BpfAttrMapOp = unsafe { std::mem::transmute(&mut attr) };
    op.map_fd = map_fd as u32;
    op.key = key.as_ptr() as u64;
    op.value = value.as_ptr() as u64;
    op.flags = flags;
    let r = bpf_syscall(BPF_MAP_UPDATE_ELEM, &mut attr);
    if r < 0 {
        return Err(format!("BPF_MAP_UPDATE_ELEM failed: {}", last_os_error()));
    }
    Ok(())
}

pub fn lookup_map(map_fd: RawFd, key: &[u8], value: &mut [u8]) -> Result<bool, String> {
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let op: &mut BpfAttrMapOp = unsafe { std::mem::transmute(&mut attr) };
    op.map_fd = map_fd as u32;
    op.key = key.as_ptr() as u64;
    op.value = value.as_mut_ptr() as u64;
    let r = bpf_syscall(BPF_MAP_LOOKUP_ELEM, &mut attr);
    if r < 0 {
        let err = std::io::Error::last_os_error();
        if err.raw_os_error() == Some(libc::ENOENT) {
            return Ok(false);
        }
        return Err(format!("BPF_MAP_LOOKUP_ELEM failed: {}", err));
    }
    Ok(true)
}

pub fn delete_map(map_fd: RawFd, key: &[u8]) -> Result<bool, String> {
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let op: &mut BpfAttrMapOp = unsafe { std::mem::transmute(&mut attr) };
    op.map_fd = map_fd as u32;
    op.key = key.as_ptr() as u64;
    let r = bpf_syscall(BPF_MAP_DELETE_ELEM, &mut attr);
    if r < 0 {
        let err = std::io::Error::last_os_error();
        if err.raw_os_error() == Some(libc::ENOENT) {
            return Ok(false);
        }
        return Err(format!("BPF_MAP_DELETE_ELEM failed: {}", err));
    }
    Ok(true)
}

// ── Program load / attach / detach ──────────────────────────────────────────

pub fn load_program(insns: &[BpfInsn], prog_type: u32, name: &str) -> Result<RawFd, String> {
    // First attempt: silent load (no verifier log)
    {
        let mut attr = [0u8; BPF_ATTR_SIZE];
        let pl: &mut BpfAttrProgLoad = unsafe { std::mem::transmute(&mut attr) };
        pl.prog_type = prog_type;
        pl.insn_cnt = insns.len() as u32;
        pl.insns = insns.as_ptr() as u64;
        pl.license = b"GPL\0".as_ptr() as u64;
        pl.log_level = 0;
        pl.log_size = 0;
        pl.log_buf = 0;
        let name_bytes = name.as_bytes();
        let copy_len = name_bytes.len().min(15);
        pl.prog_name[..copy_len].copy_from_slice(&name_bytes[..copy_len]);

        let fd = bpf_syscall(BPF_PROG_LOAD, &mut attr);
        if fd >= 0 {
            return Ok(fd as RawFd);
        }
    }

    // Retry with verifier log
    let mut log_buf = vec![0u8; 65536];
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let pl: &mut BpfAttrProgLoad = unsafe { std::mem::transmute(&mut attr) };
    pl.prog_type = prog_type;
    pl.insn_cnt = insns.len() as u32;
    pl.insns = insns.as_ptr() as u64;
    pl.license = b"GPL\0".as_ptr() as u64;
    pl.log_level = 1;
    pl.log_size = log_buf.len() as u32;
    pl.log_buf = log_buf.as_mut_ptr() as u64;
    let name_bytes = name.as_bytes();
    let copy_len = name_bytes.len().min(15);
    pl.prog_name[..copy_len].copy_from_slice(&name_bytes[..copy_len]);

    let fd = bpf_syscall(BPF_PROG_LOAD, &mut attr);
    if fd < 0 {
        let log = CStr::from_bytes_until_nul(&log_buf)
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        return Err(format!("BPF_PROG_LOAD({}) failed: {} log={}", name, last_os_error(), log));
    }
    Ok(fd as RawFd)
}

pub fn load_program_for_attach_type(insns: &[BpfInsn], prog_type: u32, attach_type: u32, name: &str) -> Result<RawFd, String> {
    // First attempt: silent load (no verifier log)
    {
        let mut attr = [0u8; BPF_ATTR_SIZE];
        let pl: &mut BpfAttrProgLoad = unsafe { std::mem::transmute(&mut attr) };
        pl.prog_type = prog_type;
        pl.insn_cnt = insns.len() as u32;
        pl.insns = insns.as_ptr() as u64;
        pl.license = b"GPL\0".as_ptr() as u64;
        pl.log_level = 0;
        pl.log_size = 0;
        pl.log_buf = 0;
        pl.expected_attach_type = attach_type;
        let name_bytes = name.as_bytes();
        let copy_len = name_bytes.len().min(15);
        pl.prog_name[..copy_len].copy_from_slice(&name_bytes[..copy_len]);

        let fd = bpf_syscall(BPF_PROG_LOAD, &mut attr);
        if fd >= 0 {
            return Ok(fd as RawFd);
        }
    }

    // Retry with verifier log
    let mut log_buf = vec![0u8; 65536];
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let pl: &mut BpfAttrProgLoad = unsafe { std::mem::transmute(&mut attr) };
    pl.prog_type = prog_type;
    pl.insn_cnt = insns.len() as u32;
    pl.insns = insns.as_ptr() as u64;
    pl.license = b"GPL\0".as_ptr() as u64;
    pl.log_level = 1;
    pl.log_size = log_buf.len() as u32;
    pl.log_buf = log_buf.as_mut_ptr() as u64;
    pl.expected_attach_type = attach_type;
    let name_bytes = name.as_bytes();
    let copy_len = name_bytes.len().min(15);
    pl.prog_name[..copy_len].copy_from_slice(&name_bytes[..copy_len]);

    let fd = bpf_syscall(BPF_PROG_LOAD, &mut attr);
    if fd < 0 {
        let log = CStr::from_bytes_until_nul(&log_buf)
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        return Err(format!("BPF_PROG_LOAD({}) failed: {} log={}", name, last_os_error(), log));
    }
    Ok(fd as RawFd)
}

pub fn attach_program(target_fd: RawFd, prog_fd: RawFd, attach_type: u32) -> Result<(), String> {
    // Try with BPF_F_ALLOW_MULTI first, fall back without.
    for flags in [BPF_F_ALLOW_MULTI, 0] {
        let mut attr = [0u8; BPF_ATTR_SIZE];
        let pa: &mut BpfAttrProgAttach = unsafe { std::mem::transmute(&mut attr) };
        pa.target_fd = target_fd as u32;
        pa.attach_bpf_fd = prog_fd as u32;
        pa.attach_type = attach_type;
        pa.attach_flags = flags;
        pa.replace_bpf_fd = u32::MAX; // invalid fd = no replace
        let r = bpf_syscall(BPF_PROG_ATTACH, &mut attr);
        if r >= 0 {
            return Ok(());
        }
        let err = std::io::Error::last_os_error();
        #[allow(unreachable_patterns)]
        if flags == BPF_F_ALLOW_MULTI && matches!(err.raw_os_error(), Some(libc::EINVAL | libc::EPERM | libc::ENOTSUP | libc::EOPNOTSUPP)) {
            continue; // retry without ALLOW_MULTI
        }
        return Err(format!("BPF_PROG_ATTACH type={} failed: {}", attach_type, err));
    }
    unreachable!()
}

pub fn detach_program(target_fd: RawFd, prog_fd: RawFd, attach_type: u32) -> Result<(), String> {
    let mut attr = [0u8; BPF_ATTR_SIZE];
    let pd: &mut BpfAttrProgDetach = unsafe { std::mem::transmute(&mut attr) };
    pd.target_fd = target_fd as u32;
    pd.detach_bpf_fd = prog_fd as u32;
    pd.detach_type = attach_type;
    let r = bpf_syscall(BPF_PROG_DETACH, &mut attr);
    if r < 0 {
        return Err(format!("BPF_PROG_DETACH type={} failed: {}", attach_type, last_os_error()));
    }
    Ok(())
}

// ── Probe helpers ───────────────────────────────────────────────────────────

pub fn is_cgroup_v2_mount(path: &str) -> bool {
    let c_path = std::ffi::CString::new(path).unwrap();
    let mut stat: libc::statfs = unsafe { std::mem::zeroed() };
    let r = unsafe { libc::statfs(c_path.as_ptr(), &mut stat) };
    r == 0 && stat.f_type as u64 == CGROUP2_SUPER_MAGIC
}

pub fn probe_map_create() -> Result<RawFd, String> {
    create_map(BPF_MAP_TYPE_ARRAY, 4, 4, 1, 0)
}

pub fn probe_sock_addr_programs() -> bool {
    let insns = [
        BpfInsn { code: BPF_ALU64 | BPF_MOV | BPF_K, dst_reg: 0, src_reg: 0, off: 0, imm: 1 },
        BpfInsn { code: BPF_JMP | BPF_EXIT, dst_reg: 0, src_reg: 0, off: 0, imm: 0 },
    ];
    load_program(&insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, "fc_probe").is_ok()
}

pub fn probe_sock_addr_cgroup_attach(cgroup_path: &str) -> bool {
    let c_path = match std::ffi::CString::new(cgroup_path) {
        Ok(p) => p,
        Err(_) => return false,
    };
    let cgroup_fd = unsafe { libc::open(c_path.as_ptr(), libc::O_RDONLY | libc::O_CLOEXEC) };
    if cgroup_fd < 0 {
        return false;
    }
    let insns = [
        BpfInsn { code: BPF_ALU64 | BPF_MOV | BPF_K, dst_reg: 0, src_reg: 0, off: 0, imm: 1 },
        BpfInsn { code: BPF_JMP | BPF_EXIT, dst_reg: 0, src_reg: 0, off: 0, imm: 0 },
    ];
    let attach_types = [
        BPF_CGROUP_INET4_CONNECT, BPF_CGROUP_UDP4_SENDMSG, BPF_CGROUP_UDP4_RECVMSG,
        BPF_CGROUP_INET6_CONNECT, BPF_CGROUP_UDP6_SENDMSG, BPF_CGROUP_UDP6_RECVMSG,
    ];
    let mut ok = true;
    for &at in &attach_types {
        let prog_fd = match load_program(&insns, BPF_PROG_TYPE_CGROUP_SOCK_ADDR, "fc_att") {
            Ok(fd) => fd,
            Err(_) => { ok = false; break; }
        };
        if attach_program(cgroup_fd, prog_fd, at).is_err() {
            unsafe { libc::close(prog_fd); }
            ok = false;
            break;
        }
        let _ = detach_program(cgroup_fd, prog_fd, at);
        unsafe { libc::close(prog_fd); }
    }
    unsafe { libc::close(cgroup_fd); }
    ok
}

/// Clean up socket-address programs with our name prefix.
pub fn cleanup_sock_addr_programs(cgroup_path: &str) -> Result<(), String> {
    let c_path = std::ffi::CString::new(cgroup_path).map_err(|e| e.to_string())?;
    let cgroup_fd = unsafe { libc::open(c_path.as_ptr(), libc::O_RDONLY | libc::O_CLOEXEC) };
    if cgroup_fd < 0 {
        return Err(format!("open cgroup failed: {}", last_os_error()));
    }

    let attach_types = [
        BPF_CGROUP_INET4_CONNECT, BPF_CGROUP_INET6_CONNECT,
        BPF_CGROUP_UDP4_SENDMSG, BPF_CGROUP_UDP6_SENDMSG,
        BPF_CGROUP_UDP4_RECVMSG, BPF_CGROUP_UDP6_RECVMSG,
    ];

    for &at in &attach_types {
        // S1: Re-query in a loop until no more matching programs are found.
        // This mirrors the C++ cleanupSocketAddressPrograms() pattern.
        loop {
            let mut prog_ids = [0u32; 64];
            let mut prog_cnt = prog_ids.len() as u32;
            let mut attr = [0u8; BPF_ATTR_SIZE];
            let pq: &mut BpfAttrProgQuery = unsafe { std::mem::transmute(&mut attr) };
            pq.target_fd = cgroup_fd as u32;
            pq.attach_type = at;
            pq.prog_ids = prog_ids.as_mut_ptr() as u64;
            pq.prog_cnt = prog_cnt;

            if bpf_syscall(BPF_PROG_QUERY, &mut attr) < 0 {
                break;
            }
            prog_cnt = pq.prog_cnt;
            let queried_full = (prog_cnt as usize) >= prog_ids.len();

            let mut removed = false;
            for &pid in &prog_ids[..prog_cnt as usize] {
                // Use raw syscall for BPF_PROG_GET_FD_BY_ID (cmd=14).
                let mut id_attr = [0u8; BPF_ATTR_SIZE];
                id_attr[0..4].copy_from_slice(&pid.to_ne_bytes());
                let prog_fd_raw = bpf_syscall(14, &mut id_attr);
                if prog_fd_raw < 0 {
                    continue;
                }

                let mut info = BpfProgInfo::default();
                let mut info_attr2 = [0u8; BPF_ATTR_SIZE];
                let oi2: &mut BpfAttrObjInfo = unsafe { std::mem::transmute(&mut info_attr2) };
                oi2.bpf_fd = prog_fd_raw as u32;
                oi2.info_len = size_of::<BpfProgInfo>() as u32;
                oi2.info = &mut info as *mut BpfProgInfo as u64;

                if bpf_syscall(BPF_OBJ_GET_INFO_BY_FD, &mut info_attr2) >= 0 {
                    let prog_name = CStr::from_bytes_until_nul(&info.name)
                        .map(|s| s.to_string_lossy().into_owned())
                        .unwrap_or_default();
                    if prog_name.starts_with("fc_") || prog_name.starts_with("yb_") {
                        let _ = detach_program(cgroup_fd, prog_fd_raw as RawFd, at);
                        removed = true;
                    }
                }
                unsafe { libc::close(prog_fd_raw as RawFd); }
            }
            // Stop when nothing was removed and the buffer wasn't full
            if !removed && !queried_full {
                break;
            }
        }
    }
    unsafe { libc::close(cgroup_fd); }
    Ok(())
}

// Helper: get UID from context
pub const fn ctx_offset_userns_uid() -> i16 { 16 } // offsetof(bpf_sock_addr, userns_uid) — typically 16

// Socket address context field offsets (struct bpf_sock_addr)
// These are u32 field offsets from the start of the context.
pub const CTX_USERNS_UID: i16 = 16;

// BPF helper function IDs
pub const fn helper_map_lookup_elem() -> i32 { BPF_FUNC_map_lookup_elem }
pub const fn helper_map_update_elem() -> i32 { BPF_FUNC_map_update_elem }
pub const fn helper_map_delete_elem() -> i32 { BPF_FUNC_map_delete_elem }
pub const fn helper_get_current_uid_gid() -> i32 { BPF_FUNC_get_current_uid_gid }
pub const fn helper_get_current_pid_tgid() -> i32 { BPF_FUNC_get_current_pid_tgid }
pub const fn helper_get_socket_cookie() -> i32 { BPF_FUNC_get_socket_cookie }
pub const fn helper_get_prandom_u32() -> i32 { BPF_FUNC_get_prandom_u32 }

// Map types
pub const MAP_TYPE_HASH: u32 = BPF_MAP_TYPE_HASH;
pub const MAP_TYPE_ARRAY: u32 = BPF_MAP_TYPE_ARRAY;
pub const MAP_TYPE_LRU_HASH: u32 = BPF_MAP_TYPE_LRU_HASH;
pub const MAP_TYPE_LPM_TRIE: u32 = BPF_MAP_TYPE_LPM_TRIE;

// Attach types
pub const ATTACH_INET4_CONNECT: u32 = BPF_CGROUP_INET4_CONNECT;
pub const ATTACH_INET6_CONNECT: u32 = BPF_CGROUP_INET6_CONNECT;
pub const ATTACH_UDP4_SENDMSG: u32 = BPF_CGROUP_UDP4_SENDMSG;
pub const ATTACH_UDP6_SENDMSG: u32 = BPF_CGROUP_UDP6_SENDMSG;
pub const ATTACH_UDP4_RECVMSG: u32 = BPF_CGROUP_UDP4_RECVMSG;
pub const ATTACH_UDP6_RECVMSG: u32 = BPF_CGROUP_UDP6_RECVMSG;

pub const ALL_ATTACH_TYPES: [u32; 6] = [
    BPF_CGROUP_INET4_CONNECT, BPF_CGROUP_INET6_CONNECT,
    BPF_CGROUP_UDP4_SENDMSG, BPF_CGROUP_UDP6_SENDMSG,
    BPF_CGROUP_UDP4_RECVMSG, BPF_CGROUP_UDP6_RECVMSG,
];

pub const BPF_ANY_FLAG: u64 = BPF_ANY;
