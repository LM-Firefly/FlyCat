// UDP transparent proxy bridge — SOCKS5 UDP ASSOCIATE with recvmmsg/sendmmsg batch I/O.
//
// Mirrors the C++ bridge/udp_bridge.cpp.

use std::collections::HashMap;
use std::os::unix::io::RawFd;
use std::sync::atomic::AtomicBool;
use crate::bpf::cgroup::{CgroupRuntime, OriginalDestination};
use crate::bridge::socks5;
use crate::util::*;

const MAX_UDP_SESSIONS: usize = 256;
const UDP_SESSION_IDLE_MS: u64 = 30_000;
const MAX_PENDING_DATAGRAMS: usize = 16;
const MAX_BINDINGS: usize = 64;
const BATCH_SIZE: usize = 4;
const MAX_UDP_PAYLOAD: usize = 65507;

/// Pre-allocated batch storage for recvmmsg/sendmmsg.
/// Avoids per-packet heap allocations.
struct BatchStorage {
    // Receive
    receive_buffers: [[u8; MAX_UDP_PAYLOAD]; BATCH_SIZE],
    receive_addrs: [libc::sockaddr_in; BATCH_SIZE],
    receive_controls: [[u8; CONTROL_SIZE]; BATCH_SIZE],
    receive_iovs: [libc::iovec; BATCH_SIZE],
    receive_msgs: [libc::mmsghdr; BATCH_SIZE],
    // Response
    response_buffers: [[u8; MAX_UDP_PAYLOAD]; BATCH_SIZE],
    response_addrs: [libc::sockaddr_in; BATCH_SIZE],
    response_tokens: [[u8; 4]; BATCH_SIZE],
    response_iovs: [libc::iovec; BATCH_SIZE],
    response_controls: [[u8; CONTROL_SIZE]; BATCH_SIZE],
    response_msgs: [libc::mmsghdr; BATCH_SIZE],
    response_count: usize,
}

const CONTROL_SIZE: usize = 128; // Enough for CMSG_SPACE(sizeof(in_pktinfo))

impl BatchStorage {
    fn new() -> Self {
        Self {
            receive_buffers: [[0u8; MAX_UDP_PAYLOAD]; BATCH_SIZE],
            receive_addrs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            receive_controls: [[0u8; CONTROL_SIZE]; BATCH_SIZE],
            receive_iovs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            receive_msgs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            response_buffers: [[0u8; MAX_UDP_PAYLOAD]; BATCH_SIZE],
            response_addrs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            response_tokens: [[0u8; 4]; BATCH_SIZE],
            response_iovs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            response_controls: [[0u8; CONTROL_SIZE]; BATCH_SIZE],
            response_msgs: [unsafe { std::mem::zeroed() }; BATCH_SIZE],
            response_count: 0,
        }
    }

    /// Initialize receive message headers for recvmmsg.
    fn init_receive_headers(&mut self) {
        for i in 0..BATCH_SIZE {
            self.receive_addrs[i] = unsafe { std::mem::zeroed() };
            self.receive_controls[i].fill(0);
            self.receive_iovs[i] = libc::iovec {
                iov_base: self.receive_buffers[i].as_mut_ptr() as *mut libc::c_void,
                iov_len: MAX_UDP_PAYLOAD,
            };
            self.receive_msgs[i] = unsafe { std::mem::zeroed() };
            self.receive_msgs[i].msg_hdr.msg_name = &mut self.receive_addrs[i] as *mut _ as *mut libc::c_void;
            self.receive_msgs[i].msg_hdr.msg_namelen = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
            self.receive_msgs[i].msg_hdr.msg_iov = &mut self.receive_iovs[i];
            self.receive_msgs[i].msg_hdr.msg_iovlen = 1;
            self.receive_msgs[i].msg_hdr.msg_control = self.receive_controls[i].as_mut_ptr() as *mut libc::c_void;
            self.receive_msgs[i].msg_hdr.msg_controllen = CONTROL_SIZE;
        }
    }

