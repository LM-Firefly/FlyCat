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

package com.github.yumelira.yumebox.runtime.service.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.github.yumelira.yumebox.runtime.api.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.appContextOrSelf
import com.github.yumelira.yumebox.runtime.service.root.RootTunBinding.Companion.shared
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * libsu [RootService] binding machinery for [RootTunRootService].
 *
 * Everything except the root process runs in the single main process, so [shared] is the one
 * connection every caller (controller, bridge, foreground service) must use — two parallel
 * bindings would race lifecycle and observer registration.
 */
class RootTunBinding {
    companion object {
        private const val BIND_TIMEOUT_MS = 15_000L

        // Declared before `shared`: companion properties initialize top to bottom.
        private val statusObserver =
            object : IRootTunStateObserver.Stub() {
                override fun onStatusChanged(statusJson: String) {
                    runCatching {
                        RootTunStatusFlow.update(rootTunDecode<RootTunStatus>(statusJson))
                    }
                }
            }

        /**
         * The process-wide connection. Status-push observation re-arms on every (re)bind no
         * matter which caller triggered it, replacing the old per-owner duplicate bindings.
         */
        val shared =
            RootTunBinding().apply {
                afterBind = { service ->
                    runCatching { service.registerStateObserver(statusObserver) }
                }
                beforeUnbind = { service ->
                    runCatching { service.unregisterStateObserver(statusObserver) }
                }
            }
    }

    private val mutex = Mutex()
    private val connectionStateLock = Any()

    @Volatile private var binder: IRootTunService? = null

    @Volatile private var connection: ServiceConnection? = null

    /**
     * Invoked once per freshly-established binder (in [onServiceConnected]). Lets the owner
     * (re)register cross-process callbacks such as `IRootTunStateObserver` on every (re)bind.
     */
    @Volatile var afterBind: ((IRootTunService) -> Unit)? = null

    /** Invoked with the current binder right before [disconnect] tears it down. */
    @Volatile var beforeUnbind: ((IRootTunService) -> Unit)? = null

    /** The currently cached binder without forcing a (re)bind; best-effort, may be stale. */
    val currentBinder: IRootTunService?
        get() = binder

    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)? = null,
        block: (IRootTunService) -> T,
    ): T {
        val appContext = context.appContextOrSelf
        return withContext(Dispatchers.IO) {
            try {
                block(bind(appContext))
            } catch (error: Throwable) {
                // fault barrier: root binder call may fail for any reason; invalidate the dead
                // connection and either fall back or rethrow untouched.
                if (RootTunRuntimeRecovery.isBinderConnectionFailure(error)) {
                    invalidateConnection(
                        appContext,
                        RootTunRuntimeRecovery.binderFailureReason(error),
                    )
                    onBinderFailure?.let {
                        return@withContext it()
                    }
                }
                throw error
            }
        }
    }

    suspend fun bind(context: Context): IRootTunService {
        val appContext = context.appContextOrSelf
        cachedBinder(appContext)?.let {
            return it
        }

        return mutex.withLock {
            cachedBinder(appContext)?.let {
                return it
            }

            withTimeout(BIND_TIMEOUT_MS) {
                RootAccessSupport.requireRootTunAccess(appContext)
                awaitBinding(appContext)
            }
        }
    }

    // tryResume/completeResume (internal API) are the only two-phase resume that can hand the
    // race between onServiceConnected and cancellation without leaking a live binding.
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun awaitBinding(appContext: Context): IRootTunService =
        suspendCancellableCoroutine { continuation ->
            val intent = createIntent(appContext)
            val mainHandler = Handler(Looper.getMainLooper())

            val newConnection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val remote = IRootTunService.Stub.asInterface(service)
                        if (remote == null) {
                            invalidateConnection(
                                appContext,
                                "root tun binder is null",
                                this,
                            )
                            resumeWithException(
                                continuation,
                                IllegalStateException("root tun binder is null"),
                            )
                            return
                        }

                        val resumeToken = adoptConnection(this, remote, continuation)
                        if (resumeToken == null) {
                            mainHandler.post { runCatching { RootService.unbind(this) } }
                            return
                        }
                        runCatching { afterBind?.invoke(remote) }
                        continuation.completeResume(resumeToken)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        invalidateConnection(appContext, null, this)
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        invalidateConnection(
                            appContext,
                            "root tun service returned null binding",
                            this,
                        )
                        resumeWithException(
                            continuation,
                            IllegalStateException("root tun service returned null binding"),
                        )
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        invalidateConnection(appContext, "RootTun binding died", this)
                    }
                }

            expectConnection(newConnection)
            continuation.invokeOnCancellation {
                mainHandler.post {
                    clearConnection(newConnection)
                    runCatching { RootService.unbind(newConnection) }
                }
            }

            mainHandler.post {
                runCatching { RootService.bind(intent, newConnection) }
                    .onFailure { error ->
                        clearConnection(newConnection)
                        resumeWithException(continuation, error)
                    }
            }
        }

    suspend fun disconnect() {
        mutex.withLock {
            val current = connection ?: return

            binder?.let { service -> runCatching { beforeUnbind?.invoke(service) } }
            withContext(Dispatchers.Main) { runCatching { RootService.unbind(current) } }
            clearConnection(current)
        }
    }

    fun cachedBinder(context: Context): IRootTunService? {
        val current = binder ?: return null
        if (RootTunRuntimeRecovery.isBinderAlive(current)) {
            return current
        }
        invalidateConnection(context.appContextOrSelf, "RootTun binder cache is dead")
        return null
    }

    fun invalidateConnection(context: Context, reason: String?) {
        val staleConnection = clearCurrentConnection()
        // Unbind the stale ServiceConnection, otherwise libsu keeps it registered for
        // reconnection and repeated failures accumulate dead connections.
        if (staleConnection != null) {
            Handler(Looper.getMainLooper()).post {
                runCatching { RootService.unbind(staleConnection) }
            }
        }
        RootTunRuntimeRecovery.handleBinderGone(context, reason)
    }

    private fun expectConnection(expected: ServiceConnection) {
        synchronized(connectionStateLock) {
            connection = expected
            binder = null
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun adoptConnection(
        expected: ServiceConnection,
        remote: IRootTunService,
        continuation: CancellableContinuation<IRootTunService>,
    ): Any? =
        synchronized(connectionStateLock) {
            if (connection !== expected) return@synchronized null
            val resumeToken = continuation.tryResume(remote)
            if (resumeToken == null) {
                connection = null
                binder = null
                return@synchronized null
            }
            binder = remote
            resumeToken
        }

    private fun clearConnection(expected: ServiceConnection): Boolean =
        synchronized(connectionStateLock) {
            if (connection !== expected) return@synchronized false
            connection = null
            binder = null
            true
        }

    private fun clearCurrentConnection(): ServiceConnection? =
        synchronized(connectionStateLock) {
            val current = connection
            connection = null
            binder = null
            current
        }

    private fun invalidateConnection(
        context: Context,
        reason: String?,
        expected: ServiceConnection,
    ) {
        if (!clearConnection(expected)) return
        Handler(Looper.getMainLooper()).post { runCatching { RootService.unbind(expected) } }
        RootTunRuntimeRecovery.handleBinderGone(context, reason)
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun resumeWithException(
        continuation: CancellableContinuation<IRootTunService>,
        error: Throwable,
    ) {
        val resumeToken = continuation.tryResumeWithException(error) ?: return
        continuation.completeResume(resumeToken)
    }

    fun createIntent(context: Context): Intent = Intent(context, RootTunRootService::class.java)
}
