// SOCKS5 protocol frame builder — zero-allocation, network byte order.
//
// Mirrors the C++ bridge/socks5.cpp.

/// SOCKS5 endpoint (IPv4 or IPv6).
pub struct Socks5Endpoint {
    pub addr_type: u8, // 0x01 = IPv4, 0x04 = IPv6
    pub addr: [u8; 16],
    pub port: u16, // network byte order
}

impl Socks5Endpoint {
    pub fn from_ipv4(ip: [u8; 4], port: u16) -> Self {
        let mut addr = [0u8; 16];
        addr[..4].copy_from_slice(&ip);
        Self { addr_type: 0x01, addr, port }
    }

    pub fn from_ipv6(ip: [u8; 16], port: u16) -> Self {
        Self { addr_type: 0x04, addr: ip, port }
    }
}

/// Build SOCKS5 no-auth greeting: [05 01 00].
pub fn build_no_auth_greeting(buf: &mut [u8]) -> usize {
    buf[0] = 0x05; // VER
    buf[1] = 0x01; // NMETHODS
    buf[2] = 0x00; // NO AUTHENTICATION REQUIRED
    3
}

/// Build SOCKS5 CONNECT request.
/// Returns number of bytes written.
pub fn build_connect_request(buf: &mut [u8], ep: &Socks5Endpoint) -> usize {
    buf[0] = 0x05; // VER
    buf[1] = 0x01; // CMD = CONNECT
    buf[2] = 0x00; // RSV
    buf[3] = ep.addr_type; // ATYP
    let addr_len = if ep.addr_type == 0x01 { 4 } else { 16 };
    buf[4..4 + addr_len].copy_from_slice(&ep.addr[..addr_len]);
    let port_off = 4 + addr_len;
    buf[port_off] = (ep.port >> 8) as u8;
    buf[port_off + 1] = (ep.port & 0xFF) as u8;
    port_off + 2
}

/// Build SOCKS5 UDP ASSOCIATE request (for asking the proxy to allocate a UDP relay).
pub fn build_udp_associate_request(buf: &mut [u8], local_port: u16) -> usize {
    buf[0] = 0x05; // VER
    buf[1] = 0x03; // CMD = UDP ASSOCIATE
    buf[2] = 0x00; // RSV
    buf[3] = 0x01; // ATYP = IPv4
    buf[4..8].copy_from_slice(&[0, 0, 0, 0]); // DST.ADDR = 0.0.0.0
    buf[8] = (local_port >> 8) as u8;
    buf[9] = (local_port & 0xFF) as u8;
    10
}

/// Build SOCKS5 UDP datagram header for sending.
/// Returns number of bytes written (header only, payload follows).
pub fn build_udp_datagram_header(buf: &mut [u8], ep: &Socks5Endpoint) -> usize {
    buf[0] = 0x00; // RSV
    buf[1] = 0x00; // RSV
    buf[2] = 0x00; // FRAG
    buf[3] = ep.addr_type;
    let addr_len = if ep.addr_type == 0x01 { 4 } else { 16 };
    buf[4..4 + addr_len].copy_from_slice(&ep.addr[..addr_len]);
    let port_off = 4 + addr_len;
    buf[port_off] = (ep.port >> 8) as u8;
    buf[port_off + 1] = (ep.port & 0xFF) as u8;
    port_off + 2
}

/// Parse a SOCKS5 UDP datagram header.
/// Returns (header_len, endpoint) or None on parse error.
pub fn parse_udp_datagram_header(buf: &[u8]) -> Option<(usize, Socks5Endpoint)> {
    if buf.len() < 4 {
        return None;
    }
    if buf[0] != 0x00 || buf[1] != 0x00 || buf[2] != 0x00 {
        return None;
    }
    let atyp = buf[3];
    let (addr_len, header_len) = match atyp {
        0x01 => (4, 10),
        0x04 => (16, 22),
        _ => return None,
    };
    if buf.len() < header_len {
        return None;
    }
    let mut addr = [0u8; 16];
    addr[..addr_len].copy_from_slice(&buf[4..4 + addr_len]);
    let port = u16::from_be_bytes([buf[4 + addr_len], buf[4 + addr_len + 1]]);
    Some((header_len, Socks5Endpoint { addr_type: atyp, addr, port }))
}
