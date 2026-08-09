"""Rust Android shared-library builder."""

from __future__ import annotations

from .command import command_failure, execute_command
from .config import ProjectConfig, configured_abis


class RustBuilder:
    def __init__(self, config: ProjectConfig):
        self.config = config
        self.root = config.project_root
        self.source_dir = self.root / "lib/native/rust"
        self.output_dir = self.root / "build/native/rust"

    def build_all(self) -> None:
        cargo_file = self.source_dir / "Cargo.toml"
        if not cargo_file.is_file():
            raise RuntimeError(f"[Rust] Source directory not ready: missing {cargo_file}")
        abis = configured_abis(self.config)
        print(f"[Rust] Building Android shared library from {self.source_dir}")
        print("[Rust] Host CLI/ELF is not built by this script")
        print(f"[Rust] Building for ABIs: {', '.join(abis)}")
        for abi in abis:
            self.build_for_abi(abi)

    def build_for_abi(self, abi: str) -> None:
        print(f"[building] Building for {abi} (Rust)...")
        result = execute_command(
            [
                "cargo", "ndk", "-t", abi, "-o", str(self.output_dir), "build", "--release", "--lib",
                "-Z", "build-std=std,panic_abort",
            ],
            working_dir=self.source_dir,
            environment={
                "RUSTUP_TOOLCHAIN": "nightly",
                "RUSTFLAGS": "-Zunstable-options -Cpanic=immediate-abort -C link-arg=-Wl,-soname,liboverride.so",
            },
            stdout_prefix=f"[building][{abi}]",
            stderr_prefix=f"[building][{abi}]",
            stderr_is_error=False,
        )
        source_lib = self.output_dir / abi / "liboverride.so"
        if not result.success:
            raise RuntimeError(f"[building] Failed to build {abi} (Rust): {command_failure(result)}")
        if not source_lib.is_file():
            raise RuntimeError(f"[building] Output library not found: {source_lib}")
        destination = self.root / "jniLibs" / abi / "liboverride.so"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(source_lib.read_bytes())
        print(f"[Rust] Copied to {destination}")

