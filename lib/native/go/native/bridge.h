// Copyright (c) YumeYuka 2025.
//
// This work is free. You can redistribute it and/or modify it under the
// terms of the Do What The Fuck You Want To Public License, Version 2,
//  as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.

#pragma once

#include <stddef.h>
#include <stdint.h>
#include <malloc.h>
#include <android/log.h>

#define TAG "FlyCat"

typedef const char *c_string;

extern void (*mark_socket_func)(void *tun_interface, int fd);

extern char *(*query_socket_owner_func)(void *tun_interface, int protocol, const char *source, const char *target);

extern void (*complete_func)(void *completable, const char *exception);

extern void (*complete_with_string_func)(void *completable, const char *result);

extern void (*fetch_report_func)(void *fetch_callback, const char *status_json);

extern void (*fetch_complete_func)(void *fetch_callback, const char *error);

extern int (*logcat_received_func)(void *logcat_interface, const char *payload);

extern int (*connection_close_received_func)(void *callback, const char *payload);

extern int (*connection_join_received_func)(void *callback, const char *payload);

extern int (*traffic_update_received_func)(void *callback, const char *payload);

extern int (*traffic_update_received_packed_func)(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed);

extern void (*release_object_func)(void *obj);

extern int (*open_content_func)(const char *url, char *error, int error_length);

// Optional callback registration helpers for non-C++ bridge owners.
extern void set_complete_callback(void (*callback)(void *completable, const char *exception));
extern void set_complete_with_string_callback(void (*callback)(void *completable, const char *result));
extern void set_release_object_callback(void (*callback)(void *obj));
extern void set_open_content_callback(int (*callback)(const char *url, char *error, int error_length));
extern void set_fetch_report_callback(void (*callback)(void *fetch_callback, const char *status_json));
extern void set_fetch_complete_callback(void (*callback)(void *fetch_callback, const char *error));
extern void set_logcat_received_callback(int (*callback)(void *logcat_interface, const char *payload));
extern void set_connection_close_received_callback(int (*callback)(void *callback, const char *payload));
extern void set_connection_join_received_callback(int (*callback)(void *callback, const char *payload));
extern void set_traffic_update_received_callback(int (*callback)(void *callback, const char *payload));
extern void set_traffic_update_received_packed_callback(int (*callback)(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed));
extern void set_mark_socket_callback(void (*callback)(void *tun_interface, int fd));
extern void set_query_socket_owner_callback(char *(*callback)(void *tun_interface, int protocol, const char *source, const char *target));

// cgo
extern void mark_socket(void *interface, int fd);

extern char *query_socket_owner(void *interface, int protocol, char *source, char *target);

extern void complete(void *obj, char *error);

extern void complete_with_string(void *obj, char *result);

extern void fetch_complete(void *completable, char *exception);

extern void fetch_report(void *fetch_callback, char *status_json);

extern int logcat_received(void *logcat_interface, char *payload);

extern int connection_close_received(void *callback, char *payload);

extern int connection_join_received(void *callback, char *payload);

extern int traffic_update_received(void *callback, char *payload);

extern int traffic_update_received_packed(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed);

extern void release_object(void *obj);

extern int open_content(char *url, char *error, int error_length);
