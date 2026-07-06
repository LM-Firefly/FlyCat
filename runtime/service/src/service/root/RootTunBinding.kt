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
import com.github.yumelira.yumebox.core.appContextOrSelf
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunBindingContract
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.rootTunDecode
import com.github.yumelira.yumebox.service.root.IRootTunService
import com.github.yumelira.yumebox.service.root.IRootTunStateObserver
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * libsu [RootService] binding machinery for [RootTunRootService].
 *
 * Everything except the root process runs in the single main process, so [shared] is the one
 * connection every caller (controller, bridge, foreground service) must use — two parallel
 * bindings would race lifecycle and observer registration.
 */
class RootTunBinding {
    companion object {
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
        cachedBinder(context)?.let {
            return it
        }

        return mutex.withLock {
            cachedBinder(context)?.let {
                return it
            }

            suspendCancellableCoroutine { continuation ->
                val appContext = context.appContextOrSelf
                val intent = createIntent(appContext)
                val mainHandler = Handler(Looper.getMainLooper())

                val newConnection =
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            val remote = IRootTunService.Stub.asInterface(service)
                            if (remote == null) {
                                invalidateConnection(appContext, "root tun binder is null")
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IllegalStateException("root tun binder is null")
                                    )
                                }
                                return
                            }

                            binder = remote
                            connection = this
                            runCatching { afterBind?.invoke(remote) }
                            if (continuation.isActive) {
                                continuation.resume(remote)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            invalidateConnection(appContext, null)
                        }

                        override fun onNullBinding(name: ComponentName?) {
                            invalidateConnection(
                                appContext,
                                "root tun service returned null binding",
                            )
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException("root tun service returned null binding")
                                )
                            }
                        }

                        override fun onBindingDied(name: ComponentName?) {
                            invalidateConnection(appContext, "RootTun binding died")
                        }
                    }

                connection = newConnection
                continuation.invokeOnCancellation {
                    mainHandler.post { runCatching { RootService.unbind(newConnection) } }
                    if (connection === newConnection) {
                        connection = null
                    }
                    if (binder != null && connection == null) {
                        binder = null
                    }
                }

                mainHandler.post {
                    runCatching { RootService.bind(intent, newConnection) }
                        .onFailure { error ->
                            connection = null
                            binder = null
                            if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        }
                }
            }
        }
    }

    suspend fun disconnect() {
        mutex.withLock {
            val current = connection ?: return

            binder?.let { service -> runCatching { beforeUnbind?.invoke(service) } }
            withContext(Dispatchers.Main) { runCatching { RootService.unbind(current) } }
            connection = null
            binder = null
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
        val staleConnection = connection
        binder = null
        connection = null
        // Unbind the stale ServiceConnection, otherwise libsu keeps it registered for
        // reconnection and repeated failures accumulate dead connections.
        if (staleConnection != null) {
            Handler(Looper.getMainLooper()).post {
                runCatching { RootService.unbind(staleConnection) }
            }
        }
        RootTunRuntimeRecovery.handleBinderGone(context, reason)
    }

    fun createIntent(context: Context): Intent = Intent(context, RootTunRootService::class.java)
}

// ── RootTunBindingContractAdapter ───────────────────────────────────────

/**
 * Adapter that wraps [RootTunBinding] to implement [RootTunBindingContract]
 * for use by runtime:client via the contract registry.
 */
class RootTunBindingContractAdapter(
    private val delegate: RootTunBinding,
) : RootTunBindingContract {

    override suspend fun <T> remoteCall(
        context: Context,
        onBinderFailure: (() -> T)?,
        block: (Any) -> T,
    ): T = delegate.remoteCall(context, onBinderFailure) { binder -> block(binder) }

    override suspend fun bind(context: Context): Any = delegate.bind(context)

    override fun cachedBinder(context: Context): Any? = delegate.cachedBinder(context)

    override fun createIntent(context: Context): Intent = delegate.createIntent(context)

    override suspend fun disconnect() = delegate.disconnect()

    override fun afterBind(callback: ((Any) -> Unit)?) {
        delegate.afterBind = callback?.let { cb -> { service: IRootTunService -> cb(service) } }
    }

    override fun beforeUnbind(callback: ((Any) -> Unit)?) {
        delegate.beforeUnbind = callback?.let { cb -> { service: IRootTunService -> cb(service) } }
    }
}
