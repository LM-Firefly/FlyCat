#pragma once

#include <cstddef>
#include <cstdint>

namespace yumebox::ebpf {

struct Socks5Endpoint final {
    std::uint8_t address_type = 0;
    std::uint8_t address[16]{};
    std::uint16_t port = 0;
};

// The builders are allocation-free and produce network-byte-order SOCKS5 frames.
std::size_t buildNoAuthGreeting(std::uint8_t* output, std::size_t capacity);
std::size_t buildConnectRequest(
    const Socks5Endpoint& destination,
    std::uint8_t* output,
    std::size_t capacity);
std::size_t buildUdpDatagram(
    const Socks5Endpoint& destination,
    const std::uint8_t* payload,
    std::size_t payload_size,
    std::uint8_t* output,
    std::size_t capacity);

}  // namespace yumebox::ebpf
