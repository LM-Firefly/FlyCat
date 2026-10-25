/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
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
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.extension;

/**
 * 扩展 sidecar 不包含任何可执行逻辑——其负载是 Javet 原生运行时加上 {@code extension_*} 清单元数据。
 * 这个标记的存在仅仅是为了让 Kotlin 工具链管线的 dex/L8 阶段有一个可处理的编译单元（空程序会因"未指定源文件"而在 L8 阶段失败）。
 */
public final class ExtensionMarker {
    public static final String TYPE = "js+sub-store";
    private ExtensionMarker() {
    }
}
