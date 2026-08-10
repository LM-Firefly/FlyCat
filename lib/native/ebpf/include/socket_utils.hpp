#pragma once

#include <cstdint>

#include <fcntl.h>
#include <sys/epoll.h>
#include <time.h>
#include <unistd.h>

namespace yumebox::ebpf {

inline bool addEpoll(int epoll_fd, int fd, std::uint32_t events, void* pointer) {
    epoll_event event{};
    event.events = events;
    event.data.ptr = pointer;
    return epoll_ctl(epoll_fd, EPOLL_CTL_ADD, fd, &event) == 0;
}

inline bool modifyEpoll(int epoll_fd, int fd, std::uint32_t events, void* pointer) {
    epoll_event event{};
    event.events = events;
    event.data.ptr = pointer;
    return epoll_ctl(epoll_fd, EPOLL_CTL_MOD, fd, &event) == 0;
}

inline void closeFd(int* fd) {
    if (fd != nullptr && *fd >= 0) {
        close(*fd);
        *fd = -1;
    }
}

inline std::uint64_t monotonicMs() {
    timespec value{};
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) return 0;
    return static_cast<std::uint64_t>(value.tv_sec) * 1000U +
        static_cast<std::uint64_t>(value.tv_nsec) / 1'000'000U;
}

inline bool setNonBlocking(int fd) {
    const int flags = fcntl(fd, F_GETFL, 0);
    return flags >= 0 && fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0;
}

}  // namespace yumebox::ebpf
