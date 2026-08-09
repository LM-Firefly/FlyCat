#include "bpf_syscall.hpp"

#include <cerrno>
#include <cstdio>
#include <cstring>

#include <fcntl.h>
#include <sys/statfs.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef CGROUP2_SUPER_MAGIC
#define CGROUP2_SUPER_MAGIC 0x63677270
#endif

#ifndef SYS_bpf
#if defined(__aarch64__)
#define SYS_bpf 280
#elif defined(__arm__)
#define SYS_bpf 386
#elif defined(__x86_64__)
#define SYS_bpf 321
#elif defined(__i386__)
#define SYS_bpf 357
#endif
#endif

namespace yumebox::ebpf {

long bpfSyscall(enum bpf_cmd command, union bpf_attr* attributes, unsigned int size) {
#if defined(SYS_bpf)
    return syscall(SYS_bpf, command, attributes, size);
#else
    (void)command;
    (void)attributes;
    (void)size;
    errno = ENOSYS;
    return -1;
#endif
}

int createMap(
    enum bpf_map_type type,
    std::uint32_t key_size,
    std::uint32_t value_size,
    std::uint32_t max_entries,
    std::uint32_t flags) {
    union bpf_attr attributes{};
    attributes.map_type = type;
    attributes.key_size = key_size;
    attributes.value_size = value_size;
    attributes.max_entries = max_entries;
    attributes.map_flags = flags;
    return static_cast<int>(bpfSyscall(BPF_MAP_CREATE, &attributes, sizeof(attributes)));
}

int updateMap(int map_fd, const void* key, const void* value, std::uint64_t flags) {
    union bpf_attr attributes{};
    attributes.map_fd = static_cast<std::uint32_t>(map_fd);
    attributes.key = reinterpret_cast<std::uint64_t>(key);
    attributes.value = reinterpret_cast<std::uint64_t>(value);
    attributes.flags = flags;
    return static_cast<int>(bpfSyscall(BPF_MAP_UPDATE_ELEM, &attributes, sizeof(attributes)));
}

int lookupMap(int map_fd, const void* key, void* value) {
    union bpf_attr attributes{};
    attributes.map_fd = static_cast<std::uint32_t>(map_fd);
    attributes.key = reinterpret_cast<std::uint64_t>(key);
    attributes.value = reinterpret_cast<std::uint64_t>(value);
    return static_cast<int>(bpfSyscall(BPF_MAP_LOOKUP_ELEM, &attributes, sizeof(attributes)));
}

int deleteMap(int map_fd, const void* key) {
    union bpf_attr attributes{};
    attributes.map_fd = static_cast<std::uint32_t>(map_fd);
    attributes.key = reinterpret_cast<std::uint64_t>(key);
    return static_cast<int>(bpfSyscall(BPF_MAP_DELETE_ELEM, &attributes, sizeof(attributes)));
}

namespace {

#ifndef BPF_OBJ_NAME_LEN
constexpr std::size_t kBpfObjectNameLength = 16;
#else
constexpr std::size_t kBpfObjectNameLength = BPF_OBJ_NAME_LEN;
#endif

constexpr std::size_t kVerifierLogSize = 64 * 1024;

int loadProgramOnce(
    const struct bpf_insn* instructions,
    std::size_t instruction_count,
    const char* name,
    enum bpf_prog_type program_type,
    enum bpf_attach_type expected_attach_type,
    char* log_buffer,
    std::uint32_t log_size,
    std::uint32_t log_level) {
    union bpf_attr attributes{};
    attributes.prog_type = program_type;
    attributes.insns = reinterpret_cast<std::uint64_t>(instructions);
    attributes.insn_cnt = static_cast<std::uint32_t>(instruction_count);
    attributes.license = reinterpret_cast<std::uint64_t>("GPL");
    attributes.expected_attach_type = expected_attach_type;
    attributes.log_buf = reinterpret_cast<std::uint64_t>(log_buffer);
    attributes.log_size = log_size;
    attributes.log_level = log_level;
    if (name != nullptr) {
        std::snprintf(attributes.prog_name, kBpfObjectNameLength, "%s", name);
    }
    return static_cast<int>(bpfSyscall(BPF_PROG_LOAD, &attributes, sizeof(attributes)));
}

}  // namespace

int loadProgram(
    const struct bpf_insn* instructions,
    std::size_t instruction_count,
    const char* name,
    enum bpf_prog_type program_type,
    enum bpf_attach_type expected_attach_type,
    bool log_errors) {
    static char verifier_log[kVerifierLogSize];
    std::memset(verifier_log, 0, sizeof(verifier_log));
    const std::uint32_t log_level = log_errors ? 1U : 0U;
    int program_fd = loadProgramOnce(
        instructions,
        instruction_count,
        name,
        program_type,
        expected_attach_type,
        log_errors ? verifier_log : nullptr,
        log_errors ? static_cast<std::uint32_t>(sizeof(verifier_log)) : 0U,
        log_level);
    if (program_fd < 0 && log_errors) {
        std::fprintf(
            stderr,
            "%s: BPF_PROG_LOAD failed: errno=%d (%s)\n%s\n",
            name == nullptr ? "eBPF program" : name,
            errno,
            std::strerror(errno),
            verifier_log);
    }
    return program_fd;
}

int attachProgram(int cgroup_fd, int program_fd, enum bpf_attach_type attach_type) {
    union bpf_attr attributes{};
    attributes.target_fd = static_cast<std::uint32_t>(cgroup_fd);
    attributes.attach_bpf_fd = static_cast<std::uint32_t>(program_fd);
    attributes.attach_type = attach_type;
#ifdef BPF_F_ALLOW_MULTI
    attributes.attach_flags = BPF_F_ALLOW_MULTI;
#endif
    int result = static_cast<int>(bpfSyscall(BPF_PROG_ATTACH, &attributes, sizeof(attributes)));
#ifdef BPF_F_ALLOW_MULTI
    // Android kernels may expose BPF_F_ALLOW_MULTI in UAPI while rejecting it for
    // cgroup socket-address hooks. bpf2socks uses the legacy attach API and retries
    // the same operation without the flag for this compatibility case.
    if (result != 0 &&
        (errno == EINVAL || errno == EPERM || errno == ENOTSUP || errno == EOPNOTSUPP)) {
        attributes.attach_flags = 0;
        result = static_cast<int>(bpfSyscall(BPF_PROG_ATTACH, &attributes, sizeof(attributes)));
    }
#endif
    return result;
}

int detachProgram(int cgroup_fd, int program_fd, enum bpf_attach_type attach_type) {
    union bpf_attr attributes{};
    attributes.target_fd = static_cast<std::uint32_t>(cgroup_fd);
    attributes.attach_bpf_fd = static_cast<std::uint32_t>(program_fd);
    attributes.attach_type = attach_type;
    return static_cast<int>(bpfSyscall(BPF_PROG_DETACH, &attributes, sizeof(attributes)));
}

BpfMap& BpfMap::operator=(BpfMap&& other) noexcept {
    if (this != &other) {
        reset(other.fd_);
        other.fd_ = -1;
    }
    return *this;
}

BpfMap::~BpfMap() {
    reset();
}

void BpfMap::reset(int fd) {
    if (fd_ >= 0) {
        close(fd_);
    }
    fd_ = fd;
}

bool isCgroupV2Mount(const char* path) {
    if (path == nullptr || path[0] == '\0') {
        errno = EINVAL;
        return false;
    }
    struct statfs filesystem{};
    if (statfs(path, &filesystem) != 0) {
        return false;
    }
    return static_cast<unsigned long>(filesystem.f_type) == CGROUP2_SUPER_MAGIC;
}

int probeMapCreate() {
    return createMap(BPF_MAP_TYPE_ARRAY, sizeof(std::uint32_t), sizeof(std::uint32_t), 1, 0);
}

bool probeSocketAddressCgroupAttach(const char* path) {
    if (path == nullptr || path[0] == '\0') {
        errno = EINVAL;
        return false;
    }
    const int cgroup_fd = open(path, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (cgroup_fd < 0) return false;

    // Loading a program is not enough on Android: SELinux/capability policy can allow
    // BPF_PROG_LOAD while rejecting BPF_PROG_ATTACH for the target cgroup. Exercise the same
    // attach/detach operation used by the bridge, and leave no program attached on success.
    const struct bpf_insn instructions[] = {
        {static_cast<std::uint8_t>(BPF_ALU64 | BPF_MOV | BPF_K), 0, 0, 0, 1},
        {static_cast<std::uint8_t>(BPF_JMP | BPF_EXIT), 0, 0, 0, 0},
    };
    const enum bpf_attach_type attach_types[] = {
        BPF_CGROUP_INET4_CONNECT,
        BPF_CGROUP_UDP4_SENDMSG,
        BPF_CGROUP_UDP4_RECVMSG,
        BPF_CGROUP_INET6_CONNECT,
        BPF_CGROUP_UDP6_SENDMSG,
        BPF_CGROUP_UDP6_RECVMSG,
    };
    for (const enum bpf_attach_type attach_type : attach_types) {
        const int program_fd = loadProgram(
            instructions,
            sizeof(instructions) / sizeof(instructions[0]),
            "yb_attach_probe",
            BPF_PROG_TYPE_CGROUP_SOCK_ADDR,
            attach_type,
            false);
        if (program_fd < 0) {
            const int saved_errno = errno;
            close(cgroup_fd);
            errno = saved_errno;
            return false;
        }
        if (attachProgram(cgroup_fd, program_fd, attach_type) != 0) {
            const int saved_errno = errno;
            close(program_fd);
            close(cgroup_fd);
            errno = saved_errno;
            return false;
        }
        if (detachProgram(cgroup_fd, program_fd, attach_type) != 0) {
            const int saved_errno = errno;
            close(program_fd);
            close(cgroup_fd);
            errno = saved_errno;
            return false;
        }
        close(program_fd);
    }
    close(cgroup_fd);
    return true;
}

int cleanupSocketAddressPrograms(const char* path) {
    if (path == nullptr || path[0] == '\0') {
        errno = EINVAL;
        return -1;
    }
    const int cgroup_fd = open(path, O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (cgroup_fd < 0) return -1;

    const enum bpf_attach_type attach_types[] = {
        BPF_CGROUP_INET4_CONNECT,
        BPF_CGROUP_UDP4_SENDMSG,
        BPF_CGROUP_UDP4_RECVMSG,
        BPF_CGROUP_INET6_CONNECT,
        BPF_CGROUP_UDP6_SENDMSG,
        BPF_CGROUP_UDP6_RECVMSG,
    };
    std::uint32_t program_ids[64]{};
    for (const enum bpf_attach_type attach_type : attach_types) {
        while (true) {
            union bpf_attr query{};
            query.query.target_fd = static_cast<std::uint32_t>(cgroup_fd);
            query.query.attach_type = attach_type;
            query.query.prog_ids = reinterpret_cast<std::uint64_t>(program_ids);
            query.query.prog_cnt = sizeof(program_ids) / sizeof(program_ids[0]);
            if (bpfSyscall(BPF_PROG_QUERY, &query, sizeof(query)) != 0) {
                const int saved_errno = errno;
                close(cgroup_fd);
                errno = saved_errno;
                return -1;
            }
            const std::uint32_t count = query.query.prog_cnt;
            if (count == 0) break;
            bool removed = false;
            for (std::uint32_t index = 0; index < count; ++index) {
                union bpf_attr get_fd{};
                get_fd.start_id = program_ids[index];
                const int program_fd = static_cast<int>(bpfSyscall(BPF_PROG_GET_FD_BY_ID, &get_fd, sizeof(get_fd)));
                if (program_fd < 0) continue;
                struct bpf_prog_info info{};
                union bpf_attr get_info{};
                get_info.info.bpf_fd = static_cast<std::uint32_t>(program_fd);
                get_info.info.info_len = sizeof(info);
                get_info.info.info = reinterpret_cast<std::uint64_t>(&info);
                const bool is_yumebox =
                    bpfSyscall(BPF_OBJ_GET_INFO_BY_FD, &get_info, sizeof(get_info)) == 0 &&
                    std::strncmp(info.name, "yb_", 3) == 0;
                if (is_yumebox && detachProgram(cgroup_fd, program_fd, attach_type) == 0) {
                    removed = true;
                }
                close(program_fd);
            }
            if (!removed || count < sizeof(program_ids) / sizeof(program_ids[0])) break;
        }
    }
    close(cgroup_fd);
    return 0;
}

}  // namespace yumebox::ebpf
