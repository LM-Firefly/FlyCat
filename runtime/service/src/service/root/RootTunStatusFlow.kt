package com.github.yumelira.yumebox.runtime.service.root

import android.content.Context
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatus
import com.github.yumelira.yumebox.runtime.api.service.root.RootTunStatusFlow as ApiRootTunStatusFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin delegation to the canonical RootTunStatusFlow in runtime:api.
 * Kept in runtime:service so that existing intra-service references
 * (e.g. RootTunRuntimeRecovery) continue to compile without import changes.
 */
object RootTunStatusFlow {
    val flow: StateFlow<RootTunStatus> get() = ApiRootTunStatusFlow.flow

    fun ensureSeeded(context: Context) = ApiRootTunStatusFlow.ensureSeeded(context)

    fun current(context: Context): RootTunStatus = ApiRootTunStatusFlow.current(context)

    fun update(status: RootTunStatus) = ApiRootTunStatusFlow.update(status)

    fun markIdle(error: String? = null) = ApiRootTunStatusFlow.markIdle(error)
}