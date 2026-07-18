// Copyright (c) YumeYuka 2025.
//
// This work is free. You can redistribute it and/or modify it under the
// terms of the Do What The Fuck You Want To Public License, Version 2,
//  as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.

#include "bridge.h"
#include "trace.h"

static void noop_mark_socket(void *tun_interface, int fd) {
    (void)tun_interface;
    (void)fd;
}

static char *noop_query_socket_owner(void *tun_interface, int protocol, const char *source, const char *target) {
    (void)tun_interface;
    (void)protocol;
    (void)source;
    (void)target;
    return NULL;
}

static void noop_complete(void *completable, const char *exception) {
    (void)completable;
    (void)exception;
}

static void noop_complete_with_string(void *completable, const char *result) {
    (void)completable;
    (void)result;
}

static void noop_fetch_report(void *fetch_callback, const char *status_json) {
    (void)fetch_callback;
    (void)status_json;
}

static void noop_fetch_complete(void *fetch_callback, const char *error) {
    (void)fetch_callback;
    (void)error;
}

static int noop_received(void *callback, const char *payload) {
    (void)callback;
    (void)payload;
    return 1;
}

static int noop_received_packed(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed) {
    (void)callback;
    (void)upload_total;
    (void)download_total;
    (void)upload_speed;
    (void)download_speed;
    return 1;
}

static int noop_open_content(const char *url, char *error, int error_length) {
    (void)url;
    (void)error;
    (void)error_length;
    return -1;
}

static void noop_release_object(void *obj) {
    (void)obj;
}

void (*mark_socket_func)(void *tun_interface, int fd) = noop_mark_socket;

char *(*query_socket_owner_func)(void *tun_interface, int protocol, const char *source, const char *target) = noop_query_socket_owner;

void (*complete_func)(void *completable, const char *exception) = noop_complete;

void (*complete_with_string_func)(void *completable, const char *result) = noop_complete_with_string;

void (*fetch_report_func)(void *fetch_callback, const char *status_json) = noop_fetch_report;

void (*fetch_complete_func)(void *fetch_callback, const char *error) = noop_fetch_complete;

int (*logcat_received_func)(void *logcat_interface, const char *payload) = noop_received;

int (*connection_close_received_func)(void *callback, const char *payload) = noop_received;

int (*connection_join_received_func)(void *callback, const char *payload) = noop_received;

int (*traffic_update_received_func)(void *callback, const char *payload) = noop_received;

int (*traffic_update_received_packed_func)(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed) = noop_received_packed;

int (*open_content_func)(const char *url, char *error, int error_length) = noop_open_content;

void (*release_object_func)(void *obj) = noop_release_object;

void set_complete_callback(void (*callback)(void *completable, const char *exception)) {
    complete_func = callback ? callback : noop_complete;
}

void set_complete_with_string_callback(void (*callback)(void *completable, const char *result)) {
    complete_with_string_func = callback ? callback : noop_complete_with_string;
}

void set_release_object_callback(void (*callback)(void *obj)) {
    release_object_func = callback ? callback : noop_release_object;
}

void set_open_content_callback(int (*callback)(const char *url, char *error, int error_length)) {
    open_content_func = callback ? callback : noop_open_content;
}

void set_fetch_report_callback(void (*callback)(void *fetch_callback, const char *status_json)) {
    fetch_report_func = callback ? callback : noop_fetch_report;
}

void set_fetch_complete_callback(void (*callback)(void *fetch_callback, const char *error)) {
    fetch_complete_func = callback ? callback : noop_fetch_complete;
}

void set_logcat_received_callback(int (*callback)(void *logcat_interface, const char *payload)) {
    logcat_received_func = callback ? callback : noop_received;
}

void set_connection_close_received_callback(int (*callback)(void *callback, const char *payload)) {
    connection_close_received_func = callback ? callback : noop_received;
}

void set_connection_join_received_callback(int (*callback)(void *callback, const char *payload)) {
    connection_join_received_func = callback ? callback : noop_received;
}

void set_traffic_update_received_callback(int (*callback)(void *callback, const char *payload)) {
    traffic_update_received_func = callback ? callback : noop_received;
}

void set_traffic_update_received_packed_callback(int (*callback)(void *callback, long long upload_total, long long download_total, long long upload_speed, long long download_speed)) {
    traffic_update_received_packed_func = callback ? callback : noop_received_packed;
}

void set_mark_socket_callback(void (*callback)(void *tun_interface, int fd)) {
    mark_socket_func = callback ? callback : noop_mark_socket;
}

void set_query_socket_owner_callback(char *(*callback)(void *tun_interface, int protocol, const char *source, const char *target)) {
    query_socket_owner_func = callback ? callback : noop_query_socket_owner;
}

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
