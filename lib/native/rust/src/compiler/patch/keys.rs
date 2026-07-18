//! Override key grammar.
//!
//! A key in an override document is a base config key plus an optional modifier:
//!
//! | key form                 | modifier  | meaning                                  |
//! |--------------------------|-----------|------------------------------------------|
//! | `rules`                  | replace   | replace the field                        |
//! | `rules-start` / `+rules` | start     | prepend to the list                      |
//! | `rules-end` / `rules+`   | end       | append to the list                       |
//! | `dns-merge`              | merge     | deep-merge into the map                  |
//! | `dns-force`              | force     | write verbatim, skipping schema handling |
//! | `<rules+>`               | replace   | literal key, modifier characters escaped |

use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::model::{ParsedKey, PatchModifier, PatchOperations};

pub fn group_patch_keys(map: JsonMap<String, JsonValue>) -> Vec<(String, PatchOperations)> {
    let mut grouped = Vec::<(String, PatchOperations)>::new();
    for (key, value) in map {
        let parsed = parse_modifier_key(&key);
        let base_key = parsed.base.to_string();
        let index = grouped
            .iter()
            .position(|(existing, _)| existing == &base_key)
            .unwrap_or_else(|| {
                grouped.push((base_key.clone(), PatchOperations::default()));
                grouped.len() - 1
            });
        let operations = &mut grouped[index].1;
        match parsed.modifier {
            PatchModifier::Replace => operations.replace = Some(value),
            PatchModifier::Start => operations.start.push(value),
            PatchModifier::End => operations.end.push(value),
            PatchModifier::Merge => operations.merge.push(value),
            PatchModifier::Force => operations.force = Some(value),
        }
    }
    grouped
}

pub fn parse_modifier_key(key: &str) -> ParsedKey<'_> {
    if let Some(inner) = literal_key_inner(key) {
        return ParsedKey {
            base: inner,
            modifier: PatchModifier::Replace,
        };
    }

    for (suffix, modifier) in [
        ("-start", PatchModifier::Start),
        ("-end", PatchModifier::End),
        ("-merge", PatchModifier::Merge),
        ("-force", PatchModifier::Force),
    ] {
        if let Some(base) = key.strip_suffix(suffix) {
            return ParsedKey { base, modifier };
        }
    }

    ParsedKey {
        base: key,
        modifier: PatchModifier::Replace,
    }
}

pub fn literal_key_inner(key: &str) -> Option<&str> {
    key.strip_prefix('<')
        .and_then(|value| value.strip_suffix('>'))
}

pub fn unescape_literal_key(key: &str) -> String {
    literal_key_inner(key).unwrap_or(key).to_string()
}

pub fn is_escaped_literal_key(key: &str) -> bool {
    key.starts_with('<') && key.ends_with('>')
}
