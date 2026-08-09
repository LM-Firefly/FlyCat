#pragma once

#include <cstddef>
#include <cstdint>

#include <linux/bpf.h>

namespace yumebox::ebpf {

long bpfSyscall(enum bpf_cmd command, union bpf_attr* attributes, unsigned int size);

int createMap(
    enum bpf_map_type type,
    std::uint32_t key_size,
    std::uint32_t value_size,
    std::uint32_t max_entries,
    std::uint32_t flags);

int updateMap(int map_fd, const void* key, const void* value, std::uint64_t flags = BPF_ANY);
int lookupMap(int map_fd, const void* key, void* value);
int deleteMap(int map_fd, const void* key);

int loadProgram(
    const struct bpf_insn* instructions,
    std::size_t instruction_count,
    const char* name,
    enum bpf_prog_type program_type,
    enum bpf_attach_type expected_attach_type,
    bool log_errors = true);
int attachProgram(int cgroup_fd, int program_fd, enum bpf_attach_type attach_type);
int detachProgram(int cgroup_fd, int program_fd, enum bpf_attach_type attach_type);

class BpfMap final {
public:
    BpfMap() = default;
    explicit BpfMap(int fd) : fd_(fd) {}
    BpfMap(const BpfMap&) = delete;
    BpfMap& operator=(const BpfMap&) = delete;
    BpfMap(BpfMap&& other) noexcept : fd_(other.fd_) { other.fd_ = -1; }
    BpfMap& operator=(BpfMap&& other) noexcept;
    ~BpfMap();

    [[nodiscard]] int fd() const { return fd_; }
    [[nodiscard]] bool valid() const { return fd_ >= 0; }
    void reset(int fd = -1);

private:
    int fd_ = -1;
};

bool isCgroupV2Mount(const char* path);
int probeMapCreate();
bool probeSocketAddressCgroupAttach(const char* path);
int cleanupSocketAddressPrograms(const char* path);

}  // namespace yumebox::ebpf
