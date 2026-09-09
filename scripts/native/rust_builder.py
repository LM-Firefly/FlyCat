"""Rust Android shared-library builders."""

from __future__ import annotations

from .command import command_failure, execute_command
from .config import ProjectConfig, configured_abis


class _RustCrateBuilder:
    """Base for cargo-ndk builds producing a single .so per ABI."""

    def __init__(
        self,
        config: ProjectConfig,
        *,
        name: str,
        source_relative: str,
        output_relative: str,
        soname: str,
    ):
        self.config = config
        self.root = config.project_root
        self.name = name
        self.source_dir = self.root / source_relative
        self.output_dir = self.root / output_relative
        self.soname = soname

    def build_all(self) -> None:
        cargo_file = self.source_dir / "Cargo.toml"
        if not cargo_file.is_file():
            raise RuntimeError(f"[{self.name}] Source directory not ready: missing {cargo_file}")
        abis = configured_abis(self.config)
        print(f"[{self.name}] Building Android shared library from {self.source_dir}")
        print(f"[{self.name}] Building for ABIs: {', '.join(abis)}")
        for abi in abis:
            self.build_for_abi(abi)

    def build_for_abi(self, abi: str) -> None:
        print(f"[building] Building for {abi} ({self.name})...")
        result = execute_command(
            [
                "cargo", "ndk", "-t", abi, "-o", str(self.output_dir), "build", "--release", "--lib",
            ],
            working_dir=self.source_dir,
            environment={
                "RUSTUP_TOOLCHAIN": "nightly",
                "RUSTFLAGS": f"-Cpanic=unwind -C link-arg=-Wl,-soname,{self.soname}",
            },
            stdout_prefix=f"[building][{abi}]",
            stderr_prefix=f"[building][{abi}]",
            stderr_is_error=False,
        )
        source_lib = self.output_dir / abi / self.soname
        if not result.success:
            raise RuntimeError(f"[{self.name}] Failed to build {abi}: {command_failure(result)}")
        if not source_lib.is_file():
            raise RuntimeError(f"[{self.name}] Output library not found: {source_lib}")
        destination = self.root / "jniLibs" / abi / self.soname
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(source_lib.read_bytes())
        print(f"[{self.name}] Copied to {destination}")


class RustBuilder(_RustCrateBuilder):
    """Builds the Rust config-compiler override library (liboverride.so)."""

    def __init__(self, config: ProjectConfig):
        super().__init__(config, name="Rust", source_relative="lib/native/rust", output_relative="build/native/rust", soname="liboverride.so")


class LoaderRustBuilder(_RustCrateBuilder):
    """Builds the Rust native payload extractor (libloader.so)."""

    def __init__(self, config: ProjectConfig):
        super().__init__(config, name="Loader", source_relative="pack/native", output_relative="build/native/loader", soname="libloader.so")
