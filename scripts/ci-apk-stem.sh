#!/usr/bin/env bash
#
# This file is part of YumeBox.
#
# YumeBox is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Copyright (c)  YumeYucca 2025 - Present
#
# Prints the publish APK file stem shared by reusable-prepare-publish.yml and
# reusable-notify-telegram.yml. Channel/PR builds (hash present) drop the version
# from the file name: <prefix>-<branch_segment>-<hash7>. Official releases (no
# hash) keep the clean version: <prefix>-<version_name>. Callers encode the
# builtin-geo variant by passing "<prefix>-builtin" as PREFIX. The branch segment
# normalization must mirror app/build.gradle.kts (lowercase, [^a-z0-9] runs -> '-',
# leading/trailing '-' trimmed).
#
# Usage: ci-apk-stem.sh PREFIX BRANCH HASH VERSION_NAME
set -eu

prefix="$1"
branch="$2"
hash="$3"
version_name="$4"

branch_segment="$(printf '%s' "${branch}" \
  | tr '[:upper:]' '[:lower:]' \
  | sed -e 's/[^a-z0-9]/-/g' -e 's/--*/-/g' -e 's/^-//' -e 's/-$//')"

if [ -n "${hash}" ]; then
  if [ -n "${branch_segment}" ]; then
    printf '%s-%s-%s\n' "${prefix}" "${branch_segment}" "${hash:0:7}"
  else
    printf '%s-%s\n' "${prefix}" "${hash:0:7}"
  fi
else
  printf '%s-%s\n' "${prefix}" "${version_name}"
fi
