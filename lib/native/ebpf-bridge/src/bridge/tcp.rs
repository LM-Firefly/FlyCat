// TCP transparent proxy bridge — epoll-driven SOCKS5 CONNECT with splice zero-copy.
//
// Mirrors the C++ bridge/tcp_bridge.cpp.

use std::collections::HashMap;
use std::os::unix::io::RawFd;
use std::sync::atomic::AtomicBool;
use crate::bpf::cgroup::{CgroupRuntime, OriginalDestination};
use crate::bridge::socks5;
use crate::util::*;

const MAX_TCP_SESSIONS: usize = 1024;
const SESSION_IDLE_MS: u64 = 120_000;

#[derive(Clone, Copy, PartialEq, Eq)]
enum TcpState {
    ConnectingProxy,
    SendGreeting,
    WaitGreeting,
    SendConnect,
    WaitConnect,
    Relay,
}

struct TcpDirection {
    fd: RawFd,
    pipe_read: RawFd,
    pipe_write: RawFd,
    use_splice: bool,
    pipe_pending: bool,
    source_eof: bool,
    buf: Vec<u8>,
    buf_len: usize,
    buf_offset: usize,
    pending_send: Vec<u8>,
    pending_send_off: usize,
}

impl TcpDirection {
    fn new(fd: RawFd) -> Self {
        let (pipe_read, pipe_write) = match create_pipe() {
            Ok((r, w)) => (r, w),
            Err(_) => (-1, -1),
        };
        Self {
            fd,
            pipe_read,
            pipe_write,
            use_splice: pipe_read >= 0,
            pipe_pending: false,
            source_eof: false,
            buf: Vec::new(),
            buf_len: 0,
            buf_offset: 0,
            pending_send: Vec::new(),
            pending_send_off: 0,
        }
    }

    fn close(&mut self) {
        close_fd(self.fd);
        self.fd = -1;
        self.close_pipe();
        self.pending_send.clear();
        self.pending_send_off = 0;
    }

    fn close_pipe(&mut self) {
        close_fd(self.pipe_read);
        close_fd(self.pipe_write);
        self.pipe_read = -1;
        self.pipe_write = -1;
        self.pipe_pending = false;
    }

    fn ensure_buffer(&mut self) -> bool {
        if self.buf.capacity() >= RELAY_BUFFER_SIZE {
            return true;
        }
        self.buf = vec![0u8; RELAY_BUFFER_SIZE];
        self.buf_len = 0;
        self.buf_offset = 0;
        true
    }

    fn pending(&self) -> bool {
        if self.use_splice {
            self.pipe_pending
        } else {
            self.buf_len != 0
        }
    }

    fn disable_splice(&mut self) {
        if self.pipe_pending {
            return;
        }
        if !self.ensure_buffer() {
            return;
        }
        self.use_splice = false;
        self.close_pipe();
    }
}

impl Drop for TcpDirection {
    fn drop(&mut self) {
        self.close();
    }
}

struct TcpSession {
    state: TcpState,
    client: TcpDirection,
    proxy: TcpDirection,
    dest: Option<OriginalDestination>,
    last_active: u64,
    send_buf: Vec<u8>,
    send_len: usize,
    send_off: usize,
    client_eof: bool,
    proxy_eof: bool,
    client_shutdown_sent: bool,
    proxy_shutdown_sent: bool,
    recv_buf: [u8; 512],
    recv_len: usize,
    recv_need: usize,
}

pub struct TcpBridgeConfig {
    pub listen_port: u16,
    pub proxy_ip: [u8; 4],
    pub proxy_port: u16,
}

pub struct TcpBridge {
    config: TcpBridgeConfig,
    listener_fd: RawFd,
    epoll_fd: RawFd,
    sessions: HashMap<RawFd, usize>, // fd → session index
    session_list: Vec<TcpSession>,
    stop_flag: *const std::sync::atomic::AtomicBool,
    cgroup: *const CgroupRuntime,
}

// Safety: TcpBridge is only accessed from the main thread
unsafe impl Send for TcpBridge {}

