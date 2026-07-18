// eBPF bridge CLI entry point.
//
// Mirrors the C++ shell/main.cpp: --probe, --cleanup, --run modes.
// This binary targets Linux/Android only — eBPF requires Linux kernel ≥ 5.6.

#![cfg(unix)]
#![allow(dead_code)]
#![allow(non_upper_case_globals)]
#![allow(clippy::fn_to_numeric_cast)]

mod bpf;
mod bridge;
mod util;

use std::sync::atomic::{AtomicBool, Ordering};

static STOP_FLAG: AtomicBool = AtomicBool::new(false);

const VERSION: &str = "flycat-ebpf-bridge rust-1.0";

fn main() {
    let args: Vec<String> = std::env::args().collect();

    if args.len() < 2 {
        eprintln!("Usage: {} <command> [options]", args[0]);
        eprintln!("Commands:");
        eprintln!("  --probe   [--cgroup <path>]            Probe eBPF capabilities (default: /sys/fs/cgroup)");
        eprintln!("  --cleanup --cgroup <path>              Clean up stale eBPF hooks");
        eprintln!("  --run     --cgroup <path> --socks <port> --mihomo-pid <pid> [--bridge-port <port>] [options]");
        eprintln!("  --version                              Print version");
        std::process::exit(1);
    }

    let command = args[1].as_str();
    match command {
        "--version" => println!("{}", VERSION),
        "--probe" => probe(&optional_arg(&args, "--cgroup").unwrap_or_else(|| "/sys/fs/cgroup".to_string())),
        "--cleanup" => cleanup(&require_arg(&args, "--cgroup")),
        "--run" => run_bridge(&args),
        _ => {
            eprintln!("Unknown command: {}", command);
            std::process::exit(1);
        }
    }
}

fn require_arg(args: &[String], name: &str) -> String {
    for i in 0..args.len() {
        if args[i] == name && i + 1 < args.len() {
            return args[i + 1].clone();
        }
    }
    eprintln!("Missing required argument: {}", name);
    std::process::exit(1);
}

fn optional_arg(args: &[String], name: &str) -> Option<String> {
    for i in 0..args.len() {
        if args[i] == name && i + 1 < args.len() {
            return Some(args[i + 1].clone());
        }
    }
    None
}

fn parse_u16(args: &[String], name: &str) -> u16 {
    require_arg(args, name).parse().unwrap_or_else(|_| {
        eprintln!("Invalid value for {}", name);
        std::process::exit(1);
    })
}

fn parse_u32(args: &[String], name: &str) -> u32 {
    require_arg(args, name).parse().unwrap_or_else(|_| {
        eprintln!("Invalid value for {}", name);
        std::process::exit(1);
    })
}

fn normalize_cidr_address(address: &mut [u8; 16], family: u8, prefix: u8) {
    let len = if family == libc::AF_INET as u8 { 4 } else { 16 };
    let full = (prefix as usize) / 8;
    let partial = (prefix as usize) % 8;
    for i in full..len {
        if i == full && partial != 0 {
            address[i] &= !((1u8 << (8 - partial)) - 1);
        } else {
            address[i] = 0;
        }
    }
}

fn parse_cidr_list(args: &[String], name: &str) -> Vec<bpf::cgroup::CidrRule> {
    let Some(value) = optional_arg(args, name) else {
        return Vec::new();
    };
    let mut rules = Vec::new();
    for part in value.split(',') {
        let part = part.trim();
        if part.is_empty() {
            continue;
        }
        if let Some((addr_str, prefix_str)) = part.split_once('/') {
            let prefix: u8 = prefix_str.parse().unwrap_or(0);
            if let Ok(ip) = addr_str.parse::<std::net::Ipv4Addr>() {
                let mut address = [0u8; 16];
                address[..4].copy_from_slice(&ip.octets());
                normalize_cidr_address(&mut address, libc::AF_INET as u8, prefix);
                rules.push(bpf::cgroup::CidrRule { family: libc::AF_INET as u8, prefix_length: prefix, address });
            } else if let Ok(ip6) = addr_str.parse::<std::net::Ipv6Addr>() {
                let mut address = ip6.octets();
                normalize_cidr_address(&mut address, libc::AF_INET6 as u8, prefix);
                rules.push(bpf::cgroup::CidrRule {
                    family: libc::AF_INET6 as u8,
                    prefix_length: prefix,
                    address,
                });
            }
        }
    }
    rules
}

