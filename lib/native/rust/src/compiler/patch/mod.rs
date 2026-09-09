//! Override document application, runtime patches, and provider path normalization.

pub mod document;
pub mod keys;
pub mod paths;
pub mod runtime;
pub mod values;

pub use document::apply_override_document;
pub use runtime::{disable_ebpf_tun_entrypoint, patch_preview_runtime, patch_providers, patch_static_runtime, validate_provider_paths};