impl TcpBridge {
    pub fn open(config: TcpBridgeConfig, stop: &AtomicBool, cgroup: &CgroupRuntime) -> Result<Self, String> {
        let listener_fd = create_tcp_listener(config.listen_port)?;
        let epoll_fd = unsafe { libc::epoll_create1(libc::EPOLL_CLOEXEC) };
        if epoll_fd < 0 {
            close_fd(listener_fd);
            return Err(format!("epoll_create1: {}", std::io::Error::last_os_error()));
        }
        add_epoll(epoll_fd, listener_fd, libc::EPOLLIN as u32, listener_fd as u64)?;
        Ok(Self {
            config,
            listener_fd,
            epoll_fd,
            sessions: HashMap::new(),
            session_list: Vec::with_capacity(MAX_TCP_SESSIONS),
            stop_flag: stop as *const AtomicBool,
            cgroup: cgroup as *const CgroupRuntime,
        })
    }

    pub fn listener_port(&self) -> u16 {
        let mut addr: libc::sockaddr_in = unsafe { std::mem::zeroed() };
        let mut addrlen: libc::socklen_t = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
        unsafe { libc::getsockname(self.listener_fd, &mut addr as *mut _ as *mut libc::sockaddr, &mut addrlen); }
        u16::from_be(addr.sin_port)
    }

    pub fn run(&mut self) -> Result<(), String> {
        let mut events = vec![libc::epoll_event { events: 0, u64: 0 }; 64];
        loop {
            if unsafe { (*self.stop_flag).load(std::sync::atomic::Ordering::Relaxed) } {
                break;
            }
            let nfds = unsafe { libc::epoll_wait(self.epoll_fd, events.as_mut_ptr(), events.len() as i32, 1000) };
            if nfds < 0 {
                let err = std::io::Error::last_os_error();
                if err.raw_os_error() == Some(libc::EINTR) {
                    continue;
                }
                return Err(format!("epoll_wait: {}", err));
            }
            let now = monotonic_ms();
            for i in 0..nfds as usize {
                let ev = events[i];
                let fd = ev.u64 as RawFd;
                if fd == self.listener_fd {
                    self.accept_connections(now)?;
                } else if let Some(&sid) = self.sessions.get(&fd) {
                    if ev.events & (libc::EPOLLERR | libc::EPOLLHUP) as u32 != 0 {
                        self.remove_session(sid);
                    } else {
                        self.handle_session(sid, ev.events, fd, now)?;
                    }
                }
            }
            self.cleanup_idle(now);
        }
        Ok(())
    }

    fn accept_connections(&mut self, now: u64) -> Result<(), String> {
        loop {
            let client_fd = unsafe { libc::accept4(self.listener_fd, std::ptr::null_mut(), std::ptr::null_mut(), libc::SOCK_NONBLOCK | libc::SOCK_CLOEXEC) };
            if client_fd < 0 {
                let err = std::io::Error::last_os_error();
                if err.raw_os_error() == Some(libc::EAGAIN) || err.raw_os_error() == Some(libc::EWOULDBLOCK) {
                    break;
                }
                return Err(format!("accept4: {}", err));
            }

            // Extract token from getsockname (the eBPF-rewritten destination)
            let token = self.extract_token(client_fd);
            let token_bytes = token.to_ne_bytes();
            let dest = unsafe { &*self.cgroup }.take_tcp_destination(self.config.listen_port, &token_bytes);

            // Connect to proxy
            let proxy_fd = match connect_proxy(self.config.proxy_ip, self.config.proxy_port) {
                Ok(fd) => fd,
                Err(e) => {
                    eprintln!("eBPF bridge: connect proxy failed: {}", e);
                    close_fd(client_fd);
                    continue;
                }
            };

            if self.session_list.len() >= MAX_TCP_SESSIONS {
                close_fd(client_fd);
                close_fd(proxy_fd);
                continue;
            }

            let sid = self.session_list.len();
            let mut session = TcpSession {
                state: TcpState::ConnectingProxy,
                client: TcpDirection::new(client_fd),
                proxy: TcpDirection::new(proxy_fd),
                dest,
                last_active: now,
                send_buf: vec![0u8; 256],
                send_len: 0,
                send_off: 0,
                client_eof: false,
                proxy_eof: false,
                client_shutdown_sent: false,
                proxy_shutdown_sent: false,
                recv_buf: [0u8; 512],
                recv_len: 0,
                recv_need: 0,
            };

            // Prepare SOCKS5 greeting (state remains ConnectingProxy until EPOLLOUT confirms)
            session.send_len = socks5::build_no_auth_greeting(&mut session.send_buf);
            session.send_off = 0;
            session.recv_need = 2;

            add_epoll(self.epoll_fd, client_fd, libc::EPOLLIN as u32 | libc::EPOLLRDHUP as u32, client_fd as u64)?;
            add_epoll(self.epoll_fd, proxy_fd, libc::EPOLLOUT as u32 | libc::EPOLLRDHUP as u32, proxy_fd as u64)?;

            self.sessions.insert(client_fd, sid);
            self.sessions.insert(proxy_fd, sid);
            self.session_list.push(session);
        }
        Ok(())
    }

