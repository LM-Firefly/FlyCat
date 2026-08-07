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
# Prints the publish APK file stem used by reusable-prepare-publish.yml. Segments,
# in order: <prefix>-<geo>[-<channel_segment>]-<tail>. GEO is the geo
# database variant: "builtin" (bundled in assets) or "external" (core downloads at
# runtime). ABI arm64-v8a is the only supported and unmarked ABI. CHANNEL_SEGMENT tags
# non-default channels (smart, pr); empty for the pre
# channel and stable releases. TAIL is the packaging date (yy.MM.dd, Asia/Shanghai)
# for channel/PR builds or the clean version name for official releases — the
# caller decides which.
#
# Usage: ci-apk-stem.sh PREFIX GEO ABI CHANNEL_SEGMENT TAIL
set -eu

prefix="$1"
geo="$2"
abi="$3"
channel_segment="$4"
tail="$5"

if [ -z "${tail}" ]; then
  echo "TAIL must not be empty" >&2
  exit 1
fi

case "${geo}" in
  builtin|external) ;;
  *) echo "GEO must be builtin or external, got: ${geo}" >&2; exit 1 ;;
esac

stem="${prefix}-${geo}"
case "${abi}" in
  arm64-v8a) ;;
  *) echo "ABI must be arm64-v8a, got: ${abi}" >&2; exit 1 ;;
esac

if [ -n "${channel_segment}" ]; then
  stem="${stem}-${channel_segment}"
fi

printf '%s-%s\n' "${stem}" "${tail}"
