use std::fs;
use std::path::Path;

use crate::model::{LoadedOverride, OverrideSpec};

pub struct LoadedOverrides {
    pub items: Vec<LoadedOverride>,
    pub warnings: Vec<String>,
}

pub fn load_overrides(
    overrides: &[OverrideSpec],
    encrypted: bool,
) -> Result<LoadedOverrides, String> {
    let mut loaded = Vec::new();
    let mut warnings = Vec::new();
    for override_spec in overrides {
        let content = fs::read_to_string(&override_spec.path).map_err(|err| {
            if encrypted {
                format!("read override file failed for encrypted profile: {err}")
            } else {
                format!("read override file {}: {err}", override_spec.path)
            }
        })?;
        if content.trim().is_empty() {
            let warning = if encrypted {
                format!(
                    "skip empty override file for encrypted profile: ext={}",
                    override_spec.ext
                )
            } else {
                format!("skip empty override file: {}", override_spec.path)
            };
            warnings.push(warning);
            continue;
        }
        loaded.push(LoadedOverride {
            path: override_spec.path.clone(),
            ext: override_spec.ext.trim().to_ascii_lowercase(),
            content,
        });
    }
    Ok(LoadedOverrides {
        items: loaded,
        warnings,
    })
}

pub fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let parent = path
        .parent()
        .ok_or_else(|| "runtime output path has no parent".to_string())?;
    fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    let tmp = path.with_extension("yaml.tmp");
    fs::write(&tmp, bytes).map_err(|err| err.to_string())?;
    if path.exists() {
        fs::remove_file(path).map_err(|err| err.to_string())?;
    }
    fs::rename(&tmp, path).map_err(|err| err.to_string())?;
    Ok(())
}
