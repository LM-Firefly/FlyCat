package com.github.yumelira.yumebox.screen.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.yumelira.yumebox.platform.util.formatSpeed
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.Navigator
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.`Chart-column`
import com.github.yumelira.yumebox.presentation.icon.yume.`Package-check`
import com.github.yumelira.yumebox.presentation.icon.yume.Bolt
import com.github.yumelira.yumebox.presentation.icon.yume.CircleCheckBig
import com.github.yumelira.yumebox.presentation.icon.yume.Github
import com.github.yumelira.yumebox.presentation.icon.yume.Play
import com.github.yumelira.yumebox.presentation.icon.yume.Tun
import com.github.yumelira.yumebox.presentation.navigation.Route
import com.github.yumelira.yumebox.util.showToast
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object HomeCardTokens {
    const val topCardCorner = 20
    val topCardHorizontal = 18.dp
    val statusCardVertical = 20.dp
    val topEntryCardVertical = 14.dp
    val topCardGap = 14.dp
    val topCardGroupGap = 8.dp
    val smallCardGroupGap = 6.dp
    val topIconSize = 22.dp
    val smallCardIconSlot = 28.dp
    val smallCardVertical = 9.dp
    val smallCardMinHeight = 46.dp
    val smallArrowWidth = 16.dp
}

