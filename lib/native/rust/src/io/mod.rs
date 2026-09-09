use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};

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

static NEXT_TEMP_ID: AtomicU64 = AtomicU64::new(0);

pub fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let parent = path
        .parent()
        .ok_or_else(|| "runtime output path has no parent".to_string())?;
    fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| "runtime output path has no valid file name".to_string())?;
    let temp_id = NEXT_TEMP_ID.fetch_add(1, Ordering::Relaxed);
    let tmp = parent.join(format!(
        ".{file_name}.{}.{}.tmp",
        std::process::id(),
        temp_id
    ));

    let publish = (|| -> Result<(), String> {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&tmp)
            .map_err(|err| err.to_string())?;
        file.write_all(bytes).map_err(|err| err.to_string())?;
        file.sync_all().map_err(|err| err.to_string())?;
        drop(file);
        fs::rename(&tmp, path).map_err(|err| err.to_string())?;
        Ok(())
    })();
    if publish.is_err() {
        let _ = fs::remove_file(&tmp);
    }
    publish
}
