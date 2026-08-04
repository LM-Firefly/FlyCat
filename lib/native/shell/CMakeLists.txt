cmake_minimum_required(VERSION 3.22.1)

project(mihomo-shell C)

add_executable(mihomo-shell shell.c)
set_target_properties(mihomo-shell PROPERTIES OUTPUT_NAME "libmihomo.so")
target_compile_features(mihomo-shell PRIVATE c_std_17)
target_compile_options(mihomo-shell PRIVATE -O3 -fPIE -fvisibility=hidden -Wall -Wextra -Werror)
target_link_options(
    mihomo-shell
    PRIVATE
    -pie
    -Wl,--gc-sections
    -Wl,--build-id=none
    -Wl,-z,relro
    -Wl,-z,now
)
target_link_libraries(mihomo-shell PRIVATE dl)
