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
pub struct PatchOperations {
    pub replace: Option<JsonValue>,
    pub start: Option<JsonValue>,
    pub end: Option<JsonValue>,
    pub merge: Option<JsonValue>,
    pub force: Option<JsonValue>,
}

fn default_request_schema_version() -> u32 {
    REQUEST_SCHEMA_VERSION
}
