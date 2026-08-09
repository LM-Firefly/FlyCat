cmake_minimum_required(VERSION 3.22.1)

project(yumebox-ebpf-bridge CXX)

add_executable(
    ebpf-bridge
    shell/main.cpp
    core/bpf_syscall.cpp
    core/bpf_program_builder.cpp
    core/cgroup_runtime.cpp
    bridge/socks5.cpp
    bridge/tcp_bridge.cpp
    bridge/udp_bridge.cpp
)

set_target_properties(ebpf-bridge PROPERTIES OUTPUT_NAME "libebpfbridge.so")

target_include_directories(ebpf-bridge PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_features(ebpf-bridge PRIVATE cxx_std_20)
target_compile_options(
    ebpf-bridge
    PRIVATE
    -O3
    -fPIE
    -fvisibility=hidden
    -fno-exceptions
    -fno-rtti
    -Wall
    -Wextra
    -Werror
)
target_link_options(
    ebpf-bridge
    PRIVATE
    -pie
    -pthread
    -Wl,--gc-sections
    -Wl,--build-id=none
    -Wl,-z,relro
    -Wl,-z,now
)
