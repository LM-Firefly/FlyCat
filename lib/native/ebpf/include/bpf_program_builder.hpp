#pragma once

#include <cstdint>

namespace yumebox::ebpf {

// Builds the IPv4 CONNECT program shared by TCP and connected UDP. UDP
// sendmsg/recvmsg have separate programs because their cgroup hook contexts
// are different.
// The program stores the original destination in redirect_map and rewrites the
// connect destination to 127.128.0.0/9:<listener_port>.
int loadTcp4ConnectProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp4SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 0,
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
    std::uint8_t dns_mode = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp6SendmsgProgram(
    int redirect_map_fd,
    int bypass_tgid_map_fd,
    std::uint16_t listener_port,
    int uid_policy_map_fd = -1,
    std::uint8_t uid_policy_mode = 0,
    std::uint8_t dns_mode = 0,
    int bypass_cidr4_map_fd = -1,
    int bypass_cidr6_map_fd = -1);

int loadUdp6RecvmsgProgram(
    int redirect_map_fd,
    std::uint16_t listener_port);

bool probeSocketAddressPrograms();

}  // namespace yumebox::ebpf
