// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material.sensor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State

/**
 * 设备倾斜的欧拉角表示，外加重力单位向量在屏幕平面上的投影。
 *
 * @property pitch 绕设备 X 轴的倾斜（弧度）；正值 = 顶部向后倾。
 * @property roll 绕设备 Y 轴的倾斜（弧度）；正值 = 右侧向前倾。
 * @property gravityX 设备坐标系中的重力 X（`+X` = 右）。设备绕屏幕法线旋转时与
 *  `gravityY` 一起扫过单位圆——这是欧拉角会丢失的信息。无传感器时为 `0`。
 * @property gravityY 设备坐标系中的重力 Y（`+Y` = 上）。无传感器时为 `0`。
 */
@Immutable
data class DeviceTilt(
    val pitch: Float,
    val roll: Float,
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
) {

    companion object {

        @Stable
        val Zero: DeviceTilt = DeviceTilt(0f, 0f, 0f, 0f)
    }
}

// FORK（FlyCat）：:ui 为纯 Android 模块（非 MPP）不支持 expect/actual，
// 上游的 rememberDeviceTilt 折叠为普通声明，实现见 DeviceTilt.android.kt。