    fn extract_token(&self, fd: RawFd) -> u32 {
        let mut addr: libc::sockaddr_in = unsafe { std::mem::zeroed() };
        let mut addrlen: libc::socklen_t = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
        unsafe { libc::getsockname(fd, &mut addr as *mut _ as *mut libc::sockaddr, &mut addrlen); }
        u32::from_be(addr.sin_addr.s_addr)
    }

    /// Compute and apply the correct epoll mask based on session state.
    fn update_events(epoll_fd: RawFd, fd: RawFd, state: TcpState, has_pending: bool, eof: bool) -> Result<(), String> {
        if fd < 0 {
            return Ok(());
        }
        let mut events: u32 = libc::EPOLLRDHUP as u32;
        match state {
            TcpState::ConnectingProxy | TcpState::SendGreeting | TcpState::SendConnect => {
                events |= libc::EPOLLOUT as u32;
            }
            TcpState::WaitGreeting | TcpState::WaitConnect => {
                events |= libc::EPOLLIN as u32;
            }
            TcpState::Relay => {
                if !eof {
                    events |= libc::EPOLLIN as u32;
                }
                if has_pending {
                    events |= libc::EPOLLOUT as u32;
                }
            }
        }
        modify_epoll(epoll_fd, fd, events, fd as u64)
    }

