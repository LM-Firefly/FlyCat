// Socket and epoll utilities.
//
// Mirrors the C++ include/socket_utils.hpp.

use std::os::unix::io::RawFd;

pub const RELAY_BUFFER_SIZE: usize = 32 * 1024;

pub fn set_non_blocking(fd: RawFd) -> Result<(), String> {
    let flags = unsafe { libc::fcntl(fd, libc::F_GETFL) };
    if flags < 0 {
        return Err(format!("fcntl F_GETFL: {}", std::io::Error::last_os_error()));
    }
    if unsafe { libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK) } < 0 {
        return Err(format!("fcntl F_SETFL: {}", std::io::Error::last_os_error()));
    }
    Ok(())
}

pub fn add_epoll(epoll_fd: RawFd, fd: RawFd, events: u32, data: u64) -> Result<(), String> {
    let mut ev = libc::epoll_event { events, u64: data };
    if unsafe { libc::epoll_ctl(epoll_fd, libc::EPOLL_CTL_ADD, fd, &mut ev) } < 0 {
        return Err(format!("epoll_ctl ADD: {}", std::io::Error::last_os_error()));
    }
    Ok(())
}

pub fn modify_epoll(epoll_fd: RawFd, fd: RawFd, events: u32, data: u64) -> Result<(), String> {
    let mut ev = libc::epoll_event { events, u64: data };
    if unsafe { libc::epoll_ctl(epoll_fd, libc::EPOLL_CTL_MOD, fd, &mut ev) } < 0 {
        return Err(format!("epoll_ctl MOD: {}", std::io::Error::last_os_error()));
    }
    Ok(())
}

pub fn close_fd(fd: RawFd) {
    if fd >= 0 {
        unsafe { libc::close(fd); }
    }
}

pub fn monotonic_ms() -> u64 {
    let mut ts: libc::timespec = unsafe { std::mem::zeroed() };
    unsafe { libc::clock_gettime(libc::CLOCK_MONOTONIC, &mut ts); }
    (ts.tv_sec as u64) * 1000 + (ts.tv_nsec as u64) / 1_000_000
}

pub fn create_tcp_listener(port: u16) -> Result<RawFd, String> {
    let fd = unsafe { libc::socket(libc::AF_INET, libc::SOCK_STREAM | libc::SOCK_NONBLOCK | libc::SOCK_CLOEXEC, 0) };
    if fd < 0 {
        return Err(format!("socket: {}", std::io::Error::last_os_error()));
    }
    let opt: i32 = 1;
    unsafe {
        libc::setsockopt(fd, libc::SOL_SOCKET, libc::SO_REUSEADDR, &opt as *const _ as *const libc::c_void, 4);
    }
    let addr = libc::sockaddr_in {
        sin_family: libc::AF_INET as u16,
        sin_port: port.to_be(),
        sin_addr: libc::in_addr { s_addr: u32::from_be_bytes([127, 0, 0, 1]).to_be() },
        sin_zero: [0; 8],
    };
    if unsafe { libc::bind(fd, &addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t) } < 0 {
        close_fd(fd);
        return Err(format!("bind: {}", std::io::Error::last_os_error()));
    }
    if unsafe { libc::listen(fd, 128) } < 0 {
        close_fd(fd);
        return Err(format!("listen: {}", std::io::Error::last_os_error()));
    }
    Ok(fd)
}

pub fn create_udp_socket(port: u16) -> Result<RawFd, String> {
    let fd = unsafe { libc::socket(libc::AF_INET, libc::SOCK_DGRAM | libc::SOCK_NONBLOCK | libc::SOCK_CLOEXEC, 0) };
    if fd < 0 {
        return Err(format!("socket: {}", std::io::Error::last_os_error()));
    }
    let opt: i32 = 1;
    unsafe {
        libc::setsockopt(fd, libc::SOL_SOCKET, libc::SO_REUSEADDR, &opt as *const _ as *const libc::c_void, 4);
        // Enable IP_PKTINFO to get destination token from eBPF-rewritten address
        libc::setsockopt(fd, libc::IPPROTO_IP, libc::IP_PKTINFO, &opt as *const _ as *const libc::c_void, 4);
    }
    let addr = libc::sockaddr_in {
        sin_family: libc::AF_INET as u16,
        sin_port: port.to_be(),
        sin_addr: libc::in_addr { s_addr: u32::from_be_bytes([127, 0, 0, 1]).to_be() },
        sin_zero: [0; 8],
    };
    if unsafe { libc::bind(fd, &addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t) } < 0 {
        close_fd(fd);
        return Err(format!("bind: {}", std::io::Error::last_os_error()));
    }
    Ok(fd)
}

