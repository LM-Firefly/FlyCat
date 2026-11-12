// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.material.sensor.rememberDeviceTilt

/**
 * 返回 UV 位置按设备实时倾斜偏移后的 [base]，使描边边缘跟随设备朝向。
 * 没有倾斜传感器的平台保持光源在 [base]。[sensitivity] 是每弧度倾斜对应的描边边界偏移比例。
 */
@Composable
fun rememberTiltLight(
    base: EdgeLightSource,
    @FloatRange(from = 0.0) sensitivity: Float = 0.1f,
): EdgeLightSource {
    val tilt by rememberDeviceTilt()
    return remember(tilt, base, sensitivity) {
        base.copy(
            x = base.x + sensitivity * tilt.roll,
            y = base.y - sensitivity * tilt.pitch,
        )
    }
}