fn probe(cgroup_path: &str) {
    let cgroup_v2 = bpf::syscall::is_cgroup_v2_mount(cgroup_path);
    let map_fd = bpf::syscall::probe_map_create();
    let map_ok = map_fd.is_ok();
    let sock_addr = map_ok && bpf::syscall::probe_sock_addr_programs();
    let cgroup_attach = sock_addr && bpf::syscall::probe_sock_addr_cgroup_attach(cgroup_path);

    if let Ok(fd) = map_fd {
        unsafe { libc::close(fd); }
    }

    // M1: stderr details for each failed stage
    if !cgroup_v2 {
        eprintln!("eBPF probe: cgroup_v2 failed: {} is not cgroup v2", cgroup_path);
    }
    if !map_ok {
        eprintln!("eBPF probe: map_create failed");
    }
    if !sock_addr && map_ok {
        eprintln!("eBPF probe: socket_address_programs failed");
    }
    if !cgroup_attach && sock_addr {
        eprintln!("eBPF probe: cgroup_attach failed for {}", cgroup_path);
    }

    let result = serde_json::json!({
        "cgroup_v2": cgroup_v2,
        "bpf_map_create": map_ok,
        "bpf_socket_address": sock_addr,
        "bpf_cgroup_attach": cgroup_attach,
        "ready": cgroup_v2 && cgroup_attach,
    });
    println!("{}", result);
}

fn cleanup(cgroup_path: &str) {
    match bpf::syscall::cleanup_sock_addr_programs(cgroup_path) {
        Ok(()) => eprintln!("eBPF bridge: cleanup done"),
        Err(e) => eprintln!("eBPF bridge: cleanup failed: {}", e),
    }
}