    /// Queue a response for sendmmsg. Returns true if queued, false if batch full.
    fn queue_response(&mut self, client: &libc::sockaddr_in, token: &[u8; 4], payload: &[u8]) -> bool {
        if self.response_count >= BATCH_SIZE {
            return false;
        }
        let idx = self.response_count;
        self.response_addrs[idx] = *client;
        self.response_tokens[idx] = *token;
        let len = payload.len().min(MAX_UDP_PAYLOAD);
        self.response_buffers[idx][..len].copy_from_slice(&payload[..len]);
        self.response_iovs[idx] = libc::iovec {
            iov_base: self.response_buffers[idx].as_mut_ptr() as *mut libc::c_void,
            iov_len: len,
        };
        self.response_count += 1;
        true
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum UdpState {
    ConnectingProxy,
    SendGreeting,
    WaitGreeting,
    SendAssociate,
    WaitAssociate,
    Relay,
}

struct PendingDatagram {
    data: Vec<u8>,
    src_addr: libc::sockaddr_in,
    dest_token: u32,
    timestamp: u64,
}

struct CachedBinding {
    token: u32,
    destination: OriginalDestination,
    last_used: u64,
}

struct UdpSession {
    state: UdpState,
    control_fd: RawFd,    // TCP socket for SOCKS5 handshake
    relay_fd: RawFd,      // UDP socket for data relay
    last_active: u64,
    send_buf: Vec<u8>,
    send_len: usize,
    send_off: usize,
    socks_relay_addr: Option<libc::sockaddr_in>,
    client_addr: libc::sockaddr_in,
    pending_datagrams: Vec<PendingDatagram>,
    token: u32,
    recv_buf: [u8; 256],
    recv_len: usize,
    recv_need: usize,
    bindings: Vec<u32>,
}

pub struct UdpBridgeConfig {
    pub listen_port: u16,
    pub proxy_ip: [u8; 4],
    pub proxy_port: u16,
}

pub struct UdpBridge {
    config: UdpBridgeConfig,
    listener_fd: RawFd,
    sessions: Vec<UdpSession>,
    token_to_session: HashMap<u32, usize>,
    binding_cache: HashMap<u32, CachedBinding>,
    epoll_fd: RawFd,
    fd_to_session: HashMap<RawFd, usize>,
    stop_flag: *const std::sync::atomic::AtomicBool,
    cgroup: *const CgroupRuntime,
    batch: Option<Box<BatchStorage>>,
}

unsafe impl Send for UdpBridge {}

impl UdpBridge {
    pub fn open(config: UdpBridgeConfig, stop: &AtomicBool, cgroup: &CgroupRuntime) -> Result<Self, String> {
        let listener_fd = create_udp_socket(config.listen_port)?;
        Ok(Self {
            config,
            listener_fd,
            sessions: Vec::with_capacity(MAX_UDP_SESSIONS),
            token_to_session: HashMap::new(),
            binding_cache: HashMap::new(),
            epoll_fd: -1,
            fd_to_session: HashMap::new(),
            stop_flag: stop as *const AtomicBool,
            cgroup: cgroup as *const CgroupRuntime,
            batch: Some(Box::new(BatchStorage::new())),
        })
    }

    pub fn run(&mut self) -> Result<(), String> {
        self.epoll_fd = unsafe { libc::epoll_create1(libc::EPOLL_CLOEXEC) };
        if self.epoll_fd < 0 {
            return Err(format!("epoll_create1: {}", std::io::Error::last_os_error()));
        }
        self.fd_to_session.clear();
        add_epoll(self.epoll_fd, self.listener_fd, libc::EPOLLIN as u32, self.listener_fd as u64)?;

        let mut events = vec![libc::epoll_event { events: 0, u64: 0 }; 16];
        loop {
            if unsafe { (*self.stop_flag).load(std::sync::atomic::Ordering::Relaxed) } {
                break;
            }
            let nfds = unsafe { libc::epoll_wait(self.epoll_fd, events.as_mut_ptr(), events.len() as i32, 1000) };
            if nfds < 0 {
                let err = std::io::Error::last_os_error();
                if err.raw_os_error() == Some(libc::EINTR) { continue; }
                return Err(format!("epoll_wait: {}", err));
            }
            let now = monotonic_ms();
            for i in 0..nfds as usize {
                let ev = events[i];
                let fd = ev.u64 as RawFd;
                if fd == self.listener_fd && ev.events & libc::EPOLLIN as u32 != 0 {
                    self.process_incoming_batch(now)?;
                } else if let Some(&sid) = self.fd_to_session.get(&fd) {
                    if ev.events & libc::EPOLLIN as u32 != 0 {
                        self.handle_relay_response(sid, now);
                    }
                }
            }
            // Poll control sockets for handshake state progression
            for sid in 0..self.sessions.len() {
                match self.sessions[sid].state {
                    UdpState::SendGreeting => {
                        let n = unsafe {
                            libc::send(self.sessions[sid].control_fd, self.sessions[sid].send_buf[self.sessions[sid].send_off..].as_ptr() as *const libc::c_void, self.sessions[sid].send_len - self.sessions[sid].send_off, libc::MSG_NOSIGNAL)
                        };
                        if n > 0 {
                            self.sessions[sid].state = UdpState::WaitGreeting;
                        }
                    }
                    UdpState::SendAssociate => {
                        let n = unsafe {
                            libc::send(self.sessions[sid].control_fd, self.sessions[sid].send_buf[self.sessions[sid].send_off..].as_ptr() as *const libc::c_void, self.sessions[sid].send_len - self.sessions[sid].send_off, libc::MSG_NOSIGNAL)
                        };
                        if n > 0 {
                            self.sessions[sid].state = UdpState::WaitAssociate;
                        }
                    }
                    UdpState::WaitGreeting | UdpState::WaitAssociate => {
                        self.check_proxy_response(sid, now)?;
                    }
                    _ => {}
                }
            }
            self.flush_responses();
            self.cleanup_idle(now);
        }
        close_fd(self.epoll_fd);
        self.epoll_fd = -1;
        Ok(())
    }

    fn process_incoming(&mut self, now: u64) -> Result<(), String> {
        let mut buf = vec![0u8; 65536];
        let mut iov = libc::iovec {
            iov_base: buf.as_mut_ptr() as *mut libc::c_void,
            iov_len: buf.len(),
        };
        let cmsg_space = unsafe { libc::CMSG_SPACE(std::mem::size_of::<libc::in_pktinfo>() as u32) } as usize;
        let mut cmsg_buf = vec![0u8; cmsg_space];
        let mut src_addr: libc::sockaddr_in = unsafe { std::mem::zeroed() };

        let mut msg: libc::msghdr = unsafe { std::mem::zeroed() };
        msg.msg_name = &mut src_addr as *mut _ as *mut libc::c_void;
        msg.msg_namelen = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
        msg.msg_iov = &mut iov;
        msg.msg_iovlen = 1;
        msg.msg_control = cmsg_buf.as_mut_ptr() as *mut libc::c_void;
        msg.msg_controllen = cmsg_buf.len();

        let n = unsafe { libc::recvmsg(self.listener_fd, &mut msg, 0) };
        if n <= 0 {
            return Ok(());
        }

        // Extract token from IP_PKTINFO (eBPF-rewritten destination)
        let mut token: u32 = 0;
        let mut cmsg = unsafe { libc::CMSG_FIRSTHDR(&msg) };
        while !cmsg.is_null() {
            unsafe {
                if (*cmsg).cmsg_level == libc::IPPROTO_IP && (*cmsg).cmsg_type == libc::IP_PKTINFO {
                    let pktinfo = libc::CMSG_DATA(cmsg) as *const libc::in_pktinfo;
                    token = u32::from_be((*pktinfo).ipi_spec_dst.s_addr);
                    break;
                }
                cmsg = libc::CMSG_NXTHDR(&msg, cmsg);
            }
        }

        let _token_bytes = token.to_ne_bytes();
        let sid = self.find_or_create_session(token, src_addr, now)?;
        if sid >= self.sessions.len() {
            return Ok(());
        }

        let session = &self.sessions[sid];
        let session_state = session.state;
        let relay_addr = session.socks_relay_addr;
        let relay_fd = session.relay_fd;

        match session_state {
            UdpState::Relay => {
                if let Some(relay_addr) = relay_addr {
                    // Forward data to proxy via SOCKS5 UDP datagram
                    let orig = self.resolve_destination(token, now);
                    if let Some(dest) = orig {
                        let ep = if dest.family == libc::AF_INET6 as u8 {
                            socks5::Socks5Endpoint::from_ipv6(dest.addr, u16::from_be(dest.port))
                        } else {
                            socks5::Socks5Endpoint::from_ipv4(
                                dest.addr[..4].try_into().unwrap(),
                                u16::from_be(dest.port),
                            )
                        };
                        let mut header = [0u8; 32];
                        let header_len = socks5::build_udp_datagram_header(&mut header, &ep);
                        let mut send_buf = Vec::with_capacity(header_len + n as usize);
                        send_buf.extend_from_slice(&header[..header_len]);
                        send_buf.extend_from_slice(&buf[..n as usize]);

                        unsafe {
                            libc::sendto(
                                relay_fd,
                                send_buf.as_ptr() as *const libc::c_void,
                                send_buf.len(),
                                0,
                                &relay_addr as *const _ as *const libc::sockaddr,
                                std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t,
                            );
                        }
                    }
                } else if self.sessions[sid].pending_datagrams.len() < MAX_PENDING_DATAGRAMS {
                    // Relay but no relay_addr yet — buffer
                    self.sessions[sid].pending_datagrams.push(PendingDatagram {
                        data: buf[..n as usize].to_vec(),
                        src_addr,
                        dest_token: token,
                        timestamp: now,
                    });
                }
            }
            _ => {
                // Still setting up SOCKS5 connection, buffer the datagram
                if self.sessions[sid].pending_datagrams.len() < MAX_PENDING_DATAGRAMS {
                    self.sessions[sid].pending_datagrams.push(PendingDatagram {
                        data: buf[..n as usize].to_vec(),
                        src_addr,
                        dest_token: token,
                        timestamp: now,
                    });
                }
            }
        }
        Ok(())
    }

    /// Batch receive via recvmmsg, falling back to single recvmsg if unsupported.
    fn process_incoming_batch(&mut self, now: u64) -> Result<(), String> {
        let has_batch = self.batch.is_some();
        if !has_batch {
            return self.process_incoming(now);
        }

        // Initialize receive headers
        {
            let batch = self.batch.as_mut().unwrap();
            batch.init_receive_headers();
        }

        let count = unsafe {
            libc::recvmmsg(
                self.listener_fd,
                self.batch.as_mut().unwrap().receive_msgs.as_mut_ptr(),
                BATCH_SIZE as u32,
                libc::MSG_DONTWAIT,
                std::ptr::null_mut(),
            )
        };

        if count < 0 {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EAGAIN || err == libc::EWOULDBLOCK || err == libc::EINTR {
                return Ok(());
            }
            if err == libc::ENOSYS || err == libc::EINVAL {
                return self.process_incoming(now);
            }
            return Ok(());
        }

        // Collect parsed datagrams to avoid borrow conflict with self.batch
        struct ParsedDatagram {
            token: u32,
            src_addr: libc::sockaddr_in,
            len: usize,
            index: usize,
        }
        let mut datagrams = Vec::with_capacity(count as usize);

        for i in 0..count as usize {
            let msg_len = self.batch.as_ref().unwrap().receive_msgs[i].msg_len as usize;
            if msg_len == 0 {
                continue;
            }
            let src_addr = self.batch.as_ref().unwrap().receive_addrs[i];

            // Extract token from IP_PKTINFO
            let mut token: u32 = 0;
            let msg = &self.batch.as_ref().unwrap().receive_msgs[i].msg_hdr;
            let mut cmsg = unsafe { libc::CMSG_FIRSTHDR(msg as *const libc::msghdr) };
            while !cmsg.is_null() {
                unsafe {
                    if (*cmsg).cmsg_level == libc::IPPROTO_IP && (*cmsg).cmsg_type == libc::IP_PKTINFO {
                        let pktinfo = libc::CMSG_DATA(cmsg) as *const libc::in_pktinfo;
                        token = u32::from_be((*pktinfo).ipi_spec_dst.s_addr);
                        break;
                    }
                    cmsg = libc::CMSG_NXTHDR(msg as *const libc::msghdr, cmsg);
                }
            }

            datagrams.push(ParsedDatagram { token, src_addr, len: msg_len, index: i });
        }

        // Now process each datagram — batch borrow is released
        for dg in &datagrams {
            let buf = &self.batch.as_ref().unwrap().receive_buffers[dg.index][..dg.len];
            let buf_copy: Vec<u8> = buf.to_vec();
            self.process_single_datagram(dg.token, dg.src_addr, &buf_copy, now)?;
        }
        Ok(())
    }

    /// Process a single received datagram (shared logic for batch and single-recv paths).
    fn process_single_datagram(&mut self, token: u32, src_addr: libc::sockaddr_in, buf: &[u8], now: u64) -> Result<(), String> {
        let sid = self.find_or_create_session(token, src_addr, now)?;
        if sid >= self.sessions.len() {
            return Ok(());
        }

        let session_state = self.sessions[sid].state;
        let relay_addr = self.sessions[sid].socks_relay_addr;
        let relay_fd = self.sessions[sid].relay_fd;

        match session_state {
            UdpState::Relay => {
                if let Some(relay_addr) = relay_addr {
                    let orig = self.resolve_destination(token, now);
                    if let Some(dest) = orig {
                        let ep = if dest.family == libc::AF_INET6 as u8 {
                            socks5::Socks5Endpoint::from_ipv6(dest.addr, u16::from_be(dest.port))
                        } else {
                            socks5::Socks5Endpoint::from_ipv4(
                                dest.addr[..4].try_into().unwrap(),
                                u16::from_be(dest.port),
                            )
                        };
                        let mut header = [0u8; 32];
                        let header_len = socks5::build_udp_datagram_header(&mut header, &ep);
                        let mut send_buf = Vec::with_capacity(header_len + buf.len());
                        send_buf.extend_from_slice(&header[..header_len]);
                        send_buf.extend_from_slice(buf);

                        unsafe {
                            libc::sendto(
                                relay_fd,
                                send_buf.as_ptr() as *const libc::c_void,
                                send_buf.len(),
                                0,
                                &relay_addr as *const _ as *const libc::sockaddr,
                                std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t,
                            );
                        }
                    }
                } else if self.sessions[sid].pending_datagrams.len() < MAX_PENDING_DATAGRAMS {
                    self.sessions[sid].pending_datagrams.push(PendingDatagram {
                        data: buf.to_vec(),
                        src_addr,
                        dest_token: token,
                        timestamp: now,
                    });
                }
            }
            _ => {
                if self.sessions[sid].pending_datagrams.len() < MAX_PENDING_DATAGRAMS {
                    self.sessions[sid].pending_datagrams.push(PendingDatagram {
                        data: buf.to_vec(),
                        src_addr,
                        dest_token: token,
                        timestamp: now,
                    });
                }
            }
        }
        Ok(())
    }

    /// Queue a response to the client into the batch buffer.
    /// If the batch is full, flush first.
    fn queue_response_to_client(&mut self, client: &libc::sockaddr_in, token: &[u8; 4], payload: &[u8]) {
        if let Some(batch) = self.batch.as_mut() {
            if batch.response_count >= BATCH_SIZE {
                // Flush before borrowing batch again
            } else {
                batch.queue_response(client, token, payload);
                return;
            }
        }
        self.flush_responses();
        if let Some(batch) = self.batch.as_mut() {
            batch.queue_response(client, token, payload);
        }
    }

    /// Flush batched responses via sendmmsg, falling back to single sendmsg.
    fn flush_responses(&mut self) {
        let batch = match self.batch.as_mut() {
            Some(b) => b,
            None => return,
        };
        if batch.response_count == 0 || self.listener_fd < 0 {
            return;
        }

        // Build mmsghdr for each response
        for i in 0..batch.response_count {
            batch.response_controls[i].fill(0);
            batch.response_msgs[i] = unsafe { std::mem::zeroed() };
            batch.response_msgs[i].msg_hdr.msg_name = &mut batch.response_addrs[i] as *mut _ as *mut libc::c_void;
            batch.response_msgs[i].msg_hdr.msg_namelen = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
            batch.response_msgs[i].msg_hdr.msg_iov = &mut batch.response_iovs[i];
            batch.response_msgs[i].msg_hdr.msg_iovlen = 1;
            batch.response_msgs[i].msg_hdr.msg_control = batch.response_controls[i].as_mut_ptr() as *mut libc::c_void;
            batch.response_msgs[i].msg_hdr.msg_controllen = CONTROL_SIZE;

            // Set IP_PKTINFO cmsg to route response back to client
            unsafe {
                let header = libc::CMSG_FIRSTHDR(&batch.response_msgs[i].msg_hdr);
                if !header.is_null() {
                    (*header).cmsg_level = libc::IPPROTO_IP;
                    (*header).cmsg_type = libc::IP_PKTINFO;
                    (*header).cmsg_len = libc::CMSG_LEN(std::mem::size_of::<libc::in_pktinfo>() as u32) as usize;
                    let pktinfo = libc::CMSG_DATA(header) as *mut libc::in_pktinfo;
                    (*pktinfo).ipi_ifindex = 0;
                    std::ptr::copy_nonoverlapping(
                        batch.response_tokens[i].as_ptr(),
                        &mut (*pktinfo).ipi_spec_dst.s_addr as *mut _ as *mut u8,
                        4,
                    );
                }
            }
        }

        let sent = unsafe {
            libc::sendmmsg(
                self.listener_fd,
                batch.response_msgs.as_mut_ptr(),
                batch.response_count as u32,
                libc::MSG_DONTWAIT | libc::MSG_NOSIGNAL,
            )
        };

        if sent < 0 {
            let err = std::io::Error::last_os_error().raw_os_error().unwrap_or(0);
            if err == libc::EINTR || err == libc::EAGAIN || err == libc::EWOULDBLOCK {
                return;
            }
            // sendmmsg not supported — fall back to single sendmsg
            if err == libc::ENOSYS || err == libc::EINVAL {
                self.flush_responses_single();
                return;
            }
            batch.response_count = 0;
            return;
        }

        let sent_count = sent as usize;
        if sent_count >= batch.response_count {
            batch.response_count = 0;
            return;
        }

        // Move unsent responses to front
        let unsent = batch.response_count - sent_count;
        for i in 0..unsent {
            let src = i + sent_count;
            batch.response_addrs[i] = batch.response_addrs[src];
            batch.response_tokens[i] = batch.response_tokens[src];
            let len = batch.response_iovs[src].iov_len;
            // Use split_at_mut to avoid aliasing borrow
            if src > i {
                let (left, right) = batch.response_buffers.split_at_mut(src);
                left[i][..len].copy_from_slice(&right[0][..len]);
            }
            batch.response_iovs[i] = libc::iovec {
                iov_base: batch.response_buffers[i].as_mut_ptr() as *mut libc::c_void,
                iov_len: len,
            };
        }
        batch.response_count = unsent;
    }

    /// Fallback: flush responses one at a time via sendmsg.
    fn flush_responses_single(&mut self) {
        let batch = match self.batch.as_mut() {
            Some(b) => b,
            None => return,
        };

        let count = batch.response_count;
        for i in 0..count {
            let mut control = [0u8; CONTROL_SIZE];
            let mut msg: libc::msghdr = unsafe { std::mem::zeroed() };
            msg.msg_name = &mut batch.response_addrs[i] as *mut _ as *mut libc::c_void;
            msg.msg_namelen = std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t;
            msg.msg_iov = &mut batch.response_iovs[i];
            msg.msg_iovlen = 1;
            msg.msg_control = control.as_mut_ptr() as *mut libc::c_void;
            msg.msg_controllen = CONTROL_SIZE;

            unsafe {
                let header = libc::CMSG_FIRSTHDR(&msg);
                if !header.is_null() {
                    (*header).cmsg_level = libc::IPPROTO_IP;
                    (*header).cmsg_type = libc::IP_PKTINFO;
                    (*header).cmsg_len = libc::CMSG_LEN(std::mem::size_of::<libc::in_pktinfo>() as u32) as usize;
                    let pktinfo = libc::CMSG_DATA(header) as *mut libc::in_pktinfo;
                    (*pktinfo).ipi_ifindex = 0;
                    std::ptr::copy_nonoverlapping(
                        batch.response_tokens[i].as_ptr(),
                        &mut (*pktinfo).ipi_spec_dst.s_addr as *mut _ as *mut u8,
                        4,
                    );
                }
                libc::sendmsg(self.listener_fd, &msg, libc::MSG_NOSIGNAL);
            }
        }
        batch.response_count = 0;
    }

    fn find_or_create_session(&mut self, token: u32, client_addr: libc::sockaddr_in, now: u64) -> Result<usize, String> {
        // Look up existing session by token
        if let Some(&sid) = self.token_to_session.get(&token) {
            if sid < self.sessions.len() {
                self.sessions[sid].last_active = now;
                self.sessions[sid].client_addr = client_addr;
                return Ok(sid);
            }
        }

        // Create new session
        if self.sessions.len() >= MAX_UDP_SESSIONS {
            return Ok(self.sessions.len()); // overflow
        }

        // Create TCP control socket for SOCKS5 handshake
        let control_fd = match connect_proxy(self.config.proxy_ip, self.config.proxy_port) {
            Ok(fd) => fd,
            Err(e) => return Err(format!("TCP control connect: {}", e)),
        };
        // Create UDP relay socket for data
        let relay_fd = unsafe { libc::socket(libc::AF_INET, libc::SOCK_DGRAM | libc::SOCK_NONBLOCK | libc::SOCK_CLOEXEC, 0) };
        if relay_fd < 0 {
            close_fd(control_fd);
            return Err(format!("UDP relay socket: {}", std::io::Error::last_os_error()));
        }
        // Bind relay socket to any address so it can receive responses
        let bind_addr = libc::sockaddr_in {
            sin_family: libc::AF_INET as u16,
            sin_port: 0,
            sin_addr: libc::in_addr { s_addr: 0 },
            sin_zero: [0; 8],
        };
        if unsafe { libc::bind(relay_fd, &bind_addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t) } < 0 {
            close_fd(relay_fd);
            close_fd(control_fd);
            return Err(format!("UDP relay bind: {}", std::io::Error::last_os_error()));
        }
        // U2: Allow binding to non-local addresses for transparent proxying
        unsafe {
            libc::setsockopt(relay_fd, libc::IPPROTO_IP, libc::IP_FREEBIND, &1i32 as *const _ as *const libc::c_void, 4);
        }
        // Register relay socket with epoll for response-driven I/O
        if self.epoll_fd >= 0 {
            let _ = add_epoll(self.epoll_fd, relay_fd, libc::EPOLLIN as u32, relay_fd as u64);
        }
        self.fd_to_session.insert(relay_fd, self.sessions.len());

        let session = UdpSession {
            state: UdpState::ConnectingProxy,
            control_fd,
            relay_fd,
            last_active: now,
            send_buf: vec![0u8; 256],
            send_len: 0,
            send_off: 0,
            socks_relay_addr: None,
            client_addr,
            pending_datagrams: Vec::new(),
            token,
            recv_buf: [0u8; 256],
            recv_len: 0,
            recv_need: 2,
            bindings: Vec::new(),
        };

        let sid = self.sessions.len();
        self.sessions.push(session);
        self.token_to_session.insert(token, sid);
        self.sessions[sid].bindings.push(token);

        // Start SOCKS5 handshake
        self.start_socks5_handshake(sid)?;

        Ok(sid)
    }

    fn start_socks5_handshake(&mut self, sid: usize) -> Result<(), String> {
        let session = &mut self.sessions[sid];
        session.send_len = socks5::build_no_auth_greeting(&mut session.send_buf);
        session.send_off = 0;
        session.state = UdpState::SendGreeting;

        // control_fd is already connected to proxy (via connect_proxy)
        // Send SOCKS5 greeting via TCP control socket
        let n = unsafe {
            libc::send(session.control_fd, session.send_buf.as_ptr() as *const libc::c_void, session.send_len, libc::MSG_NOSIGNAL)
        };
        if n > 0 {
            session.state = UdpState::WaitGreeting;
        }
        Ok(())
    }

    fn check_proxy_response(&mut self, sid: usize, now: u64) -> Result<(), String> {
        if sid >= self.sessions.len() {
            return Ok(());
        }
        match self.sessions[sid].state {
            UdpState::WaitGreeting => {
                // U2: Accumulate greeting response into recv_buf
                let recv_len = self.sessions[sid].recv_len;
                let control_fd = self.sessions[sid].control_fd;
                let space = 256usize.saturating_sub(recv_len);
                if space > 0 {
                    let n = unsafe {
                        libc::recv(
                            control_fd,
                            self.sessions[sid].recv_buf[recv_len..].as_mut_ptr() as *mut libc::c_void,
                            space,
                            0,
                        )
                    };
                    if n <= 0 {
                        return Ok(());
                    }
                    self.sessions[sid].recv_len += n as usize;
                }
                self.sessions[sid].last_active = now;
                if self.sessions[sid].recv_len >= self.sessions[sid].recv_need {
                    // U1: Validate greeting response: VER=0x05, METHOD=0x00
                    if self.sessions[sid].recv_buf[0] != 0x05 || self.sessions[sid].recv_buf[1] != 0x00 {
                        self.remove_session(sid);
                        return Ok(());
                    }
                    // Build and send ASSOCIATE request
                    self.sessions[sid].send_len = socks5::build_udp_associate_request(&mut self.sessions[sid].send_buf, self.config.listen_port);
                    self.sessions[sid].send_off = 0;
                    let sent = unsafe {
                        libc::send(self.sessions[sid].control_fd, self.sessions[sid].send_buf.as_ptr() as *const libc::c_void, self.sessions[sid].send_len, libc::MSG_NOSIGNAL)
                    };
                    if sent > 0 {
                        self.sessions[sid].recv_len = 0;
                        self.sessions[sid].recv_need = 4;
                        self.sessions[sid].state = UdpState::WaitAssociate;
                    } else {
                        self.sessions[sid].state = UdpState::SendAssociate;
                    }
                }
            }
            UdpState::WaitAssociate => {
                // U2: Accumulate ASSOCIATE response into recv_buf
                let recv_len = self.sessions[sid].recv_len;
                let control_fd = self.sessions[sid].control_fd;
                let space = 256usize.saturating_sub(recv_len);
                if space > 0 {
                    let n = unsafe {
                        libc::recv(
                            control_fd,
                            self.sessions[sid].recv_buf[recv_len..].as_mut_ptr() as *mut libc::c_void,
                            space,
                            0,
                        )
                    };
                    if n <= 0 {
                        return Ok(());
                    }
                    self.sessions[sid].recv_len += n as usize;
                }
                self.sessions[sid].last_active = now;
                let recv_len = self.sessions[sid].recv_len;
                if recv_len >= 4 {
                    // Validate VER, REP, RSV
                    if self.sessions[sid].recv_buf[0] != 0x05
                        || self.sessions[sid].recv_buf[1] != 0x00
                        || self.sessions[sid].recv_buf[2] != 0x00
                    {
                        self.remove_session(sid);
                        return Ok(());
                    }
                    // Determine required length by ATYP
                    let need = match self.sessions[sid].recv_buf[3] {
                        0x01 => 10usize,
                        0x04 => 22usize,
                        0x03 => {
                            if recv_len < 5 {
                                self.sessions[sid].recv_need = 5;
                                return Ok(());
                            }
                            7 + self.sessions[sid].recv_buf[4] as usize
                        }
                        _ => {
                            self.remove_session(sid);
                            return Ok(());
                        }
                    };
                    if recv_len >= need {
                        // Parse UDP ASSOCIATE response from recv_buf
                        let atyp = self.sessions[sid].recv_buf[3];
                        let (relay_addr, _relay_port) = match atyp {
                            0x01 => {
                                let ip = [self.sessions[sid].recv_buf[4], self.sessions[sid].recv_buf[5], self.sessions[sid].recv_buf[6], self.sessions[sid].recv_buf[7]];
                                let port = u16::from_be_bytes([self.sessions[sid].recv_buf[8], self.sessions[sid].recv_buf[9]]);
                                let mut addr = libc::sockaddr_in {
                                    sin_family: libc::AF_INET as u16,
                                    sin_port: port.to_be(),
                                    sin_addr: libc::in_addr { s_addr: u32::from_ne_bytes(ip) },
                                    sin_zero: [0; 8],
                                };
                                if addr.sin_addr.s_addr == 0 || addr.sin_addr.s_addr == u32::MAX {
                                    addr.sin_addr.s_addr = u32::from_be_bytes(self.config.proxy_ip);
                                }
                                (Some(addr), port)
                            }
                            0x03 => {
                                let domain_len = self.sessions[sid].recv_buf[4] as usize;
                                let port_off = 5 + domain_len;
                                if recv_len >= port_off + 2 {
                                    let _port = u16::from_be_bytes([self.sessions[sid].recv_buf[port_off], self.sessions[sid].recv_buf[port_off + 1]]);
                                    let ip = self.config.proxy_ip;
                                    (Some(libc::sockaddr_in {
                                        sin_family: libc::AF_INET as u16,
                                        sin_port: self.config.proxy_port.to_be(),
                                        sin_addr: libc::in_addr { s_addr: u32::from_be_bytes(ip) },
                                        sin_zero: [0; 8],
                                    }), self.config.proxy_port)
                                } else {
                                    (None, 0)
                                }
                            }
                            0x04 => {
                                // TODO: IPv6 relay address cannot be represented in sockaddr_in; falls back to proxy_ip. Full IPv6 support requires sockaddr_in6 for both relay_addr and the listener socket.
                                let mut ip6 = [0u16; 8];
                                for i in 0..8 {
                                    ip6[i] = u16::from_be_bytes([self.sessions[sid].recv_buf[4 + i * 2], self.sessions[sid].recv_buf[5 + i * 2]]);
                                }
                                let _ = ip6; // IPv6 relay cannot be represented in sockaddr_in; proxy_ip fallback is used
                                let port = u16::from_be_bytes([self.sessions[sid].recv_buf[20], self.sessions[sid].recv_buf[21]]);
                                let ip = self.config.proxy_ip;
                                (Some(libc::sockaddr_in {
                                    sin_family: libc::AF_INET as u16,
                                    sin_port: port.to_be(),
                                    sin_addr: libc::in_addr { s_addr: u32::from_be_bytes(ip) },
                                    sin_zero: [0; 8],
                                }), port)
                            }
                            _ => (None, 0),
                        };

                        if let Some(addr) = relay_addr {
                            // Take pending datagrams before mutating session further
                            let pending: Vec<_> = std::mem::take(&mut self.sessions[sid].pending_datagrams);
                            self.sessions[sid].socks_relay_addr = Some(addr);
                            self.sessions[sid].state = UdpState::Relay;

                            // Flush pending datagrams
                            for datagram in pending {
                                let dest_token = datagram.dest_token;
                                let orig = self.resolve_destination(dest_token, now);
                                if let Some(dest) = orig {
                                    let ep = if dest.family == libc::AF_INET6 as u8 {
                                        socks5::Socks5Endpoint::from_ipv6(dest.addr, u16::from_be(dest.port))
                                    } else {
                                        socks5::Socks5Endpoint::from_ipv4(
                                            dest.addr[..4].try_into().unwrap(),
                                            u16::from_be(dest.port),
                                        )
                                    };
                                    let mut header = [0u8; 32];
                                    let header_len = socks5::build_udp_datagram_header(&mut header, &ep);
                                    let mut send_buf = Vec::with_capacity(header_len + datagram.data.len());
                                    send_buf.extend_from_slice(&header[..header_len]);
                                    send_buf.extend_from_slice(&datagram.data);
                                    unsafe {
                                        libc::sendto(
                                            self.sessions[sid].relay_fd,
                                            send_buf.as_ptr() as *const libc::c_void,
                                            send_buf.len(),
                                            0,
                                            &addr as *const _ as *const libc::sockaddr,
                                            std::mem::size_of::<libc::sockaddr_in>() as libc::socklen_t,
                                        );
                                    }
                                }
                            }
                        }
                    } else {
                        self.sessions[sid].recv_need = need;
                    }
                }
            }
            UdpState::Relay => {
                // Relay responses are handled via epoll in handle_relay_response
            }
            _ => {}
        }
        Ok(())
    }

    fn handle_relay_response(&mut self, sid: usize, now: u64) {
        if sid >= self.sessions.len() || self.sessions[sid].state != UdpState::Relay {
            return;
        }
        let relay_fd = self.sessions[sid].relay_fd;
        let mut buf = vec![0u8; 65536];

        // U1: Loop recv until EAGAIN
        loop {
            let n = unsafe {
                libc::recv(relay_fd, buf.as_mut_ptr() as *mut libc::c_void, buf.len(), 0)
            };
            if n <= 0 {
                break;
            }

            if let Some((header_len, ep)) = socks5::parse_udp_datagram_header(&buf[..n as usize]) {
                let payload = &buf[header_len..n as usize];

                // U1: Match by address+port in binding cache
                let ep_addr_len: usize = if ep.addr_type == 0x01 { 4 } else { 16 };
                let matched_token = self.binding_cache.iter().find_map(|(&token, cached)| {
                    let dest = &cached.destination;
                    let dest_addr_len = if dest.family == libc::AF_INET as u8 { 4 } else { 16 };
                    if dest_addr_len == ep_addr_len
                        && dest.addr[..dest_addr_len] == ep.addr[..ep_addr_len]
                        && u16::from_be(dest.port) == ep.port
                    {
                        Some(token)
                    } else {
                        None
                    }
                });

                let default_client = self.sessions[sid].client_addr;
                let default_token = self.sessions[sid].token;
                let (client_addr, token) = if let Some(t) = matched_token {
                    if let Some(binding) = self.binding_cache.get_mut(&t) {
                        binding.last_used = now;
                    }
                    match self.token_to_session.get(&t) {
                        Some(&target_sid) if target_sid < self.sessions.len() => {
                            (self.sessions[target_sid].client_addr, t)
                        }
                        _ => (default_client, default_token),
                    }
                } else {
                    (default_client, default_token)
                };

                self.sessions[sid].last_active = now;

                // Queue response into batch buffer for sendmmsg
                let token_bytes = token.to_be_bytes();
                let token_arr: [u8; 4] = [token_bytes[0], token_bytes[1], token_bytes[2], token_bytes[3]];
                self.queue_response_to_client(&client_addr, &token_arr, payload);
            }
        }
    }

    fn resolve_destination(&mut self, token: u32, now: u64) -> Option<OriginalDestination> {
        if let Some(cached) = self.binding_cache.get_mut(&token) {
            cached.last_used = now;
            return Some(cached.destination);
        }
        let token_bytes = token.to_ne_bytes();
        let dest = unsafe { &*self.cgroup }.take_udp_destination(self.config.listen_port, &token_bytes)?;
        self.evict_oldest_binding();
        self.binding_cache.insert(token, CachedBinding {
            token,
            destination: dest,
            last_used: now,
        });
        Some(dest)
    }

    fn evict_oldest_binding(&mut self) {
        if self.binding_cache.len() < MAX_BINDINGS {
            return;
        }
        let oldest_key = self.binding_cache.iter()
            .min_by_key(|(_, v)| v.last_used)
            .map(|(&k, _)| k);
        if let Some(key) = oldest_key {
            if let Some(entry) = self.binding_cache.remove(&key) {
                let token_bytes = entry.token.to_ne_bytes();
                unsafe { &*self.cgroup }.release_udp_destination(self.config.listen_port, &token_bytes);
            }
        }
    }

    fn remove_session(&mut self, sid: usize) {
        if sid >= self.sessions.len() {
            return;
        }
        // Release eBPF bindings
        for b in &self.sessions[sid].bindings {
            let b_bytes = b.to_ne_bytes();
            unsafe { &*self.cgroup }.release_udp_destination(self.config.listen_port, &b_bytes);
        }
        // Unregister from epoll
        if self.epoll_fd >= 0 {
            unsafe {
                libc::epoll_ctl(self.epoll_fd, libc::EPOLL_CTL_DEL, self.sessions[sid].relay_fd, std::ptr::null_mut());
            }
        }
        // Remove fd mapping
        self.fd_to_session.remove(&self.sessions[sid].relay_fd);
        // Close fds
        close_fd(self.sessions[sid].control_fd);
        close_fd(self.sessions[sid].relay_fd);
        // Remove session (swap_remove)
        self.sessions.swap_remove(sid);
        // Rebuild index maps
        self.token_to_session.clear();
        self.fd_to_session.clear();
        for (i, session) in self.sessions.iter().enumerate() {
            self.token_to_session.insert(session.token, i);
            self.fd_to_session.insert(session.relay_fd, i);
        }
    }

    fn cleanup_idle(&mut self, now: u64) {
        // U4: Use remove_session for expired sessions
        let mut sid = 0;
        while sid < self.sessions.len() {
            if now.saturating_sub(self.sessions[sid].last_active) > UDP_SESSION_IDLE_MS {
                self.remove_session(sid);
            } else {
                sid += 1;
            }
        }
        // Evict stale binding cache entries
        let stale_tokens: Vec<u32> = self.binding_cache.iter()
            .filter(|(_, v)| now.saturating_sub(v.last_used) > UDP_SESSION_IDLE_MS)
            .map(|(&k, _)| k)
            .collect();
        for token in stale_tokens {
            if let Some(entry) = self.binding_cache.remove(&token) {
                let token_bytes = entry.token.to_ne_bytes();
                unsafe { &*self.cgroup }.release_udp_destination(self.config.listen_port, &token_bytes);
            }
        }
    }
}

impl Drop for UdpBridge {
    fn drop(&mut self) {
        while !self.sessions.is_empty() {
            self.remove_session(0);
        }
        close_fd(self.listener_fd);
        if self.epoll_fd >= 0 {
            close_fd(self.epoll_fd);
        }
    }
}
