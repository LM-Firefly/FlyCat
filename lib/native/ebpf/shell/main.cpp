#include "bpf_syscall.hpp"
#include "bpf_program_builder.hpp"
#include "cgroup_runtime.hpp"
#include "tcp_bridge.hpp"
#include "udp_bridge.hpp"

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <arpa/inet.h>
#include <vector>

#include <signal.h>
#include <pthread.h>
#include <sys/types.h>
#include <unistd.h>

namespace {

constexpr const char* kVersion = "yumebox-ebpf-bridge phase3-ipv4-ipv6-tcp-udp-root";
std::atomic_bool g_stop{false};
static_assert(std::atomic_bool::is_always_lock_free);
volatile sig_atomic_t g_stop_signal = 0;

void stopHandler(int signal) {
    g_stop_signal = signal;
    g_stop.store(true, std::memory_order_relaxed);
}

const char* signalName(int signal) {
    switch (signal) {
        case SIGTERM:
            return "SIGTERM";
        case SIGINT:
            return "SIGINT";
        default:
            return "signal";
    }
}

void printUsage(const char* executable) {
    std::printf(
        "Usage: %s --probe [--cgroup PATH]\n"
        "       %s --cleanup --cgroup PATH\n"
        "       %s --run [--cgroup PATH] [--bridge-port PORT]\n"
        "                    [--socks-host IPV4] [--socks-port PORT]\n"
        "                    [--mihomo-pid PID]\n"
        "                    [--uid-policy all|include|exclude] [--uids UID[,UID...]]\n"
        "                    [--dns-mode proxy|bypass]\n"
        "                    [--ipv6 on|off]\n"
        "                    [--bypass-cidrs CIDR[,CIDR...]]\n"
        "       %s --version\n",
        executable,
        executable,
        executable,
        executable);
}

const char* argumentValue(int argc, char** argv, const char* name) {
    for (int index = 1; index + 1 < argc; ++index) {
        if (std::strcmp(argv[index], name) == 0) {
            return argv[index + 1];
        }
    }
    return nullptr;
}

bool hasArgument(int argc, char** argv, const char* name) {
    for (int index = 1; index < argc; ++index) {
        if (std::strcmp(argv[index], name) == 0) {
            return true;
        }
    }
    return false;
}

bool unsignedArgument(int argc, char** argv, const char* name, std::uint32_t default_value, std::uint32_t* result) {
    const char* value = argumentValue(argc, argv, name);
    if (value == nullptr) {
        *result = default_value;
        return true;
    }
    char* end = nullptr;
    errno = 0;
    const unsigned long parsed = std::strtoul(value, &end, 10);
    if (errno != 0 || end == value || *end != '\0' || parsed > UINT32_MAX) {
        return false;
    }
    *result = static_cast<std::uint32_t>(parsed);
    return true;
}

bool uidPolicyArgument(int argc, char** argv, std::uint8_t* result) {
    const char* value = argumentValue(argc, argv, "--uid-policy");
    if (value == nullptr || std::strcmp(value, "all") == 0 || std::strcmp(value, "0") == 0) {
        *result = 0;
        return true;
    }
    if (std::strcmp(value, "include") == 0 || std::strcmp(value, "1") == 0) {
        *result = 1;
        return true;
    }
    if (std::strcmp(value, "exclude") == 0 || std::strcmp(value, "2") == 0) {
        *result = 2;
        return true;
    }
    return false;
}

bool uidListArgument(int argc, char** argv, std::vector<std::uint32_t>* result) {
    result->clear();
    const char* value = argumentValue(argc, argv, "--uids");
    if (value == nullptr || value[0] == '\0') {
        return true;
    }
    const char* cursor = value;
    while (*cursor != '\0') {
        char* end = nullptr;
        errno = 0;
        const unsigned long parsed = std::strtoul(cursor, &end, 10);
        if (errno != 0 || end == cursor || parsed > UINT32_MAX || (*end != '\0' && *end != ',')) {
            return false;
        }
        result->push_back(static_cast<std::uint32_t>(parsed));
        if (result->size() > 4096) return false;
        if (*end == '\0') return true;
        cursor = end + 1;
        if (*cursor == '\0') return false;
    }
    return true;
}

bool dnsModeArgument(int argc, char** argv, std::uint8_t* result) {
    const char* value = argumentValue(argc, argv, "--dns-mode");
    if (value == nullptr || std::strcmp(value, "proxy") == 0 || std::strcmp(value, "0") == 0) {
        *result = 0;
        return true;
    }
    if (std::strcmp(value, "bypass") == 0 || std::strcmp(value, "1") == 0) {
        *result = 1;
        return true;
    }
    return false;
}

bool cidrListArgument(int argc, char** argv, std::vector<yumebox::ebpf::CidrRule>* result) {
    result->clear();
    const char* value = argumentValue(argc, argv, "--bypass-cidrs");
    if (value == nullptr || value[0] == '\0') return true;
    const char* cursor = value;
    while (*cursor != '\0') {
        const char* comma = std::strchr(cursor, ',');
        const std::size_t length = comma == nullptr
            ? std::strlen(cursor)
            : static_cast<std::size_t>(comma - cursor);
        if (length == 0 || length >= 64) return false;
        char token[64]{};
        std::memcpy(token, cursor, length);
        char* slash = std::strchr(token, '/');
        if (slash == nullptr) return false;
        *slash = '\0';
        char* end = nullptr;
        errno = 0;
        const unsigned long prefix = std::strtoul(slash + 1, &end, 10);
        if (errno != 0 || end == slash + 1 || *end != '\0') return false;

        yumebox::ebpf::CidrRule rule{};
        const int family = std::strchr(token, ':') != nullptr ? AF_INET6 : AF_INET;
        const std::uint32_t max_prefix = family == AF_INET ? 32U : 128U;
        const std::size_t address_size = family == AF_INET ? 4U : 16U;
        if (prefix > max_prefix || inet_pton(family, token, rule.address) != 1) return false;
        rule.family = static_cast<std::uint8_t>(family);
        rule.prefix_length = static_cast<std::uint8_t>(prefix);
        const std::size_t prefix_byte = prefix / 8U;
        const std::uint32_t prefix_bits = prefix % 8U;
        if (prefix_bits != 0 && prefix_byte < address_size) {
            rule.address[prefix_byte] = static_cast<std::uint8_t>(
                rule.address[prefix_byte] & static_cast<std::uint8_t>(0xffU << (8U - prefix_bits)));
        }
        const std::size_t first_zero_byte = prefix_bits == 0 ? prefix_byte : prefix_byte + 1U;
        for (std::size_t index = first_zero_byte; index < address_size; ++index) {
            rule.address[index] = 0;
        }
        for (std::size_t index = address_size; index < sizeof(rule.address); ++index) {
            rule.address[index] = 0;
        }
        result->push_back(rule);
        if (result->size() > 256) return false;
        if (comma == nullptr) return true;
        cursor = comma + 1;
        if (*cursor == '\0') return false;
    }
    return true;
}

bool ipv6ModeArgument(int argc, char** argv, bool* result) {
    const char* value = argumentValue(argc, argv, "--ipv6");
    if (value == nullptr || std::strcmp(value, "on") == 0 || std::strcmp(value, "1") == 0) {
        *result = true;
        return true;
    }
    if (std::strcmp(value, "off") == 0 || std::strcmp(value, "0") == 0) {
        *result = false;
        return true;
    }
    return false;
}

int probe(int argc, char** argv) {
    const char* cgroup_path = argumentValue(argc, argv, "--cgroup");
    if (cgroup_path == nullptr) {
        cgroup_path = "/sys/fs/cgroup";
    }

    const bool cgroup_v2 = yumebox::ebpf::isCgroupV2Mount(cgroup_path);
    const int map_fd = yumebox::ebpf::probeMapCreate();
    const int map_errno = errno;
    if (map_fd >= 0) {
        close(map_fd);
    }

    const bool socket_address_programs = map_fd >= 0 && yumebox::ebpf::probeSocketAddressPrograms();
    const int program_errno = errno;
    const bool cgroup_attach =
        cgroup_v2 && socket_address_programs && yumebox::ebpf::probeSocketAddressCgroupAttach(cgroup_path);
    const int attach_errno = errno;
    std::printf(
        "{\"cgroup\":\"%s\",\"cgroup_v2\":%s,\"bpf_map_create\":%s,\"bpf_socket_address\":%s,\"bpf_cgroup_attach\":%s,\"ready\":%s}\n",
        cgroup_path,
        cgroup_v2 ? "true" : "false",
        map_fd >= 0 ? "true" : "false",
        socket_address_programs ? "true" : "false",
        cgroup_attach ? "true" : "false",
        cgroup_v2 && map_fd >= 0 && socket_address_programs && cgroup_attach ? "true" : "false");
    if (!cgroup_v2) {
        std::fprintf(stderr, "eBPF bridge: %s is not a cgroup v2 mount\n", cgroup_path);
    }
    if (map_fd < 0) {
        std::fprintf(
            stderr,
            "eBPF bridge: BPF_MAP_CREATE failed: errno=%d (%s)\n",
            map_errno,
            std::strerror(map_errno));
    }
    if (!socket_address_programs) {
        std::fprintf(stderr, "eBPF bridge: required IPv4/IPv6 socket-address BPF hooks are unavailable\n");
    }
    if (!cgroup_attach) {
        std::fprintf(
            stderr,
            "eBPF bridge: BPF_CGROUP_ATTACH probe failed: errno=%d (%s)\n",
            attach_errno != 0 ? attach_errno : program_errno,
            std::strerror(attach_errno != 0 ? attach_errno : program_errno));
    }
    return cgroup_v2 && map_fd >= 0 && socket_address_programs && cgroup_attach ? 0 : 1;
}

struct UdpThreadContext final {
    yumebox::ebpf::UdpBridge* bridge = nullptr;
    std::atomic_bool* stop_flag = nullptr;
    int result = 0;
    int error_number = 0;
};

void* runUdpBridge(void* opaque) {
    auto* context = static_cast<UdpThreadContext*>(opaque);
    context->result = context->bridge->run(context->stop_flag);
    context->error_number = context->result == 0 ? 0 : errno;
    if (context->result != 0) context->stop_flag->store(true, std::memory_order_relaxed);
    return nullptr;
}

int runBridge(int argc, char** argv) {
    std::uint32_t bridge_port = 0;
    std::uint32_t socks_port = 0;
    std::uint32_t mihomo_pid = 0;
    std::uint8_t uid_policy_mode = 0;
    std::uint8_t dns_mode = 0;
    bool enable_ipv6 = true;
    std::vector<std::uint32_t> policy_uids;
    std::vector<yumebox::ebpf::CidrRule> bypass_cidrs;
    if (!unsignedArgument(argc, argv, "--bridge-port", 0, &bridge_port) ||
        !unsignedArgument(argc, argv, "--socks-port", 7890, &socks_port) ||
        !unsignedArgument(argc, argv, "--mihomo-pid", 0, &mihomo_pid) ||
        !uidPolicyArgument(argc, argv, &uid_policy_mode) ||
        !uidListArgument(argc, argv, &policy_uids) ||
        !dnsModeArgument(argc, argv, &dns_mode) ||
        !ipv6ModeArgument(argc, argv, &enable_ipv6) ||
        !cidrListArgument(argc, argv, &bypass_cidrs) ||
        bridge_port > UINT16_MAX || socks_port == 0 || socks_port > UINT16_MAX) {
        std::fprintf(stderr, "eBPF bridge: invalid port or PID argument\n");
        return 2;
    }
    const char* cgroup_path = argumentValue(argc, argv, "--cgroup");
    if (cgroup_path == nullptr) {
        cgroup_path = "/sys/fs/cgroup";
    }
    const char* socks_host = argumentValue(argc, argv, "--socks-host");
    if (socks_host == nullptr) {
        socks_host = "127.0.0.1";
    }
    std::fprintf(
        stderr,
        "eBPF bridge: startup cgroup=%s socks=%s:%u mihomo-pid=%u uid-policy=%u uids=%zu dns-mode=%u ipv6=%s bypass-cidrs=%zu\n",
        cgroup_path,
        socks_host,
        socks_port,
        mihomo_pid,
        uid_policy_mode,
        policy_uids.size(),
        dns_mode,
        enable_ipv6 ? "on" : "off",
        bypass_cidrs.size());
    std::fflush(stderr);

    if (yumebox::ebpf::cleanupSocketAddressPrograms(cgroup_path) != 0) {
        std::fprintf(
            stderr,
            "eBPF bridge: stale hook cleanup unavailable: errno=%d (%s)\n",
            errno,
            std::strerror(errno));
    }

    yumebox::ebpf::CgroupRuntime runtime;
    yumebox::ebpf::TcpBridge bridge;
    yumebox::ebpf::UdpBridge udp_bridge;
    yumebox::ebpf::TcpBridgeConfig bridge_config{
        static_cast<std::uint16_t>(bridge_port),
        socks_host,
        static_cast<std::uint16_t>(socks_port),
    };
    if (!bridge.open(bridge_config, &runtime)) {
        std::fprintf(stderr, "eBPF bridge: listener open failed: errno=%d (%s)\n", errno, std::strerror(errno));
        return 1;
    }
    yumebox::ebpf::UdpBridgeConfig udp_config{
        bridge.port(),
        socks_host,
        static_cast<std::uint16_t>(socks_port),
    };
    if (!udp_bridge.open(udp_config, &runtime)) {
        std::fprintf(stderr, "eBPF bridge: UDP listener open failed: errno=%d (%s)\n", errno, std::strerror(errno));
        bridge.close();
        return 1;
    }
    if (!runtime.start(
            cgroup_path,
            bridge.port(),
            static_cast<std::uint32_t>(getpid()),
            mihomo_pid,
            uid_policy_mode,
            policy_uids.data(),
            policy_uids.size(),
            dns_mode,
            enable_ipv6,
            bypass_cidrs.data(),
            bypass_cidrs.size())) {
        std::fprintf(stderr, "eBPF bridge: cgroup setup failed: errno=%d (%s)\n", errno, std::strerror(errno));
        bridge.close();
        udp_bridge.close();
        return 1;
    }
    if (mihomo_pid == 0) {
        std::fprintf(stderr, "eBPF bridge: warning: --mihomo-pid is omitted; mihomo must be outside this cgroup or it may loop\n");
    }
    struct sigaction action{};
    action.sa_handler = stopHandler;
    sigemptyset(&action.sa_mask);
    struct sigaction ignore_pipe{};
    ignore_pipe.sa_handler = SIG_IGN;
    sigemptyset(&ignore_pipe.sa_mask);
    if (sigaction(SIGTERM, &action, nullptr) != 0 ||
        sigaction(SIGINT, &action, nullptr) != 0 ||
        sigaction(SIGPIPE, &ignore_pipe, nullptr) != 0) {
        std::fprintf(stderr, "eBPF bridge: signal setup failed: errno=%d (%s)\n", errno, std::strerror(errno));
        bridge.close();
        udp_bridge.close();
        runtime.stop();
        return 1;
    }
    g_stop.store(false, std::memory_order_relaxed);
    g_stop_signal = 0;
    UdpThreadContext udp_thread_context{&udp_bridge, &g_stop, 0, 0};
    pthread_t udp_thread{};
    if (pthread_create(&udp_thread, nullptr, runUdpBridge, &udp_thread_context) != 0) {
        std::fprintf(stderr, "eBPF bridge: UDP worker start failed: errno=%d (%s)\n", errno, std::strerror(errno));
        bridge.close();
        udp_bridge.close();
        runtime.stop();
        return 1;
    }
    std::printf(
        "eBPF bridge: tcp4/udp4 listener (IPv6 mapped) on 0.0.0.0:%u, mihomo SOCKS %s:%u\n",
        bridge.port(),
        socks_host,
        socks_port);
    const int result = bridge.run(&g_stop);
    const int tcp_errno = result == 0 ? 0 : errno;
    g_stop.store(true, std::memory_order_relaxed);
    (void)pthread_join(udp_thread, nullptr);
    bridge.close();
    udp_bridge.close();
    runtime.stop();
    if (result != 0) {
        std::fprintf(
            stderr,
            "eBPF bridge: TCP event loop failed: errno=%d (%s)\n",
            tcp_errno,
            std::strerror(tcp_errno));
        return 1;
    }
    if (udp_thread_context.result != 0) {
        const int udp_errno = udp_thread_context.error_number == 0 ? EIO : udp_thread_context.error_number;
        std::fprintf(
            stderr,
            "eBPF bridge: UDP event loop failed: errno=%d (%s)\n",
            udp_errno,
            std::strerror(udp_errno));
        return 1;
    }
    if (g_stop_signal != 0) {
        std::fprintf(stderr, "eBPF bridge: stopped by %s (%d)\n", signalName(g_stop_signal), g_stop_signal);
    } else {
        std::fprintf(stderr, "eBPF bridge: stopped by internal request\n");
    }
    return 0;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc <= 1 || hasArgument(argc, argv, "--help")) {
        printUsage(argv[0]);
        return argc <= 1 ? 2 : 0;
    }
    if (hasArgument(argc, argv, "--version")) {
        std::puts(kVersion);
        return 0;
    }
    if (hasArgument(argc, argv, "--probe")) {
        return probe(argc, argv);
    }
    if (hasArgument(argc, argv, "--cleanup")) {
        const char* cgroup_path = argumentValue(argc, argv, "--cgroup");
        if (cgroup_path == nullptr) cgroup_path = "/sys/fs/cgroup";
        if (yumebox::ebpf::cleanupSocketAddressPrograms(cgroup_path) != 0) {
            std::fprintf(
                stderr,
                "eBPF bridge: cleanup failed: errno=%d (%s)\n",
                errno,
                std::strerror(errno));
            return 1;
        }
        return 0;
    }
    if (hasArgument(argc, argv, "--run")) {
        return runBridge(argc, argv);
    }
    std::fprintf(stderr, "eBPF bridge: unsupported command or data plane is unavailable\n");
    printUsage(argv[0]);
    return 2;
}