    fn handle_session(&mut self, sid: usize, events: u32, fd: RawFd, now: u64) -> Result<(), String> {
        if sid >= self.session_list.len() {
            return Ok(());
        }
        let session = &mut self.session_list[sid];
        session.last_active = now;

        match session.state {
            TcpState::ConnectingProxy => {
                // T1: Verify TCP connect() completion via SO_ERROR
                if events & libc::EPOLLOUT as u32 != 0 {
                    let mut err: i32 = 0;
                    let mut err_len = std::mem::size_of::<i32>() as libc::socklen_t;
                    let ret = unsafe {
                        libc::getsockopt(
                            self.session_list[sid].proxy.fd,
                            libc::SOL_SOCKET,
                            libc::SO_ERROR,
                            &mut err as *mut _ as *mut libc::c_void,
                            &mut err_len,
                        )
                    };
                    if ret < 0 || err != 0 {
                        self.remove_session(sid);
                        return Ok(());
                    }
                    self.session_list[sid].state = TcpState::SendGreeting;
                }
            }
            TcpState::SendGreeting => {
                if events & libc::EPOLLOUT as u32 != 0 {
                    self.send_pending(sid, TcpState::WaitGreeting)?;
                }
            }
            TcpState::WaitGreeting => {
                if events & libc::EPOLLIN as u32 != 0 {
                    // Accumulate greeting response
                    let recv_len = self.session_list[sid].recv_len;
                    let proxy_fd = self.session_list[sid].proxy.fd;
                    let space = self.session_list[sid].recv_buf.len().saturating_sub(recv_len);
                    if space > 0 {
                        let n = unsafe {
                            libc::read(
                                proxy_fd,
                                self.session_list[sid].recv_buf[recv_len..].as_mut_ptr() as *mut libc::c_void,
                                space,
                            )
                        };
                        if n > 0 {
                            self.session_list[sid].recv_len += n as usize;
                        } else if n == 0 {
                            self.remove_session(sid);
                            return Ok(());
                        }
                    }
                    if self.session_list[sid].recv_len >= self.session_list[sid].recv_need {
                        // Validate SOCKS5 greeting response: VER=0x05, METHOD=0x00 (no auth)
                        if self.session_list[sid].recv_buf[0] != 0x05 || self.session_list[sid].recv_buf[1] != 0x00 {
                            self.remove_session(sid);
                            return Ok(());
                        }
                        // Build CONNECT request using actual original destination
                        // T2: Remove session if no original destination is available
                        if self.session_list[sid].dest.is_none() {
                            self.remove_session(sid);
                            return Ok(());
                        }
                        let session = &mut self.session_list[sid];
                        let dest = session.dest.as_ref().unwrap();
                        let ep = if dest.family == libc::AF_INET6 as u8 {
                            socks5::Socks5Endpoint::from_ipv6(dest.addr, u16::from_be(dest.port))
                        } else {
                            socks5::Socks5Endpoint::from_ipv4(
                                dest.addr[..4].try_into().unwrap(),
                                u16::from_be(dest.port),
                            )
                        };
                        session.send_len = socks5::build_connect_request(&mut session.send_buf, &ep);
                        session.recv_len = 0;
                        session.recv_need = 4;
                        session.send_off = 0;
                        session.state = TcpState::SendConnect;
                        Self::update_events(self.epoll_fd, session.proxy.fd, TcpState::SendConnect, false, false)?;
                    }
                }
            }
            TcpState::SendConnect => {
                if events & libc::EPOLLOUT as u32 != 0 {
                    self.send_pending(sid, TcpState::WaitConnect)?;
                }
            }
            TcpState::WaitConnect => {
                if events & libc::EPOLLIN as u32 != 0 {
                    // Accumulate connect response
                    let recv_len = self.session_list[sid].recv_len;
                    let proxy_fd = self.session_list[sid].proxy.fd;
                    let space = self.session_list[sid].recv_buf.len().saturating_sub(recv_len);
                    if space > 0 {
                        let n = unsafe {
                            libc::recv(
                                proxy_fd,
                                self.session_list[sid].recv_buf[recv_len..].as_mut_ptr() as *mut libc::c_void,
                                space,
                                libc::MSG_NOSIGNAL,
                            )
                        };
                        if n > 0 {
                            self.session_list[sid].recv_len += n as usize;
                        } else if n == 0 {
                            self.remove_session(sid);
                            return Ok(());
                        }
                    }
                    let recv_len = self.session_list[sid].recv_len;
                    if recv_len >= 4 {
                        // Validate VER, REP, RSV
                        if self.session_list[sid].recv_buf[0] != 0x05
                            || self.session_list[sid].recv_buf[1] != 0x00
                            || self.session_list[sid].recv_buf[2] != 0x00
                        {
                            self.remove_session(sid);
                            return Ok(());
                        }
                        // Determine required length by ATYP
                        let need = match self.session_list[sid].recv_buf[3] {
                            0x01 => 10usize,
                            0x04 => 22usize,
                            0x03 => {
                                if recv_len < 5 {
                                    self.session_list[sid].recv_need = 5;
                                    return Ok(());
                                }
                                7 + self.session_list[sid].recv_buf[4] as usize
                            }
                            _ => {
                                self.remove_session(sid);
                                return Ok(());
                            }
                        };
                        if recv_len >= need {
                            // Complete response received, enter Relay
                            let session = &mut self.session_list[sid];
                            session.recv_len = 0;
                            session.recv_need = 0;
                            session.state = TcpState::Relay;
                            // Enable reading from both sides
                            Self::update_events(self.epoll_fd, session.client.fd, TcpState::Relay, false, false)?;
                            Self::update_events(self.epoll_fd, session.proxy.fd, TcpState::Relay, false, false)?;
                        } else {
                            self.session_list[sid].recv_need = need;
                        }
                    }
                }
            }
            TcpState::Relay => {
                // Route all events through relay_data which dispatches to splice or buffered paths per-direction.
                if events & (libc::EPOLLIN as u32 | libc::EPOLLOUT as u32 | libc::EPOLLRDHUP as u32) != 0 {
                    self.relay_data(sid, fd)?;
                }
                // Check for full EOF on both sides
                let (c_eof, p_eof) = {
                    let s = &self.session_list[sid];
                    (s.client.source_eof, s.proxy.source_eof)
                };
                if c_eof && p_eof {
                    self.remove_session(sid);
                }
            }
        }
        Ok(())
    }

