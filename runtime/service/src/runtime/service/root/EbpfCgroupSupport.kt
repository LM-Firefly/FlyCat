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

/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

package com.github.yumeyucca.yumebox.runtime.service.root

import java.io.File

/** Resolves the cgroup v2 mount used by the socket-address hook. */
object EbpfCgroupSupport {
    private const val CGROUP_MOUNT = "/sys/fs/cgroup"

    /**
     * Android's SELinux policy may permit BPF attach on the cgroup v2 mount while rejecting attach
     * on app leaf cgroups such as `apps/uid_<uid>/pid_<pid>`. The root hook is intentionally paired with the
     * native UID policy map, so it does not mean that every UID is proxied by default.
     */
    fun rootCgroupPath(): String? =
        CGROUP_MOUNT.takeIf { File(it).isDirectory }
}
