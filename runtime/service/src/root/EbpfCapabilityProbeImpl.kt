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

package com.github.lmfirefly.flycat.runtime.service.root

import android.content.Context
import com.github.lmfirefly.flycat.runtime.api.root.EbpfCapabilityProbe

/** eBPF 的能力现在由活动内核的能力决定，而非桥接探测器。 */
class EbpfCapabilityProbeImpl : EbpfCapabilityProbe {
    override fun rootCgroupPath(): String? = null

    override fun isCapabilityAvailable(context: Context, cgroupPath: String): Boolean =
        com.github.lmfirefly.flycat.core.kernel.KernelManager.isEbpfKernelActive(context)
}