    fn send_pending(&mut self, sid: usize, next: TcpState) -> Result<(), String> {
        let session = &mut self.session_list[sid];
        let fd = if next == TcpState::WaitGreeting || next == TcpState::WaitConnect {
            session.proxy.fd
        } else {
            session.client.fd
        };
        while session.send_off < session.send_len {
            let n = unsafe {
                libc::send(fd, session.send_buf[session.send_off..].as_ptr() as *const libc::c_void, session.send_len - session.send_off, libc::MSG_NOSIGNAL)
            };
            if n < 0 {
                let err = std::io::Error::last_os_error();
                if err.raw_os_error() == Some(libc::EAGAIN) {
                    return Ok(());
                }
                self.remove_session(sid);
                return Ok(());
            }
            session.send_off += n as usize;
        }
        session.state = next;
        // Switch to waiting for response
        Self::update_events(self.epoll_fd, fd, next, false, false)?;
        Ok(())
    }

    fn relay_data(&mut self, sid: usize, from_fd: RawFd) -> Result<(), String> {
        let is_client = from_fd == self.session_list[sid].client.fd;
        // Process both directions on every call (mirrors C++ handleDirection for both)
        self.relay_direction(sid, true, from_fd)?;
        if self.session_list[sid].client.fd < 0 || self.session_list[sid].proxy.fd < 0 {
            return Ok(());
        }
        if !is_client {
            // Only process second direction if the triggering fd wasn't the client (to avoid double-processing when called from EPOLLIN on proxy)
            self.relay_direction(sid, false, from_fd)?;
        }
        // Update epoll events for both directions
        self.update_relay_events(sid)
    }

    /// Process relay for one direction. `is_c2p` = client→proxy, else proxy→client.
    fn relay_direction(&mut self, sid: usize, is_c2p: bool, from_fd: RawFd) -> Result<(), String> {
        // Determine source and destination for this direction
        let (src_fd, dst_fd) = if is_c2p {
            (self.session_list[sid].client.fd, self.session_list[sid].proxy.fd)
        } else {
            (self.session_list[sid].proxy.fd, self.session_list[sid].client.fd)
        };
        if src_fd < 0 || dst_fd < 0 {
            return Ok(());
        }

        let events = if from_fd == src_fd {
            libc::EPOLLIN as u32 | libc::EPOLLRDHUP as u32 | libc::EPOLLHUP as u32
        } else {
            libc::EPOLLOUT as u32
        };

        let use_splice = if is_c2p {
            self.session_list[sid].client.use_splice
        } else {
            self.session_list[sid].proxy.use_splice
        };

        if use_splice {
            self.handle_splice(sid, is_c2p, src_fd, dst_fd, events)?;
        } else {
            self.handle_buffered(sid, is_c2p, src_fd, dst_fd, events)?;
        }
        Ok(())
    }