@Composable
fun HomeScreen(navigator: Navigator) {
    val viewModel = koinViewModel<HomeViewModel>()
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val controlState by viewModel.controlState.collectAsStateWithLifecycle()
    val selectedServerName by viewModel.selectedServerName.collectAsStateWithLifecycle()
    val trafficData by viewModel.trafficData.collectAsStateWithLifecycle()

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
        }

    LaunchedEffect(Unit) { viewModel.messages.collect { context.showToast(it) } }

    LaunchedEffect(Unit) {
        viewModel.vpnPrepareIntent.collect { intent -> vpnPermissionLauncher.launch(intent) }
    }

    DisposableEffect(viewModel) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    val trafficText =
        when (controlState) {
            HomeControlState.Running -> {
                "下行 ${formatSpeed(trafficData.download)} · 上行 ${formatSpeed(trafficData.upload)}"
            }

            HomeControlState.Connecting -> "正在建立 VPN 通道"
            HomeControlState.Disconnecting -> "正在断开 VPN 通道"
            HomeControlState.Idle -> "点击启动 VPN"
        }
    var lastProxyName by remember { mutableStateOf<String?>(selectedServerName) }
    LaunchedEffect(controlState, selectedServerName) {
        if (controlState == HomeControlState.Running && !selectedServerName.isNullOrBlank()) {
            lastProxyName = selectedServerName
        }
    }

    val proxySummary =
        when {
            !selectedServerName.isNullOrBlank() -> selectedServerName!!
            controlState == HomeControlState.Connecting -> "连接中"
            controlState == HomeControlState.Disconnecting -> lastProxyName ?: "断开中"
            else -> lastProxyName ?: "未连接"
        }
    val showProxyCard = controlState != HomeControlState.Idle

    Scaffold(topBar = { TopBar(title = "FlyCat Lite", scrollBehavior = scrollBehavior) }) {
        innerPadding ->
        val pagePadding = combinePaddingValues(innerPadding, rememberStandalonePageMainPadding())
        ScreenLazyColumn(
            scrollBehavior = scrollBehavior,
            innerPadding = pagePadding,
            contentPadding =
                PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = pagePadding.calculateTopPadding(),
                    bottom = pagePadding.calculateBottomPadding(),
                ),
        ) {
            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Column {
                    StatusCard(
                        controlState = controlState,
                        trafficText = trafficText,
                        onToggle = {
                            when (controlState) {
                                HomeControlState.Running -> viewModel.stopProxy()
                                HomeControlState.Idle -> viewModel.startProxy()
                                HomeControlState.Connecting,
                                HomeControlState.Disconnecting -> Unit
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(HomeCardTokens.topCardGroupGap))
                    AnimatedVisibility(
                        visible = showProxyCard,
                        enter =
                            fadeIn(animationSpec = tween(220)) +
                                expandVertically(animationSpec = tween(220)) +
                                slideInVertically(
                                    initialOffsetY = { -it / 4 },
                                    animationSpec = tween(220),
                                ),
                        exit =
                            shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = tween(180),
                            ) + fadeOut(animationSpec = tween(120)),
                    ) {
                        Column {
                            TopEntryCard(
                                title = "代理",
                                icon = Yume.Tun,
                                endText = proxySummary,
                                enabled = true,
                                onClick = { navigator.push(Route.Nodes) },
                            )
                            Spacer(modifier = Modifier.height(HomeCardTokens.topCardGroupGap))
                        }
                    }
                    TopEntryCard(
                        title = "配置",
                        icon = Yume.`Package-check`,
                        endText = currentProfile?.name ?: "未配置",
                        onClick = { navigator.push(Route.ImportConfig()) },
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(HomeCardTokens.smallCardGroupGap),
                ) {
                    ArrowCard(
                        title = "设置",
                        icon = Yume.Bolt,
                        onClick = { navigator.push(Route.VpnSettings) },
                    )
                    ArrowCard(
                        title = "日志",
                        icon = Yume.`Chart-column`,
                        onClick = { navigator.push(Route.Log) },
                    )
                    ArrowCard(
                        title = "关于",
                        icon = Yume.Github,
                        onClick = { navigator.push(Route.About) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusCard(controlState: HomeControlState, trafficText: String, onToggle: () -> Unit) {
    val isRunning = controlState == HomeControlState.Running
    val background =
        if (isRunning) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onBackground
        }
    val contentColor =
        if (isRunning) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.background
        }
    val secondaryColor = contentColor.copy(alpha = 0.78f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        applyHorizontalPadding = false,
        cornerRadius = HomeCardTokens.topCardCorner,
        colors = CardDefaults.defaultColors(color = background),
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 72.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle,
                    )
                    .padding(
                        horizontal = HomeCardTokens.topCardHorizontal,
                        vertical = HomeCardTokens.statusCardVertical,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeCardTokens.topCardGap),
        ) {
            Box(
                modifier = Modifier.size(HomeCardTokens.topIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRunning) Yume.CircleCheckBig else Yume.Play,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = statusTitle(controlState),
                    color = contentColor,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trafficText,
                    color = secondaryColor,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TopEntryCard(
    title: String,
    icon: ImageVector,
    endText: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val titleColor =
        if (enabled) {
            MiuixTheme.colorScheme.onBackground
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        }
    val endColor =
        if (enabled) {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        applyHorizontalPadding = false,
        cornerRadius = HomeCardTokens.topCardCorner,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 55.dp)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(
                        horizontal = HomeCardTokens.topCardHorizontal,
                        vertical = HomeCardTokens.topEntryCardVertical,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeCardTokens.topCardGap),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor,
                modifier = Modifier.size(HomeCardTokens.topIconSize),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = titleColor,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = endText,
                modifier = Modifier.widthIn(max = 132.dp),
                color = endColor,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArrowCard(
    title: String,
    icon: ImageVector,
    endText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        applyHorizontalPadding = false,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
    ) {
        ArrowPreference(
            title = title,
            titleColor = BasicComponentDefaults.titleColor(),
            startAction = {
                Box(
                    modifier = Modifier.width(HomeCardTokens.smallCardIconSlot),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint =
                            if (enabled) {
                                MiuixTheme.colorScheme.onBackground
                            } else {
                                MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            endActions = {
                if (!endText.isNullOrBlank()) {
                    Text(
                        text = endText,
                        modifier = Modifier.widthIn(max = 140.dp),
                        color =
                            if (enabled) {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            } else {
                                MiuixTheme.colorScheme.disabledOnSecondaryVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            enabled = enabled,
            onClick = onClick,
        )
    }
}

private fun statusTitle(controlState: HomeControlState): String {
    return when (controlState) {
        HomeControlState.Idle -> "点击启动"
        HomeControlState.Connecting -> "启动中"
        HomeControlState.Running -> "运行中"
        HomeControlState.Disconnecting -> "停止中"
    }
}
