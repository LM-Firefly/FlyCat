"""Command-line orchestration for native builds."""

from __future__ import annotations

import argparse
from pathlib import Path
import shutil

from .command import command_exists
from .config import NdkTools, ProjectConfig, SystemDetector
from .geo import ResourceDownloader
from .go_core import GoCoreBuilder
from .rust_builder import LoaderRustBuilder, RustBuilder


MESSAGE = r"""
  _____  _           ____       _
 |  ___)| | _   _   / ___| __ _| |_
 | |_   | || | | | | |    / _ `| __|
 |  _|  | || |_| | | |___| (_| | |_
 | |    | |\_\_/_|  \____|\__,_|\__|
 |_|    \_\\   | |
           |\__| |
           \_____/
""".strip()


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description="FlyCat Native Build Tool")
    result.add_argument("--go", action="store_true", help="Build the mihomo shared core (c-shared JNI library)")
    result.add_argument("--coreexe", action="store_true", help="Compatibility alias of --go")
    result.add_argument("--rust", action="store_true", help="Build Rust config-compiler override library")
    result.add_argument("--loader", action="store_true", help="Build the Rust native payload extractor")
    result.add_argument("--geo", action="store_true", help="Download Geo databases and BundleMRS.7z")
    result.add_argument("--clean", action="store_true", help="Clean build outputs")
    result.add_argument("--all", action="store_true", help="Build everything")
    return result


def clean_build_outputs(root: Path) -> None:
    print("[Clean] Removing build outputs...")
    for relative in ("build/native", "build/generated"):
        shutil.rmtree(root / relative, ignore_errors=True)
    for name in ("geoip.metadb.xz", "geosite.dat.xz", "ASN.mmdb.xz"):
        (root / "app/assets" / name).unlink(missing_ok=True)
    for abi in ("armeabi-v7a", "arm64-v8a", "x86", "x86_64"):
        for name in ("libmihomo.so", "liboverride.so", "libloader.so", "core-version.properties"):
            (root / "jniLibs" / abi / name).unlink(missing_ok=True)
    (root / "build/generated/core-version.properties").unlink(missing_ok=True)
    print("[Clean] Done")


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    root = Path(__file__).resolve().parents[2]
    if args.clean:
        clean_build_outputs(root)
        return 0

    print(MESSAGE)
    print("=== FlyCat Native Build Tool ===")
    print(f"OS: {SystemDetector.os_name()}, Host: {SystemDetector.host_tag()}")
    config = ProjectConfig(root)
    explicit = any((args.go, args.coreexe, args.rust, args.loader, args.geo, args.all))
    build_go = not explicit or args.all or args.go or args.coreexe
    build_rust = not explicit or args.all or args.rust
    build_loader = not explicit or args.all or args.loader
    download_geo = not explicit or args.all or args.geo
    needs_ndk = build_go or build_loader
    ndk_tools = NdkTools(config) if needs_ndk else None
    if ndk_tools:
        print(f"NDK: {ndk_tools.ndk_dir}")
    print(f"Go: {'OK' if command_exists('go') else 'NOT FOUND'}")
    print(f"Rust: {'OK' if command_exists('cargo') else 'NOT FOUND'}")
    print("XZ: Python standard library lzma")

    if build_go:
        GoCoreBuilder(config, ndk_tools).build_all()
    if build_rust:
        RustBuilder(config).build_all()
    if build_loader:
        LoaderRustBuilder(config).build_all()
    if download_geo:
        ResourceDownloader(config).download_geo_files()
    print("=== Build Complete ===")
    return 0