    fn handle_splice(&mut self, sid: usize, is_c2p: bool, src_fd: RawFd, dst_fd: RawFd, events: u32) -> Result<(), String> {
        // Get direction state
        let (pipe_read, pipe_write, pipe_pending) = {
            let dir = if is_c2p { &self.session_list[sid].client } else { &self.session_list[sid].proxy };
            (dir.pipe_read, dir.pipe_write, dir.pipe_pending)
        };

        // If pipe has pending data and destination is writable, drain first
        if pipe_pending && (events & libc::EPOLLOUT as u32) != 0 {
            if !self.drain_splice(sid, is_c2p, pipe_read, dst_fd)? {
                return Ok(());
            }
        }

        // Re-read state after drain
        let (pipe_pending, source_eof) = {
            let dir = if is_c2p { &self.session_list[sid].client } else { &self.session_list[sid].proxy };
            (dir.pipe_pending, dir.source_eof)
        };

        // If no pending data, not eof, and source has input: splice from source into pipe
        if !pipe_pending && !source_eof && (events & (libc::EPOLLIN as u32 | libc::EPOLLRDHUP as u32 | libc::EPOLLHUP as u32)) != 0 {
            let count = unsafe {
                libc::splice(src_fd, std::ptr::null_mut(), pipe_write, std::ptr::null_mut(),
                    SPLICE_LEN, SPLICE_F_MOVE | SPLICE_F_NONBLOCK)
            };
            if count > 0 {
                if is_c2p {
                    self.session_list[sid].client.pipe_pending = true;
                } else {
                    self.session_list[sid].proxy.pipe_pending = true;
                }
                // Re-read pipe_read after setting pipe_pending
                let pipe_read = if is_c2p { self.session_list[sid].client.pipe_read } else { self.session_list[sid].proxy.pipe_read };
                self.drain_splice(sid, is_c2p, pipe_read, dst_fd)?;
            } else if count == 0 {
                // Source EOF
                if is_c2p {
                    self.session_list[sid].client.source_eof = true;
                } else {
                    self.session_list[sid].proxy.source_eof = true;
                }
                self.shutdown_destination_if_done(sid, is_c2p)?;
            } else {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR || err == libc::EAGAIN || err == libc::EWOULDBLOCK {
                    return Ok(());
                }
                if err == libc::ENOSYS || err == libc::EINVAL || err == libc::ENOTSUP || err == libc::EOPNOTSUPP {
                    // splice not supported — fall back to buffered
                    self.disable_splice_and_fallback(sid, is_c2p)?;
                    self.handle_buffered(sid, is_c2p, src_fd, dst_fd, events)?;
                    return Ok(());
                }
                self.remove_session(sid);
            }
        }
        Ok(())
    }

    fn drain_splice(&mut self, sid: usize, is_c2p: bool, pipe_read: RawFd, dst_fd: RawFd) -> Result<bool, String> {
        loop {
            let pending = if is_c2p {
                self.session_list[sid].client.pipe_pending
            } else {
                self.session_list[sid].proxy.pipe_pending
            };
            if !pending {
                break;
            }
            let count = unsafe {
                libc::splice(pipe_read, std::ptr::null_mut(), dst_fd, std::ptr::null_mut(),
                    SPLICE_LEN, SPLICE_F_MOVE | SPLICE_F_NONBLOCK)
            };
            if count > 0 {
                continue;
            }
            if count < 0 {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR {
                    continue;
                }
                if err == libc::EAGAIN || err == libc::EWOULDBLOCK {
                    // Check if pipe is truly empty
                    let pending_bytes = pipe_pending_bytes(pipe_read);
                    if pending_bytes == 0 {
                        if is_c2p {
                            self.session_list[sid].client.pipe_pending = false;
                        } else {
                            self.session_list[sid].proxy.pipe_pending = false;
                        }
                        self.shutdown_destination_if_done(sid, is_c2p)?;
                        return Ok(true);
                    }
                    return Ok(true);
                }
                self.remove_session(sid);
                return Ok(false);
            }
        }
        self.shutdown_destination_if_done(sid, is_c2p)?;
        Ok(true)
    }

    fn shutdown_destination_if_done(&mut self, sid: usize, is_c2p: bool) -> Result<(), String> {
        let (source_eof, pending, dst_fd) = if is_c2p {
            let dir = &self.session_list[sid].client;
            (dir.source_eof, dir.pending(), self.session_list[sid].proxy.fd)
        } else {
            let dir = &self.session_list[sid].proxy;
            (dir.source_eof, dir.pending(), self.session_list[sid].client.fd)
        };
        if source_eof && !pending && dst_fd >= 0 {
            // Check if we haven't already sent shutdown for this direction
            let already_shutdown = if is_c2p {
                self.session_list[sid].proxy_shutdown_sent
            } else {
                self.session_list[sid].client_shutdown_sent
            };
            if !already_shutdown {
                unsafe { libc::shutdown(dst_fd, libc::SHUT_WR); }
                if is_c2p {
                    self.session_list[sid].proxy_shutdown_sent = true;
                } else {
                    self.session_list[sid].client_shutdown_sent = true;
                }
            }
        }
        Ok(())
    }

