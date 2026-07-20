use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

pub const REQUEST_SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileRequest {
    #[serde(default = "default_request_schema_version")]
    pub schema_version: u32,
    pub profile_uuid: String,
    pub profile_dir: String,
    pub profile_path: String,
    #[serde(default)]
    pub overrides: Vec<OverrideSpec>,
    #[serde(default)]
    pub output_path: String,
    #[serde(default)]
    pub age_secret_key: Option<String>,
    #[serde(default)]
    pub run_mode: RunMode,
    #[serde(default)]
    pub skip_runtime_patches: bool,
}

/// The proxy run mode the app selected. Controls mode-specific config patching — chiefly whether the
/// compiled `tun:` block is force-disabled (VPN/TPROXY) or kept authoritative for a root-created
/// kernel device (tun). Defaults to `vpn` so older requests without the field compile unchanged.
#[derive(Debug, Deserialize, Serialize, Clone, Copy, PartialEq, Eq, Default)]
#[serde(rename_all = "kebab-case")]
pub enum RunMode {
    #[default]
    Vpn,
    Tun,
    Tproxy,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileResult {
    pub success: bool,
    pub fingerprint: String,
    pub final_yaml: String,
    pub warnings: Vec<String>,
    pub error: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileRawResult {
    pub success: bool,
    pub fingerprint: String,
    pub config_raw: String,
    pub warnings: Vec<String>,
    pub error: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct OverrideSpec {
    pub path: String,
    pub ext: String,
}

#[derive(Debug, Clone)]
pub struct LoadedOverride {
    pub path: String,
    pub ext: String,
    pub content: String,
}

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub enum CliMode {
    Preview,
    Compile,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PatchModifier {
    Replace,
    Start,
    End,
    Merge,
    Force,
}

#[derive(Clone, Copy, Debug)]
pub struct ParsedKey<'a> {
    pub base: &'a str,
    pub modifier: PatchModifier,
}

#[derive(Clone, Copy, Debug)]
pub enum SchemaId {
    Root,
    Dns,
    DnsFallbackFilter,
    Sniffer,
    Sniff,
    Protocol,
    Tun,
    ExternalControllerCors,
    Profile,
    GeoxUrl,
    App,
    ProxyItem,
    ProxyGroupItem,
    ProviderItem,
}

#[derive(Clone, Copy, Debug)]
pub enum ListStyle {
    Plain,
    NamedObjects,
}

#[derive(Clone, Copy, Debug)]
pub enum FieldBehavior {
    Scalar,
    List(ListStyle),
    Map,
    Object(SchemaId),
    Rules,
}

#[derive(Default)]
pub struct PatchOperations<'a> {
    pub replace: Option<&'a JsonValue>,
    pub start: Option<&'a JsonValue>,
    pub end: Option<&'a JsonValue>,
    pub merge: Option<&'a JsonValue>,
    pub force: Option<&'a JsonValue>,
}

fn default_request_schema_version() -> u32 {
    REQUEST_SCHEMA_VERSION
}
