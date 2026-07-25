/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

/*
 * libcompat.so — the out-of-process core bridge (the same role as CFA's libcompat.so).
 * A minimal JNI primitives layer, nothing more; it does four things and nothing else:
 *
 *   1. Launch the PIE core as a child process (fork + execve) with a socketpair
 *      control channel handed to it through the CHANNEL=<fd> environment variable.
 *   2. Reap / kill that child (waitpid / kill).
 *   3. Read/write length-delimited control messages on the socketpair, carrying an
 *      optional file descriptor via SCM_RIGHTS (this is how the TUN fd reaches the
 *      core in the non-root VpnService path).
 *   4. Connect to the core's mihomo external controller over a UNIX-domain socket
 *      (abstract namespace supported via a leading '@').
 *
 * Everything else (traffic/proxies/connections/logs/config) is driven over the
 * mihomo REST API on that UNIX socket by the Kotlin layer, NOT through this bridge.
 *
 * Behaviour mirrors the reverse-engineered CFA libcompat.so, returning int[] pairs
 * instead of constructing Java objects from native code. It is deliberately generic
 * (exec / channel / unix socket) so the future root path can reuse it unchanged.
 */

#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>
#include <dirent.h>
#include <android/log.h>

#define TAG "Compat"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern char **environ;

static void throw_io(JNIEnv *env, const char *fmt, ...) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    char message[256];
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);
    jclass exception = (*env)->FindClass(env, "java/io/IOException");
    if (exception != NULL) {
        (*env)->ThrowNew(env, exception, message);
    }
}

// Copy a Java String[] into a NULL-terminated char* array. Caller frees with free_cstr_array.
// On allocation/JNI failure returns NULL. Entries duplicated via strdup so the array outlives
// the JNI local refs (important across fork/execve).
static char **to_cstr_array(JNIEnv *env, jobjectArray array, size_t extra_slots) {
    jsize count = array != NULL ? (*env)->GetArrayLength(env, array) : 0;
    char **result = calloc((size_t)count + extra_slots + 1, sizeof(char *));
    if (result == NULL) {
        return NULL;
    }
    for (jsize i = 0; i < count; i++) {
        jstring element = (jstring)(*env)->GetObjectArrayElement(env, array, i);
        if (element == NULL) {
            result[i] = strdup("");
            continue;
        }
        const char *chars = (*env)->GetStringUTFChars(env, element, NULL);
        result[i] = strdup(chars != NULL ? chars : "");
        if (chars != NULL) {
            (*env)->ReleaseStringUTFChars(env, element, chars);
        }
        (*env)->DeleteLocalRef(env, element);
    }
    return result;
}

static void free_cstr_array(char **array) {
    if (array == NULL) {
        return;
    }
    for (char **it = array; *it != NULL; it++) {
        free(*it);
    }
    free(array);
}

static size_t cstr_array_len(char **array) {
    size_t n = 0;
    if (array != NULL) {
        while (array[n] != NULL) {
            n++;
        }
    }
    return n;
}

/*
 * NativeProcess.nativeStart(path, args, env, workdir) -> int[]{ pid, parentChannelFd }
 *
 * Non-root launch path. Creates a socketpair, forks, and in the child dups /dev/null onto
 * stdio, chdir()s into workdir (the core home), closes every inherited fd except the child
 * channel end, exports CHANNEL=<fd>, and execve()s the PIE core.
 *
 * A CLOEXEC self-pipe carries the child's errno back to the parent: on a successful execve the
 * pipe's write end closes automatically (parent reads EOF); on any pre-exec failure the child
 * writes errno and _exit()s, so the parent throws instead of handing back a pid that is already
 * dead. Without this the service would report "started" while the core had silently failed to
 * exec. The parent keeps fd[0] and returns it alongside the pid.
 */