    fn disable_splice_and_fallback(&mut self, sid: usize, is_c2p: bool) -> Result<(), String> {
        let dir = if is_c2p {
            &mut self.session_list[sid].client
        } else {
            &mut self.session_list[sid].proxy
        };
        if dir.pipe_pending {
            self.remove_session(sid);
            return Ok(());
        }
        dir.disable_splice();
        Ok(())
    }

    fn handle_buffered(&mut self, sid: usize, is_c2p: bool, src_fd: RawFd, dst_fd: RawFd, events: u32) -> Result<(), String> {
        // Ensure buffer allocated
        {
            let dir = if is_c2p {
                &mut self.session_list[sid].client
            } else {
                &mut self.session_list[sid].proxy
            };
            if !dir.ensure_buffer() {
                self.remove_session(sid);
                return Ok(());
            }
        }

        // If destination is writable, flush buffered data first
        if events & libc::EPOLLOUT as u32 != 0 {
            if !self.flush_buffered(sid, is_c2p, dst_fd)? {
                return Ok(());
            }
        }

        // Re-read state
        let (buf_len, source_eof) = {
            let dir = if is_c2p {
                &self.session_list[sid].client
            } else {
                &self.session_list[sid].proxy
            };
            (dir.buf_len, dir.source_eof)
        };

        // If buffer empty and source has input, recv into buffer
        if buf_len == 0 && !source_eof && (events & (libc::EPOLLIN as u32 | libc::EPOLLRDHUP as u32 | libc::EPOLLHUP as u32)) != 0 {
            let n = {
                let dir = if is_c2p {
                    &mut self.session_list[sid].client
                } else {
                    &mut self.session_list[sid].proxy
                };
                let cap = dir.buf.capacity();
                unsafe { libc::recv(src_fd, dir.buf.as_mut_ptr() as *mut libc::c_void, cap, 0) }
            };
            if n > 0 {
                if is_c2p {
                    self.session_list[sid].client.buf_len = n as usize;
                    self.session_list[sid].client.buf_offset = 0;
                } else {
                    self.session_list[sid].proxy.buf_len = n as usize;
                    self.session_list[sid].proxy.buf_offset = 0;
                }
                if !self.flush_buffered(sid, is_c2p, dst_fd)? {
                    return Ok(());
                }
            } else if n == 0 {
                if is_c2p {
                    self.session_list[sid].client.source_eof = true;
                } else {
                    self.session_list[sid].proxy.source_eof = true;
                }
                self.shutdown_destination_if_done(sid, is_c2p)?;
            } else {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err != libc::EINTR && err != libc::EAGAIN && err != libc::EWOULDBLOCK {
                    self.remove_session(sid);
                    return Ok(());
                }
            }
        }
        Ok(())
    }

