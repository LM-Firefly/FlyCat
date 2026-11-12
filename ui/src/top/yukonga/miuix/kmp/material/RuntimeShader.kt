// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

// FORK（FlyCat）：0.9.4 制品已提供同一套 shader 原语（top.yukonga.miuix.kmp.shader.RuntimeShader），
// 且 miuix-blur 0.9.4 的 runtimeShaderEffect 接收者即该类型。为打通类型边界并保持上游 material 包的
// API 形态（上游此处是独立接口定义，含完整 KDoc），此处以 typealias 统一到制品接口；
// 升级到含 miuix-material 的 miuix 制品后可整体移除本适配（函数转发见 RuntimeShader.android.kt）。
typealias RuntimeShader = top.yukonga.miuix.kmp.shader.RuntimeShader
