pub mod js;
pub mod yaml;

use serde_json::Value as JsonValue;

use crate::compiler::patch::apply_override_document;
use crate::model::LoadedOverride;

#[derive(Debug)]
pub struct ApplyOverridesResult {
    pub root: JsonValue,
    pub warnings: Vec<String>,
}

pub fn apply_overrides(
    mut root: JsonValue,
    overrides: &[LoadedOverride],
    encrypted: bool,
) -> Result<ApplyOverridesResult, String> {
    let mut warnings = Vec::new();
    for override_item in overrides {
        match override_item.ext.as_str() {
            "yaml" | "yml" => {
                let patch = yaml::parse_yaml_override(&override_item.content).map_err(|error| {
                    format!("parse yaml override {}: {}", override_item.path, error)
                })?;
                apply_override_document(&mut root, &patch);
            }
            "js" => {
                let outcome = js::apply_js_override(root, override_item, encrypted);
                root = outcome.root;
                warnings.extend(outcome.warnings);
            }
            other => {
                return Err(format!("unsupported override extension: {other}"));
            }
        }
    }
    Ok(ApplyOverridesResult { root, warnings })
}
