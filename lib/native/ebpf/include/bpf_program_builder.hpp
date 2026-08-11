#pragma once

#include <cstdint>

namespace yumebox::ebpf {

// Builds the socket-address programs shared by TCP and connected UDP. UDP
// sendmsg/recvmsg have separate programs because their cgroup hook contexts
// are different. In hijack mode, DNS port 53 bypasses the bridge and is
// rewritten directly to the local mihomo DNS listener.
int loadTcp4ConnectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 1,
    std::uint16_t dns_listener_port = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp4SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 1,
    std::uint16_t dns_listener_port = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp4RecvmsgProgram(
    int redirect_map_fd,
    std::uint16_t listener_port);

int loadIpv6ConnectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 1,
    std::uint16_t dns_listener_port = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp6SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 1,
    std::uint16_t dns_listener_port = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp6RecvmsgProgram(
    int redirect_map_fd,
    std::uint16_t listener_port);

bool probeSocketAddressPrograms();

}  // namespace yumebox::ebpf
