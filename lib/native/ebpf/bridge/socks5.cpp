#include "socks5.hpp"

#include <arpa/inet.h>

#include <cstring>

namespace yumebox::ebpf {
namespace {

constexpr std::size_t kMaxAddressSize = 16;

std::size_t addressSize(std::uint8_t address_type) {
    switch (address_type) {
        case 0x01:
            return 4;
        case 0x04:
            return kMaxAddressSize;
        default:
            return 0;
    }
}

std::size_t writeAddress(
    const Socks5Endpoint& endpoint,
    std::uint8_t* output,
    std::size_t capacity) {
    const std::size_t size = addressSize(endpoint.address_type);
    if (size == 0 || capacity < size + 3) {
        return 0;
    }
    output[0] = endpoint.address_type;
    std::memcpy(output + 1, endpoint.address, size);
    output[size + 1] = static_cast<std::uint8_t>(endpoint.port >> 8);
    output[size + 2] = static_cast<std::uint8_t>(endpoint.port & 0xff);
    return size + 3;
}

}  // namespace

std::size_t buildNoAuthGreeting(std::uint8_t* output, std::size_t capacity) {
    if (output == nullptr || capacity < 3) {
        return 0;
    }
    output[0] = 0x05;
    output[1] = 0x01;
    output[2] = 0x00;
    return 3;
}

std::size_t buildConnectRequest(
    const Socks5Endpoint& destination,
    std::uint8_t* output,
    std::size_t capacity) {
    if (output == nullptr || capacity < 4) {
        return 0;
    }
    output[0] = 0x05;
    output[1] = 0x01;
    output[2] = 0x00;
    const std::size_t address_length = writeAddress(destination, output + 3, capacity - 3);
    return address_length == 0 ? 0 : address_length + 3;
}

std::size_t buildUdpDatagram(
    const Socks5Endpoint& destination,
    const std::uint8_t* payload,
    std::size_t payload_size,
    std::uint8_t* output,
    std::size_t capacity) {
    if (output == nullptr || payload == nullptr || capacity < 4) {
        return 0;
    }
    output[0] = 0;
    output[1] = 0;
    output[2] = 0;
    output[3] = 0;
    const std::size_t address_length = writeAddress(destination, output + 3, capacity - 3);
    if (address_length == 0 || capacity - 3 - address_length < payload_size) {
        return 0;
    }
    std::memcpy(output + 3 + address_length, payload, payload_size);
    return 3 + address_length + payload_size;
}

}  // namespace yumebox::ebpf
