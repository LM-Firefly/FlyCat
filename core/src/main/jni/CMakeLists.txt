cmake_minimum_required(VERSION 3.22)
project(uds_fd_sender C)

add_library(uds_fd_sender SHARED uds_fd_sender.c)

target_link_libraries(uds_fd_sender log)