JNIEXPORT jintArray JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeStart(
        JNIEnv *env, jclass clazz, jstring path_value, jobjectArray args_value,
        jobjectArray env_value, jstring workdir_value) {
    (void)clazz;
    const char *path = (*env)->GetStringUTFChars(env, path_value, NULL);
    if (path == NULL) {
        return NULL;
    }
    if (access(path, R_OK | X_OK) != 0) {
        throw_io(env, "access %s: %s", path, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }
    const char *workdir = workdir_value != NULL ? (*env)->GetStringUTFChars(env, workdir_value, NULL) : NULL;

    // argv = [path, args..., NULL]; envp = [environ..., env..., CHANNEL=<fd>, NULL]
    char **extra_args = to_cstr_array(env, args_value, 0);
    char **extra_env = to_cstr_array(env, env_value, 0);
    if (extra_args == NULL || extra_env == NULL) {
        throw_io(env, "allocate argv/envp");
        free_cstr_array(extra_args);
        free_cstr_array(extra_env);
        if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    // SOCK_SEQPACKET preserves message boundaries (atomic per sendmsg/recvmsg) while still carrying
    // SCM_RIGHTS ancillary fds, so the bridge JSON protocol needs no length framing.
    int fds[2] = {-1, -1};
    if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds) < 0) {
        throw_io(env, "socketpair: %s", strerror(errno));
        free_cstr_array(extra_args);
        free_cstr_array(extra_env);
        if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    // CLOEXEC self-pipe for the child's pre-exec errno (see the function comment).
    int err[2] = {-1, -1};
    if (pipe2(err, O_CLOEXEC) < 0) {
        throw_io(env, "pipe2: %s", strerror(errno));
        close(fds[0]);
        close(fds[1]);
        free_cstr_array(extra_args);
        free_cstr_array(extra_env);
        if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    size_t arg_count = cstr_array_len(extra_args);
    char **argv = calloc(arg_count + 2, sizeof(char *));

    size_t inherited = cstr_array_len(environ);
    size_t injected = cstr_array_len(extra_env);
    char **envp = calloc(inherited + injected + 2, sizeof(char *));
    char channel_env[32];

    if (argv == NULL || envp == NULL) {
        throw_io(env, "allocate argv/envp");
        free(argv);
        free(envp);
        close(fds[0]);
        close(fds[1]);
        close(err[0]);
        close(err[1]);
        free_cstr_array(extra_args);
        free_cstr_array(extra_env);
        if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    argv[0] = (char *)path;
    for (size_t i = 0; i < arg_count; i++) {
        argv[i + 1] = extra_args[i];
    }

    size_t e = 0;
    for (size_t i = 0; i < inherited; i++) {
        envp[e++] = environ[i];
    }
    for (size_t i = 0; i < injected; i++) {
        envp[e++] = extra_env[i];
    }
    snprintf(channel_env, sizeof(channel_env), "CHANNEL=%d", fds[1]);
    envp[e++] = channel_env;
    envp[e] = NULL;

    pid_t pid = fork();
    if (pid < 0) {
        throw_io(env, "fork: %s", strerror(errno));
        free(argv);
        free(envp);
        close(fds[0]);
        close(fds[1]);
        close(err[0]);
        close(err[1]);
        free_cstr_array(extra_args);
        free_cstr_array(extra_env);
        if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    if (pid == 0) {
        // Child. From here on we must not touch the JVM; only async-signal-safe work. Any failure
        // is reported by writing errno to err[1] and _exit()ing; err[1] is CLOEXEC so a successful
        // execve closes it silently.
        int null_fd = open("/dev/null", O_RDWR | O_CLOEXEC);
        if (null_fd < 0) {
            int code = errno;
            (void)write(err[1], &code, sizeof(code));
            _exit(126);
        }
        dup2(null_fd, STDIN_FILENO);

        if (workdir != NULL && chdir(workdir) != 0) {
            int code = errno;
            (void)write(err[1], &code, sizeof(code));
            _exit(126);
        }

        int log_fd = -1;
        if (workdir != NULL) {
            log_fd = open("core.log", O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
        }
        if (log_fd >= 0) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);
            if (log_fd > STDERR_FILENO) {
                close(log_fd);
            }
        } else {
            dup2(null_fd, STDOUT_FILENO);
            dup2(null_fd, STDERR_FILENO);
        }
        if (null_fd > STDERR_FILENO) {
            close(null_fd);
        }

        // Close every inherited descriptor except the child channel end (fds[1]) and the error
        // pipe write end (err[1]) so the core starts with a clean table. Skip the opendir fd itself.
        DIR *dir = opendir("/proc/self/fd");
        if (dir != NULL) {
            int dir_fd = dirfd(dir);
            struct dirent *entry;
            while ((entry = readdir(dir)) != NULL) {
                int fd = (int)strtol(entry->d_name, NULL, 10);
                if (fd >= 3 && fd != fds[1] && fd != dir_fd && fd != err[1]) {
                    close(fd);
                }
            }
            closedir(dir);
        }

        execve(path, argv, envp);
        int code = errno;
        (void)write(err[1], &code, sizeof(code));
        _exit(127);
    }

    // Parent. Drop the child's socketpair end and our copy of the error-pipe write end, then wait
    // for the child to either exec (read returns EOF) or report a pre-exec errno.
    close(fds[1]);
    close(err[1]);
    free(argv);
    free(envp);
    free_cstr_array(extra_args);
    free_cstr_array(extra_env);
    if (workdir) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);

    int child_errno = 0;
    ssize_t got;
    while ((got = read(err[0], &child_errno, sizeof(child_errno))) < 0 && errno == EINTR) {
        // retry
    }
    close(err[0]);

    if (got == (ssize_t)sizeof(child_errno)) {
        // Child died before exec; reap it so it doesn't linger as a zombie, then surface why.
        int status = 0;
        while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
            // retry
        }
        close(fds[0]);
        LOGE("core exec %s failed: %s", path, strerror(child_errno));
        throw_io(env, "exec %s: %s", path, strerror(child_errno));
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, path_value, path);

    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(fds[0]);
        return NULL;
    }
    jint values[2] = {(jint)pid, (jint)fds[0]};
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeKill(
        JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    kill((pid_t)pid, SIGKILL);
}

JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeWait(
        JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    int status = -1;
    while (waitpid((pid_t)pid, &status, 0) < 0 && errno == EINTR) {
        // retry
    }
    return (jint)status;
}

static int validate_channel_arrays(
        JNIEnv *env, jbyteArray buffer, jint offset, jint length, jintArray fd_holder) {
    if (buffer == NULL) {
        throw_io(env, "channel buffer is null");
        return 0;
    }
    jsize buffer_length = (*env)->GetArrayLength(env, buffer);
    if (offset < 0 || length < 0 || offset > buffer_length - length) {
        throw_io(env, "invalid channel buffer slice: offset=%d length=%d size=%d",
                 offset, length, buffer_length);
        return 0;
    }
    if (fd_holder != NULL && (*env)->GetArrayLength(env, fd_holder) < 1) {
        throw_io(env, "channel fd holder is empty");
        return 0;
    }
    return 1;
}

/*
 * Channel.nativeReadMessage(fd, buf, off, len, fdHolder) -> bytes read (0 = EOF, -1 = error)
 * If the peer attached a descriptor via SCM_RIGHTS, it is stored into fdHolder[0].
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Channel_nativeReadMessage(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint offset, jint length, jintArray fd_holder) {
    (void)clazz;
    if (!validate_channel_arrays(env, buffer, offset, length, fd_holder)) {
        return -1;
    }
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) {
        return -1;
    }

    struct iovec iov;
    iov.iov_base = bytes + offset;
    iov.iov_len = (size_t)length;

    union {
        struct cmsghdr align;
        char buf[CMSG_SPACE(sizeof(int))];
    } control;
    memset(&control, 0, sizeof(control));

    struct msghdr message;
    memset(&message, 0, sizeof(message));
    message.msg_iov = &iov;
    message.msg_iovlen = 1;
    message.msg_control = control.buf;
    message.msg_controllen = sizeof(control.buf);

    ssize_t count = recvmsg(fd, &message, 0);
    int saved_errno = errno;

    jint received_fd = -1;
    if (count > 0) {
        for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message); cmsg != NULL;
             cmsg = CMSG_NXTHDR(&message, cmsg)) {
            if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS &&
                cmsg->cmsg_len == CMSG_LEN(sizeof(int))) {
                memcpy(&received_fd, CMSG_DATA(cmsg), sizeof(int));
                break;
            }
        }
    }

    (*env)->ReleaseByteArrayElements(env, buffer, bytes, count > 0 ? 0 : JNI_ABORT);

    if (fd_holder != NULL) {
        (*env)->SetIntArrayRegion(env, fd_holder, 0, 1, &received_fd);
    }

    if (count < 0) {
        throw_io(env, "recvmsg: %s", strerror(saved_errno));
        return -1;
    }
    return (jint)count;
}

/*
 * Channel.nativeWriteMessage(fd, buf, off, len, fdHolder) -> bytes written (-1 = error)
 * If fdHolder[0] >= 0, that descriptor is attached to the message via SCM_RIGHTS.
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Channel_nativeWriteMessage(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint offset, jint length, jintArray fd_holder) {
    (void)clazz;
    if (!validate_channel_arrays(env, buffer, offset, length, fd_holder)) {
        return -1;
    }
    jint pass_fd = -1;
    if (fd_holder != NULL) {
        (*env)->GetIntArrayRegion(env, fd_holder, 0, 1, &pass_fd);
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) {
        return -1;
    }

    struct iovec iov;
    iov.iov_base = bytes + offset;
    iov.iov_len = (size_t)length;

    union {
        struct cmsghdr align;
        char buf[CMSG_SPACE(sizeof(int))];
    } control;
    memset(&control, 0, sizeof(control));

    struct msghdr message;
    memset(&message, 0, sizeof(message));
    message.msg_iov = &iov;
    message.msg_iovlen = 1;

    if (pass_fd >= 0) {
        message.msg_control = control.buf;
        message.msg_controllen = sizeof(control.buf);
        struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        cmsg->cmsg_len = CMSG_LEN(sizeof(int));
        int raw = pass_fd;
        memcpy(CMSG_DATA(cmsg), &raw, sizeof(int));
    }

    ssize_t count = sendmsg(fd, &message, 0);
    int saved_errno = errno;

    (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);

    if (count < 0) {
        throw_io(env, "sendmsg: %s", strerror(saved_errno));
        return -1;
    }
    return (jint)count;
}

/*
 * UnixSocket.nativeConnectUnixSocket(path, type, timeoutMs) -> connected fd
 * A leading '@' selects the abstract namespace (path[0] becomes NUL, matching mihomo/CFA).
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeConnectUnixSocket(
        JNIEnv *env, jclass clazz, jstring path_value, jint type, jint timeout_ms) {
    (void)clazz;
    const char *path = (*env)->GetStringUTFChars(env, path_value, NULL);
    if (path == NULL) {
        return -1;
    }

    int fd = socket(AF_UNIX, type, 0);
    if (fd < 0) {
        throw_io(env, "socket: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return -1;
    }

    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;

    size_t path_len = strlen(path);
    if (path_len >= sizeof(address.sun_path)) {
        path_len = sizeof(address.sun_path) - 1;
    }
    memcpy(address.sun_path, path, path_len);

    socklen_t address_len;
    if (address.sun_path[0] == '@') {
        // Abstract namespace: first byte NUL, no trailing NUL in the length.
        address.sun_path[0] = '\0';
        address_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + path_len);
    } else {
        address_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + path_len + 1);
    }

    int original_flags = -1;
    if (timeout_ms > 0) {
        original_flags = fcntl(fd, F_GETFL, 0);
        if (original_flags < 0 || fcntl(fd, F_SETFL, original_flags | O_NONBLOCK) < 0) {
            int saved_errno = errno;
            throw_io(env, "fcntl: %s", strerror(saved_errno));
            close(fd);
            (*env)->ReleaseStringUTFChars(env, path_value, path);
            return -1;
        }
    }

    int connect_error = 0;
    if (connect(fd, (struct sockaddr *)&address, address_len) < 0) {
        connect_error = errno;
        if (timeout_ms > 0 && connect_error == EINPROGRESS) {
            struct pollfd poll_fd = {.fd = fd, .events = POLLOUT, .revents = 0};
            int poll_result;
            do {
                poll_result = poll(&poll_fd, 1, timeout_ms);
            } while (poll_result < 0 && errno == EINTR);

            if (poll_result == 0) {
                connect_error = ETIMEDOUT;
            } else if (poll_result < 0) {
                connect_error = errno;
            } else {
                socklen_t error_len = sizeof(connect_error);
                if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &connect_error, &error_len) < 0) {
                    connect_error = errno;
                }
            }
        }
    }

    if (original_flags >= 0 && fcntl(fd, F_SETFL, original_flags) < 0 && connect_error == 0) {
        connect_error = errno;
    }
    if (connect_error != 0) {
        throw_io(env, "connect: %s", strerror(connect_error));
        close(fd);
        (*env)->ReleaseStringUTFChars(env, path_value, path);
        return -1;
    }

    (*env)->ReleaseStringUTFChars(env, path_value, path);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeSetSoTimeout(
        JNIEnv *env, jclass clazz, jint fd, jint timeout_ms) {
    (void)clazz;
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0 ||
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) {
        throw_io(env, "setsockopt: %s", strerror(errno));
    }
}

JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeGetSoTimeout(
        JNIEnv *env, jclass clazz, jint fd) {
    (void)clazz;
    struct timeval tv;
    socklen_t len = sizeof(tv);
    if (getsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, &len) < 0) {
        throw_io(env, "getsockopt: %s", strerror(errno));
        return -1;
    }
    return (jint)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
}