fn run_bridge(args: &[String]) {
    let cgroup_path = require_arg(args, "--cgroup");
    let socks_port = parse_u16(args, "--socks");
    let mihomo_pid = parse_u32(args, "--mihomo-pid");
    let bridge_port: u16 = optional_arg(args, "--bridge-port")
        .map(|s| s.parse().unwrap_or(0))
        .unwrap_or(0);
    let _dns_mode: u8 = match optional_arg(&args, "--dns-mode").as_deref() {
        Some("hijack") | Some("proxy") | Some("0") => bpf::cgroup::DNS_MODE_HIJACK,
        Some("bypass") | Some("1") | None => bpf::cgroup::DNS_MODE_BYPASS,
        Some(other) => {
            eprintln!("Invalid --dns-mode: {} (expected hijack, bypass)", other);
            std::process::exit(1);
        }
    };
    let dns_port: u16 = optional_arg(&args, "--dns-port")
        .map(|s| s.parse().unwrap_or_else(|_| {
            eprintln!("Invalid --dns-port: {}", s);
            std::process::exit(1);
        }))
        .unwrap_or(0);
    let enable_ipv6 = optional_arg(args, "--ipv6").map(|s| s == "true" || s == "1").unwrap_or(true);
    let socks_host: [u8; 4] = optional_arg(args, "--socks-host")
        .map(|s| s.parse::<std::net::Ipv4Addr>().unwrap_or_else(|_| {
            eprintln!("Invalid --socks-host: {}", s);
            std::process::exit(1);
        }).octets())
        .unwrap_or([127, 0, 0, 1]);
    let bypass_cidrs = parse_cidr_list(args, "--bypass-cidrs");
    let uids: Vec<u32> = optional_arg(args, "--uids")
        .map(|s| s.split(',').filter_map(|u| u.trim().parse().ok()).collect())
        .unwrap_or_default();

    if !bpf::syscall::is_cgroup_v2_mount(&cgroup_path) {
        eprintln!("eBPF bridge: {} is not a cgroup v2 mount", cgroup_path);
        std::process::exit(1);
    }

    let map_probe = bpf::syscall::probe_map_create().is_ok();
    let prog_probe = bpf::syscall::probe_sock_addr_programs();
    let attach_probe = prog_probe && bpf::syscall::probe_sock_addr_cgroup_attach(&cgroup_path);
    if !map_probe {
        eprintln!("eBPF bridge: BPF_MAP_CREATE failed");
        std::process::exit(1);
    }
    if !prog_probe {
        eprintln!("eBPF bridge: required socket-address BPF hooks are unavailable");
        std::process::exit(1);
    }
    if !attach_probe {
        eprintln!("eBPF bridge: BPF_CGROUP_ATTACH probe failed");
        std::process::exit(1);
    }

    let mut cgroup = bpf::cgroup::CgroupRuntime::new();
    let uid_policy_mode: u8 = match optional_arg(args, "--uid-policy").as_deref() {
        Some("include") => 1,
        Some("exclude") => 2,
        Some("all") | None => 0,
        Some(other) => {
            eprintln!("Invalid --uid-policy: {} (expected all, include, exclude)", other);
            std::process::exit(1);
        }
    };

    // M2: Cleanup stale hooks before opening listeners
    if let Err(e) = bpf::syscall::cleanup_sock_addr_programs(&cgroup_path) {
        eprintln!("eBPF bridge: stale hook cleanup unavailable: {}", e);
    }

    // M2: Open TCP bridge first to get the actual listener port
    let tcp_config = bridge::tcp::TcpBridgeConfig {
        listen_port: bridge_port,
        proxy_ip: socks_host,
        proxy_port: socks_port,
    };
    let mut tcp_bridge = match bridge::tcp::TcpBridge::open(tcp_config, &STOP_FLAG, &cgroup) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("eBPF bridge: TCP listener open failed: {}", e);
            std::process::exit(1);
        }
    };
    let actual_port = tcp_bridge.listener_port();

    // DNS port collision check
    if _dns_mode == bpf::cgroup::DNS_MODE_HIJACK && dns_port == actual_port {
        eprintln!("eBPF bridge: DNS listener port collides with bridge port");
        std::process::exit(1);
    }

    // M2: Open UDP bridge on main thread (before cleanup and cgroup.start)
    let udp_config = bridge::udp::UdpBridgeConfig {
        listen_port: actual_port,
        proxy_ip: socks_host,
        proxy_port: socks_port,
    };
    let mut udp_bridge = match bridge::udp::UdpBridge::open(udp_config, &STOP_FLAG, &cgroup) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("eBPF bridge: UDP listener open failed: {}", e);
            std::process::exit(1);
        }
    };

    eprintln!(
        "eBPF bridge: startup cgroup={} bridge={}:{} socks={}:{} mihomo-pid={} dns-mode={} dns-port={} ipv6={} bypass-cidrs={}",
        cgroup_path, "127.0.0.1", actual_port, "127.0.0.1", socks_port, mihomo_pid, _dns_mode, dns_port, enable_ipv6, bypass_cidrs.len()
    );

    // Start eBPF hooks with the actual port
    if let Err(e) = cgroup.start(
        &cgroup_path,
        actual_port,
        std::process::id(),
        mihomo_pid,
        uid_policy_mode,
        &uids,
        _dns_mode,
        dns_port,
        enable_ipv6,
        &bypass_cidrs,
    ) {
        eprintln!("eBPF bridge: cgroup setup failed: {}", e);
        std::process::exit(1);
    }

    // M1: Install signal handlers after cgroup.start() succeeds
    unsafe {
        let mut sa = std::mem::zeroed::<libc::sigaction>();
        sa.sa_sigaction = handle_signal as *const () as usize;
        sa.sa_flags = libc::SA_RESTART;
        libc::sigemptyset(&mut sa.sa_mask);
        libc::sigaction(libc::SIGTERM, &sa, std::ptr::null_mut());
        libc::sigaction(libc::SIGINT, &sa, std::ptr::null_mut());
        let mut sa_ign = std::mem::zeroed::<libc::sigaction>();
        sa_ign.sa_sigaction = libc::SIG_IGN as usize;
        sa_ign.sa_flags = libc::SA_RESTART;
        libc::sigemptyset(&mut sa_ign.sa_mask);
        libc::sigaction(libc::SIGPIPE, &sa_ign, std::ptr::null_mut());
    }

    if mihomo_pid == 0 {
        eprintln!("eBPF bridge: warning: --mihomo-pid is omitted; mihomo must be outside this cgroup or it may loop");
    }

    // M2: UDP bridge already opened above; spawn thread to run event loop
    let udp_handle = std::thread::spawn(move || {
        if let Err(e) = udp_bridge.run() {
            eprintln!("eBPF bridge: UDP event loop failed: {}", e);
            STOP_FLAG.store(true, Ordering::Relaxed);
        }
    });

    eprintln!(
        "eBPF bridge: tcp4/udp4 listener on 127.0.0.1:{}, mihomo SOCKS 127.0.0.1:{}",
        actual_port, socks_port
    );

    if let Err(e) = tcp_bridge.run() {
        eprintln!("eBPF bridge: TCP event loop failed: {}", e);
    }

    STOP_FLAG.store(true, Ordering::Relaxed);
    let _ = udp_handle.join();

    cgroup.stop();
    let _ = bpf::syscall::cleanup_sock_addr_programs(&cgroup_path);
    eprintln!("eBPF bridge: stopped");
}

extern "C" fn handle_signal(_sig: i32) {
    STOP_FLAG.store(true, Ordering::Relaxed);
}
