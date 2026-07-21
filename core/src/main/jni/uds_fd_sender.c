/*
 * UDS fd sender — sends a file descriptor over a Unix domain socket
 * using SCM_RIGHTS ancillary data.
 *
 * This is a minimal JNI helper for Android. It is compiled into
 * libuds_fd_sender.so and loaded by UdsFdSender.kt.
 */

#include <jni.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <android/log.h>

#define TAG "UdsFdSender"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jboolean JNICALL
Java_com_github_yumelira_yumebox_core_uds_UdsFdSender_nativeSendFd(
    JNIEnv *env, jobject thiz, jint socket_fd, jint fd_to_send)
{
    /* We must send at least one byte of regular data. */
    struct msghdr msg;
    struct iovec iov;
    struct cmsghdr *cmsg;
    char buf[1] = {0};
    char control[CMSG_SPACE(sizeof(int))];

    memset(&msg, 0, sizeof(msg));
    memset(control, 0, sizeof(control));

    iov.iov_base = buf;
    iov.iov_len = sizeof(buf);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    msg.msg_control = control;
    msg.msg_controllen = sizeof(control);

    cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(cmsg), &fd_to_send, sizeof(int));

    ssize_t ret = sendmsg(socket_fd, &msg, 0);
    if (ret < 0) {
        LOGE("sendmsg failed: errno=%d (%s)", errno, strerror(errno));
        return JNI_FALSE;
    }

    LOGI("Sent fd %d over socket %d", fd_to_send, socket_fd);
    return JNI_TRUE;
}
