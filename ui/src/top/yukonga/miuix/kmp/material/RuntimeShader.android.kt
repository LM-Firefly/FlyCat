// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import androidx.compose.ui.graphics.ShaderBrush
import top.yukonga.miuix.kmp.shader.RuntimeShader as ShaderRuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush as shaderAsBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported as shaderIsRuntimeShaderSupported

// FORK（FlyCat）：转发到 0.9.4 制品 top.yukonga.miuix.kmp.shader 的同名函数（背景见 RuntimeShader.kt）。
// 上游此处是平台 expect/actual 实现；FlyCat 的 :ui 为纯 Android 模块（非 MPP）不支持 expect/actual。

/** 在 Android API 33+ 上为 true。 */
fun isRuntimeShaderSupported(): Boolean = shaderIsRuntimeShaderSupported()

/**
 * 从 AGSL shader 字符串创建平台相关的 [RuntimeShader]。
 *
 * @param shaderString 要编译的 AGSL shader 源码。
 */
fun RuntimeShader(shaderString: String): RuntimeShader = ShaderRuntimeShader(shaderString)

/**
 * 把该 [RuntimeShader] 包装为 [ShaderBrush]。Android 上底层 shader 是可变的，
 * 因此返回缓存的 [ShaderBrush]，以确保 uniform 更新可见。
 */
fun RuntimeShader.asBrush(): ShaderBrush = shaderAsBrush()
