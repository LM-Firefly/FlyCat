// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.icon.glass

import top.yukonga.miuix.kmp.icon.MiuixIcons

// FORK（FlyCat）：miuix-core 0.9.4 的 MiuixIcons 尚无 Glass 命名空间（上游随 miuix-glass-icons
// 一并加入，但未随 0.9.4 发布制品）。本文件以扩展属性 + 镜像命名空间顶替，使各图标文件与
// 调用点都能保持上游的 `MiuixIcons.Glass.X` 形态；升级到含 Glass 命名空间的 miuix 制品后，
// 删除本文件并把各图标文件的 `GlassIcons` 接收者改回 `MiuixIcons.Glass` 即可完成切换。

/** 镜像上游 `MiuixIcons.Glass`（含字重命名空间）的占位命名空间。 */
object GlassIcons {
    object Light
    object Normal
    object Regular
    object Medium
    object Demibold
}

val MiuixIcons.Glass: GlassIcons get() = GlassIcons