    fn flush_buffered(&mut self, sid: usize, is_c2p: bool, dst_fd: RawFd) -> Result<bool, String> {
        loop {
            let (offset, len) = if is_c2p {
                (self.session_list[sid].client.buf_offset, self.session_list[sid].client.buf_len)
            } else {
                (self.session_list[sid].proxy.buf_offset, self.session_list[sid].proxy.buf_len)
            };
            if len == 0 {
                break;
            }
            let buf = if is_c2p {
                &self.session_list[sid].client.buf
            } else {
                &self.session_list[sid].proxy.buf
            };
            let count = unsafe {
                libc::send(dst_fd, buf[offset..].as_ptr() as *const libc::c_void, len, libc::MSG_NOSIGNAL)
            };
            if count > 0 {
                let new_offset = offset + count as usize;
                let remaining = len - count as usize;
                if remaining == 0 {
                    if is_c2p {
                        self.session_list[sid].client.buf_offset = 0;
                        self.session_list[sid].client.buf_len = 0;
                    } else {
                        self.session_list[sid].proxy.buf_offset = 0;
                        self.session_list[sid].proxy.buf_len = 0;
                    }
                } else {
                    if is_c2p {
                        self.session_list[sid].client.buf_offset = new_offset;
                        self.session_list[sid].client.buf_len = remaining;
                    } else {
                        self.session_list[sid].proxy.buf_offset = new_offset;
                        self.session_list[sid].proxy.buf_len = remaining;
                    }
                }
                continue;
            }
            if count < 0 {
                let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
                if err == libc::EINTR {
                    continue;
                }
                if err == libc::EAGAIN || err == libc::EWOULDBLOCK {
                    return Ok(true);
                }
                self.remove_session(sid);
                return Ok(false);
            }
        }
        self.shutdown_destination_if_done(sid, is_c2p)?;
        Ok(true)
    }

    /// Update epoll registrations for both directions in relay state.
    fn update_relay_events(&mut self, sid: usize) -> Result<(), String> {
        let (client_fd, proxy_fd) = (self.session_list[sid].client.fd, self.session_list[sid].proxy.fd);

        // Compute events for client fd
        let client_events = {
            let c2p = &self.session_list[sid].client;
            let p2c = &self.session_list[sid].proxy;
            let mut ev: u32 = libc::EPOLLRDHUP as u32;
            // As source for c2p: read if not eof and no pending
            if !c2p.source_eof && !c2p.pending() {
                ev |= libc::EPOLLIN as u32;
            }
            // As destination for p2c: write if p2c has pending
            if p2c.pending() {
                ev |= libc::EPOLLOUT as u32;
            }
            ev
        };
        // Compute events for proxy fd
        let proxy_events = {
            let c2p = &self.session_list[sid].client;
            let p2c = &self.session_list[sid].proxy;
            let mut ev: u32 = libc::EPOLLRDHUP as u32;
            // As source for p2c: read if not eof and no pending
            if !p2c.source_eof && !p2c.pending() {
                ev |= libc::EPOLLIN as u32;
            }
            // As destination for c2p: write if c2p has pending
            if c2p.pending() {
                ev |= libc::EPOLLOUT as u32;
            }
            ev
        };

        if client_fd >= 0 {
            modify_epoll(self.epoll_fd, client_fd, client_events, client_fd as u64)?;
        }
        if proxy_fd >= 0 {
            modify_epoll(self.epoll_fd, proxy_fd, proxy_events, proxy_fd as u64)?;
        }
        Ok(())
    }

    fn remove_session(&mut self, sid: usize) {
        if sid >= self.session_list.len() {
            return;
        }
        let session = &mut self.session_list[sid];
        self.sessions.remove(&session.client.fd);
        self.sessions.remove(&session.proxy.fd);
        // Unregister from epoll before closing
        if session.client.fd >= 0 {
            unsafe { libc::epoll_ctl(self.epoll_fd, libc::EPOLL_CTL_DEL, session.client.fd, std::ptr::null_mut()); }
        }
        if session.proxy.fd >= 0 {
            unsafe { libc::epoll_ctl(self.epoll_fd, libc::EPOLL_CTL_DEL, session.proxy.fd, std::ptr::null_mut()); }
        }
        // Mark as invalid by setting state
        session.state = TcpState::ConnectingProxy;
        session.client.close();
        session.proxy.close();
    }

    fn cleanup_idle(&mut self, now: u64) {
        for sid in 0..self.session_list.len() {
            if self.session_list[sid].state != TcpState::ConnectingProxy
                && now.saturating_sub(self.session_list[sid].last_active) > SESSION_IDLE_MS
            {
                self.remove_session(sid);
            }
        }
    }
}

impl Drop for TcpBridge {
    fn drop(&mut self) {
        for sid in 0..self.session_list.len() {
            self.remove_session(sid);
        }
        close_fd(self.listener_fd);
        close_fd(self.epoll_fd);
    }
}