/// Connect to SOCKS5 proxy (mihomo).
pub fn connect_proxy(proxy_ip: [u8; 4], proxy_port: u16) -> Result<RawFd, String> {
    let fd = unsafe { libc::socket(libc::AF_INET, libc::SOCK_STREAM | libc::SOCK_NONBLOCK | libc::SOCK_CLOEXEC, 0) };
    if fd < 0 {
        return Err(format!("socket: {}", std::io::Error::last_os_error()));
    }
    let addr = libc::sockaddr_in {
        sin_family: libc::AF_INET as u16,
        sin_port: proxy_port.to_be(),
        sin_addr: libc::in_addr { s_addr: u32::from_be_bytes(proxy_ip).to_be() },
        sin_zero: [0; 8],
    };
    let r = unsafe { libc::connect(fd, &addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t) };
    if r < 0 {
        let err = std::io::Error::last_os_error();
        if err.raw_os_error() != Some(libc::EINPROGRESS) {
            close_fd(fd);
            return Err(format!("connect: {}", err));
        }
    }
    Ok(fd)
}

/// Splice data between two fds via a pipe. Returns bytes transferred or error.
pub fn splice_data(from: RawFd, to: RawFd, len: usize) -> Result<usize, String> {
    // Create a pipe for splice
    let mut pipe_fds = [0i32; 2];
    if unsafe { libc::pipe2(pipe_fds.as_mut_ptr(), libc::O_CLOEXEC) } < 0 {
        return Err(format!("pipe2: {}", std::io::Error::last_os_error()));
    }
    let (read_fd, write_fd) = (pipe_fds[0], pipe_fds[1]);

    let spliced = unsafe {
        libc::splice(from, std::ptr::null_mut(), write_fd, std::ptr::null_mut(), len, libc::SPLICE_F_MOVE | libc::SPLICE_F_NONBLOCK)
    };
    if spliced <= 0 {
        close_fd(read_fd);
        close_fd(write_fd);
        return Err(format!("splice in: {}", std::io::Error::last_os_error()));
    }

    let sent = unsafe {
        libc::splice(read_fd, std::ptr::null_mut(), to, std::ptr::null_mut(), spliced as usize, libc::SPLICE_F_MOVE | libc::SPLICE_F_NONBLOCK)
    };
    close_fd(read_fd);
    close_fd(write_fd);

    if sent <= 0 {
        return Err(format!("splice out: {}", std::io::Error::last_os_error()));
    }
    Ok(sent as usize)
}

/// Create a pipe with O_CLOEXEC. Returns (read_fd, write_fd).
pub fn create_pipe() -> Result<(RawFd, RawFd), String> {
    let mut fds = [0i32; 2];
    if unsafe { libc::pipe2(fds.as_mut_ptr(), libc::O_CLOEXEC) } < 0 {
        return Err(format!("pipe2: {}", std::io::Error::last_os_error()));
    }
    Ok((fds[0], fds[1]))
}

/// Query how many bytes are pending in a pipe via FIONREAD ioctl.
pub fn pipe_pending_bytes(fd: RawFd) -> i32 {
    let mut pending: libc::c_int = 0;
    unsafe { libc::ioctl(fd, libc::FIONREAD, &mut pending); }
    pending
}

pub const SPLICE_F_MOVE: u32 = 1;
pub const SPLICE_F_NONBLOCK: u32 = 2;
pub const SPLICE_LEN: usize = 32 * 1024;
