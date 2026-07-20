use serde_json::Value as JsonValue;
use serde_yaml::Value as YamlValue;

pub fn parse_yaml_override(content: &str) -> Result<JsonValue, String> {
    let processed_content = add_yaml_tags_to_proxies_short_id(content, false);
    let mut value: YamlValue =
        serde_yaml::from_str(&processed_content).map_err(|err| err.to_string())?;
    // Merge expansion is a full-document walk. Most overrides do not use anchors, especially the
    // small built-in patches, so only pay for it when a merge key is present.
    if processed_content.contains("<<:") {
        value.apply_merge().map_err(|err| err.to_string())?;
    }
    serde_json::to_value(value).map_err(|err| err.to_string())
}

pub fn parse_yaml_to_json_string(content: &str) -> Result<String, String> {
    let json_value = parse_yaml_override(content)?;
    serde_json::to_string(&json_value).map_err(|err| err.to_string())
}

pub fn stringify_json_to_yaml_string(content: &str) -> Result<String, String> {
    let json_value: JsonValue = serde_json::from_str(content).map_err(|err| err.to_string())?;
    serde_yaml::to_string(&json_value).map_err(|err| err.to_string())
}

pub fn add_yaml_tags_to_proxies_short_id(
    yaml_content: &str,
    include_nested_proxies: bool,
) -> String {
    if !yaml_content.contains("proxies:") || !yaml_content.contains("short-id:") {
        return yaml_content.to_string();
    }

    let lines = yaml_content.lines().collect::<Vec<_>>();
    let mut result = Vec::with_capacity(lines.len());
    let mut ctx = (false, false, false);
    let mut lvl = (-1isize, -1isize, -1isize);
    for line in lines {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            result.push(line.to_string());
            continue;
        }

        let indent = line.len() - line.trim_start().len();
        if trimmed.starts_with("proxies:") && (include_nested_proxies || indent == 0) {
            ctx = (true, false, false);
            lvl.0 = indent as isize;
        } else if ctx.0 && (indent as isize) <= lvl.0 && !trimmed.starts_with('-') {
            ctx = (false, false, false);
        }

        if ctx.0 {
            if trimmed.starts_with('-') {
                ctx = (ctx.0, true, false);
                lvl.1 = indent as isize;
            } else if ctx.1 && (indent as isize) <= lvl.1 {
                ctx = (ctx.0, false, false);
            }

            if ctx.1 {
                if trimmed.starts_with("reality-opts:") {
                    ctx = (ctx.0, ctx.1, true);
                    lvl.2 = indent as isize;
                } else if ctx.2 && (indent as isize) <= lvl.2 {
                    ctx = (ctx.0, ctx.1, false);
                }

                if ctx.2 && line.trim_start().starts_with("short-id:") {
                    let Some(short_id_index) = line.find("short-id:") else {
                        result.push(line.to_string());
                        continue;
                    };
                    let prefix = &line[..short_id_index + "short-id:".len()];
                    let after_prefix = &line[short_id_index + "short-id:".len()..];
                    let trimmed_after_prefix = after_prefix.trim_start();
                    let leading_whitespace_len = after_prefix.len() - trimmed_after_prefix.len();
                    let leading_whitespace = &after_prefix[..leading_whitespace_len];
                    let mut value_end = trimmed_after_prefix.len();
                    for (index, ch) in trimmed_after_prefix.char_indices() {
                        if ch.is_whitespace() || ch == '#' {
                            value_end = index;
                            break;
                        }
                    }
                    let value = &trimmed_after_prefix[..value_end];
                    let suffix = &trimmed_after_prefix[value_end..];
                    if value.to_lowercase() != "null"
                        && value != "~"
                        && !value.starts_with("!!")
                        && !value.starts_with('{')
                        && !value.starts_with('[')
                    {
                        result.push(format!("{prefix}{leading_whitespace}!!str {value}{suffix}"));
                        continue;
                    }
                }
            }
        }

        result.push(line.to_string());
    }

    result.join("\n")
}
