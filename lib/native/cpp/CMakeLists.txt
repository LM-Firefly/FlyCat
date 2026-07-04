cmake_minimum_required(VERSION 3.22.1)

if(POLICY CMP0057)
    cmake_policy(SET CMP0057 NEW)
endif()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(GIT_COMMIT_HASH "unknown")
set(GIT_BRANCH "unknown")
set(GIT_SUFFIX "")
set(BUILD_TIMESTAMP "")

if(DEFINED GIT_INFO_FILE AND EXISTS "${GIT_INFO_FILE}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${GIT_INFO_FILE}")
    file(STRINGS "${GIT_INFO_FILE}" GIT_INFO_LINES)
    foreach(GIT_INFO_LINE ${GIT_INFO_LINES})
        if(GIT_INFO_LINE MATCHES "^([^=]+)=(.*)$")
            set(${CMAKE_MATCH_1} "${CMAKE_MATCH_2}")
        endif()
    endforeach()
endif()

message(STATUS "Git Hash: ${GIT_COMMIT_HASH}")
message(STATUS "Git Branch: ${GIT_BRANCH}")
message(STATUS "Git Suffix: ${GIT_SUFFIX}")
message(STATUS "Build Timestamp: ${BUILD_TIMESTAMP}")

if("${BUILD_TIMESTAMP}" STREQUAL "")
    set(GIT_VERSION "${GIT_BRANCH}${GIT_SUFFIX}-${GIT_COMMIT_HASH}")
else()
    set(GIT_VERSION "${GIT_BRANCH}${GIT_SUFFIX}-${GIT_COMMIT_HASH}-${BUILD_TIMESTAMP}")
endif()

string(REGEX REPLACE "[ ]+" "" GIT_VERSION "${GIT_VERSION}")
message(STATUS "Version Info: ${GIT_VERSION}")

configure_file("${CMAKE_CURRENT_SOURCE_DIR}/version.h.in" "${CMAKE_CURRENT_BINARY_DIR}/version.h" @ONLY)

set(YUMEBOX_LINKER_FLAG_LIST "")
if(DEFINED YUMEBOX_LINKER_FLAGS AND NOT "${YUMEBOX_LINKER_FLAGS}" STREQUAL "")
    separate_arguments(YUMEBOX_LINKER_FLAG_LIST NATIVE_COMMAND "${YUMEBOX_LINKER_FLAGS}")
endif()

project(clash-bridge C CXX)

set(CMAKE_POSITION_INDEPENDENT_CODE on)
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3")

set(GO_OUTPUT_BASE "${GO_OUTPUT}/${FLAVOR_NAME}/${CMAKE_ANDROID_ARCH_ABI}")
if(NOT DEFINED FLAVOR_NAME OR "${FLAVOR_NAME}" STREQUAL "" OR NOT EXISTS("${GO_OUTPUT_BASE}"))
    set(GO_OUTPUT_BASE "${GO_OUTPUT}/${CMAKE_ANDROID_ARCH_ABI}")
    message(STATUS "Using shared Golang output folder: ${GO_OUTPUT_BASE}")
endif()

include_directories("${GO_OUTPUT_BASE}")
include_directories("${GO_SOURCE}")
include_directories("${CMAKE_CURRENT_BINARY_DIR}")
include_directories("${CMAKE_CURRENT_SOURCE_DIR}")

link_directories("${GO_OUTPUT_BASE}")

add_library(bridge SHARED main.cpp bridge_callbacks.cpp override_compile.cpp jni_helper.cpp bridge_helper.cpp jni_raii.cpp)
target_link_libraries(bridge log clash)
if(YUMEBOX_LINKER_FLAG_LIST)
    target_link_options(bridge PRIVATE ${YUMEBOX_LINKER_FLAG_LIST})
endif()
