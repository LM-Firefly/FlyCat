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

package com.github.yumelira.yumebox.runtime.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.github.yumelira.yumebox.core.util.AutoStartSessionGate
import com.github.yumelira.yumebox.core.util.PollingTimerSpecs
import com.github.yumelira.yumebox.core.util.PollingTimers
import com.github.yumelira.yumebox.data.model.RunMode
import com.github.yumelira.yumebox.data.store.MMKVProvider
import com.github.yumelira.yumebox.data.store.NetworkSettingsStore
import com.github.yumelira.yumebox.data.store.RemoteControllerStore
import com.github.yumelira.yumebox.runtime.api.*
import com.github.yumelira.yumebox.runtime.service.core.CoreProcess
import com.github.yumelira.yumebox.runtime.service.profile.ProfileManager
import com.github.yumelira.yumebox.runtime.service.session.RootSessionLauncher
import com.github.yumelira.yumebox.runtime.service.session.RuntimeServiceLauncher
import com.github.yumelira.yumebox.runtime.service.util.sendBroadcastSelf
import kotlinx.coroutines.*
import tf.gal.yumebox.locale.YumeTxt
import timber.log.Timber

@SuppressLint("NewApi")
class ProxyTileService : TileService() {
    private val profileManager by lazy { ProfileManager(applicationContext) }
    private val networkSettingsStorage by lazy {
        NetworkSettingsStore(MMKVProvider().getMMKV("network_settings"))
    }
    private val tileLabelText: String by lazy {
        applicationInfo.loadLabel(packageManager).toString().ifBlank { "YumeBox" }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private var toggleJob: Job? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateJob?.cancel()
        updateJob = scope.launch {
            // Refresh once up front: requestListeningState() (fired on every start/stop) only opens a
            // brief listening window, so we must update immediately rather than wait for the first tick.
            updateTileStateFromRuntime()
            PollingTimers.ticks(PollingTimerSpecs.ProxyTileRefresh).collect {
                updateTileStateFromRuntime()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        updateJob?.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onClick() {
        super.onClick()
        if (toggleJob?.isActive == true) return

        toggleJob = scope.launch {
            if (RemoteControllerStore.isActive()) {
                updateTileState(true)
                return@launch
            }
            val snapshot = withContext(Dispatchers.IO) { currentSnapshot() }
            val isActive = snapshot.phase.isActiveOrStopping
            val currentMode = effectiveMode(snapshot)

            // If the tile visual state is stale vs the actual runtime state, sync it
            // immediately but still perform the user's requested action — the user's tap
            // is their intent to toggle, not just to reconcile state.
            val tileState = qsTile?.state
            val tileStaleInactive = isActive && tileState == Tile.STATE_INACTIVE
            val tileStaleActive = !isActive && tileState == Tile.STATE_ACTIVE
            if (tileStaleInactive || tileStaleActive) {
                updateTileState(isActive)
            }

            try {
                if (isActive) {
                    AutoStartSessionGate.markManualPaused()
                    updateTilePendingState(isStarting = false)
                    withContext(Dispatchers.IO) { stopLocalRuntime() }
                } else {
                    val activeProfile = withContext(Dispatchers.IO) { profileManager.queryActive() }
                    if (activeProfile == null) {
                        updateTileInactiveState(subtitle = YumeTxt.Service.Tile.ClickToOpen)

                        val intent =
                            Intent(Intent.ACTION_MAIN).apply {
                                component = Components.MAIN_ACTIVITY
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        startActivityAndCollapseCompat(intent, requestCode = 1001)
                        return@launch
                    }

                    updateTilePendingState(isStarting = true)
                    when (currentMode) {
                        RunMode.VpnService -> {
                            val vpnIntent = VpnService.prepare(this@ProxyTileService)
                            if (vpnIntent != null) {
                                updateTileInactiveState(subtitle = YumeTxt.Service.Tile.ClickToOpen)
                                vpnIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivityAndCollapseCompat(vpnIntent, requestCode = 1002)
                                return@launch
                            }

                            RuntimeServiceLauncher.start(
                                this@ProxyTileService,
                                RunMode.VpnService,
                                RuntimeServiceLauncher.SOURCE_TILE,
                            )
                        }
                        RunMode.Tun,
                        RunMode.Tproxy -> {
                            withContext(Dispatchers.IO) {
                                RootSessionLauncher.start(this@ProxyTileService, currentMode)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                // fault barrier: toggle spans root bridge / service start; the tile must recover
                // to the real runtime state instead of crashing the SystemUI-bound service.
                Timber.e(error, "Error toggling proxy from tile")
            } finally {
                PollingTimers.awaitTick(
                    PollingTimerSpecs.dynamic(
                        name = "proxy_tile_toggle_state_sync",
                        intervalMillis = 300L,
                        initialDelayMillis = 300L,
                    )
                )
                updateTileStateFromRuntime()
            }
        }
    }

    private suspend fun updateTileStateFromRuntime() {
        updateTileState(withContext(Dispatchers.IO) { currentSnapshot() }.phase.isActiveOrStopping)
    }
    private fun currentSnapshot(): RuntimeSnapshot {
        val configuredMode = networkSettingsStorage.runMode.value
        val vpnPhase = StatusProvider.queryRuntimePhase(RunMode.VpnService)
        val owner =
            when {
                vpnPhase != RuntimePhase.Idle -> RuntimeOwner.VpnService
                CoreProcess.isRootDaemonAlive() -> RuntimeOwner.RootDaemon
                else -> RuntimeOwner.None
            }

        return if (owner == RuntimeOwner.None) {
            RuntimeSnapshot(
                owner = RuntimeOwner.None,
                phase = RuntimePhase.Idle,
                runMode = configuredMode,
            )
        } else {
            RuntimeSnapshot(
                owner = owner,
                phase =
                    when (owner) {
                        RuntimeOwner.VpnService -> vpnPhase
                        RuntimeOwner.RootDaemon -> RuntimePhase.Running
                        RuntimeOwner.RemoteController -> RuntimePhase.Running
                        RuntimeOwner.None -> RuntimePhase.Idle
                    },
                // VpnService owner is always the VPN mode. A root daemon can outlive a settings
                // change, so use its persisted mode instead of the current selection.
                runMode =
                    if (owner == RuntimeOwner.VpnService) RunMode.VpnService
                    else CoreProcess.rootDaemonMode() ?: configuredMode,
            )
        }
    }

    // Mirrors the home-screen stop path: broadcast a stop, tear down the VPN service, and — since the
    // root daemon isn't a service — explicitly stop it (this is a deliberate user stop).
    private fun stopLocalRuntime() {
        runCatching { sendBroadcastSelf(Intent(Intents.ACTION_CLASH_REQUEST_STOP)) }
        runCatching {
            applicationContext.stopService(Intent(applicationContext, TunService::class.java))
        }
        runCatching { RootSessionLauncher.stop(applicationContext) }
    }

    private fun effectiveMode(snapshot: RuntimeSnapshot): RunMode = snapshot.runMode

    private fun updateTileState(isRunning: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        tile.label = tileLabelText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                if (isRunning) {
                    YumeTxt.Service.Tile.ClickToStopProxy
                } else {
                    YumeTxt.Service.Tile.ClickToStartProxy
                }
        }

        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_service)

        tile.updateTile()
    }

    private fun updateTilePendingState(isStarting: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isStarting) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = tileLabelText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                if (isStarting) {
                    YumeTxt.Service.Tile.Connecting
                } else {
                    YumeTxt.Service.Tile.Disconnecting
                }
        }

        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_service)
        tile.updateTile()
    }

    private fun updateTileInactiveState(subtitle: String) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = tileLabelText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }

        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_service)
        tile.updateTile()
    }

    private fun startActivityAndCollapseCompat(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent =
                PendingIntent.getActivity(this, requestCode, intent, pendingIntentFlags)
            startActivityAndCollapse(pendingIntent)
            return
        }

        @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
