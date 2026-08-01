// Copyright (c) YumeYuka 2025.
//
// This work is free. You can redistribute it and/or modify it under the
// terms of the Do What The Fuck You Want To Public License, Version 2,
//  as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.

#include "bridge.h"
#include "trace.h"

void (*mark_socket_func)(void *tun_interface, int fd);

char *(*query_socket_owner_func)(void *tun_interface, int protocol, const char *source, const char *target);

void (*complete_func)(void *completable, const char *exception);

void (*complete_with_string_func)(void *completable, const char *result);

void (*fetch_report_func)(void *fetch_callback, const char *status_json);

void (*fetch_complete_func)(void *fetch_callback, const char *error);

int (*logcat_received_func)(void *logcat_interface, const char *payload);

int (*connection_close_received_func)(void *callback, const char *payload);

int (*connection_join_received_func)(void *callback, const char *payload);

int (*traffic_update_received_func)(void *callback, const char *payload);

int (*traffic_update_received_packed_func)(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed);

int (*open_content_func)(const char *url, char *error, int error_length);

void (*release_object_func)(void *obj);

void mark_socket(void *interface, int fd) {
    TRACE_METHOD();

    mark_socket_func(interface, fd);
}

char *query_socket_owner(void *interface, int protocol, char *source, char *target) {
    TRACE_METHOD();

    char *result = query_socket_owner_func(interface, protocol, source, target);

    free(source);
    free(target);

    return result;
}

void complete(void *obj, char *error) {
    TRACE_METHOD();

    complete_func(obj, error);

    free(error);
}

void complete_with_string(void *obj, char *result) {
TRACE_METHOD();

complete_with_string_func(obj, result);

free(result);
}

void fetch_complete(void *fetch_callback, char *exception) {
    TRACE_METHOD();

    fetch_complete_func(fetch_callback, exception);

    free(exception);
}

void fetch_report(void *fetch_callback, char *json_status) {
    TRACE_METHOD();

    fetch_report_func(fetch_callback, json_status);

    free(json_status);
}

int logcat_received(void *logcat_interface, char *payload) {
    TRACE_METHOD();

    int result = logcat_received_func(logcat_interface, payload);

    free(payload);

    return result;
}

int connection_close_received(void *callback, char *payload) {
    TRACE_METHOD();

    int result = connection_close_received_func(callback, payload);

    free(payload);

    return result;
}

int connection_join_received(void *callback, char *payload) {
    TRACE_METHOD();

    int result = connection_join_received_func(callback, payload);

    free(payload);

    return result;
}

int traffic_update_received(void *callback, char *payload) {
    TRACE_METHOD();

    int result = traffic_update_received_func(callback, payload);

    free(payload);

    return result;
}

int traffic_update_received_packed(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed) {
    TRACE_METHOD();

    return traffic_update_received_packed_func(
            callback,
            upload_total,
            download_total,
            upload_speed,
            download_speed);
}

int open_content(char *url, char *error, int error_length) {
    TRACE_METHOD();

    int result = open_content_func(url, error, error_length);

    free(url);

    return result;
}

void release_object(void *obj) {
    TRACE_METHOD();

    release_object_func(obj);
}
