use serde_json::{json, Value as JsonValue};
use std::ffi::{CStr, CString};
use std::fs;
use std::io::{Read, Write};
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::thread;

use crate::compiler::normalize::normalize_root;
use crate::compiler::{
    compile_raw_request, compile_request, override_compile_raw, override_free_string,
};
use crate::engine;
use crate::engine::yaml::add_yaml_tags_to_proxies_short_id;
#[cfg(any(target_os = "linux", target_os = "android"))]
use crate::jni::{down_scale_traffic, pack_traffic};
use crate::model::{CompileRequest, LoadedOverride, RunMode, REQUEST_SCHEMA_VERSION};
use age::secrecy::ExposeSecret;

fn test_request(profile_dir: &Path, profile_path: &Path) -> CompileRequest {
    CompileRequest {
        schema_version: REQUEST_SCHEMA_VERSION,
        profile_uuid: "test-profile".to_string(),
        profile_dir: profile_dir.to_string_lossy().into_owned(),
        profile_path: profile_path.to_string_lossy().into_owned(),
        overrides: Vec::new(),
        output_path: String::new(),
        age_secret_key: None,
        run_mode: RunMode::default(),
        skip_runtime_patches: false,
        preview: false,
    }
}

fn provider_path_from_runtime_home(profile_dir: &Path, file_name: &str) -> String {
    let runtime_home = profile_dir
        .parent()
        .and_then(Path::parent)
        .map(|files_dir| files_dir.join("mihomo"))
        .unwrap_or_else(|| profile_dir.to_path_buf());
    let provider_path = profile_dir.join("providers").join("rules").join(file_name);
    relative_path_from(&provider_path, &runtime_home)
        .unwrap_or(provider_path)
        .to_string_lossy()
        .replace('\\', "/")
}

fn relative_path_from(path: &Path, base: &Path) -> Option<PathBuf> {
    use std::path::Component;
    let path_components = path.components().collect::<Vec<_>>();
    let base_components = base.components().collect::<Vec<_>>();

    let mut common_count = 0;
    while common_count < path_components.len()
        && common_count < base_components.len()
        && path_components[common_count] == base_components[common_count]
    {
        common_count += 1;
    }
    if common_count == 0 {
        return None;
    }

    let mut result = PathBuf::new();
    for component in &base_components[common_count..] {
        if matches!(component, Component::Normal(_)) {
            result.push("..");
        }
    }
    for component in &path_components[common_count..] {
        result.push(component.as_os_str());
    }
    Some(result)
}

fn encrypt_age(plaintext: &[u8], identity: &age::x25519::Identity) -> Vec<u8> {
    let recipient = identity.to_public();
    let mut ciphertext = Vec::new();
    let mut writer = age::Encryptor::with_recipients(std::iter::once(&recipient as _))
        .expect("create age encryptor")
        .wrap_output(&mut ciphertext)
        .expect("wrap age output");
    writer.write_all(plaintext).expect("write age plaintext");
    writer.finish().expect("finish age encryption");
    ciphertext
}

fn compile_root_with_geosite_matcher(
    value: Option<JsonValue>,
) -> Result<crate::model::CompileResult, String> {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-compiler-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let mut root = serde_json::Map::new();
    root.insert("mode".to_string(), JsonValue::String("rule".to_string()));
    if let Some(value) = value {
        root.insert("geosite-matcher".to_string(), value);
    }

    let profile_path = temp_dir.join("profile.yaml");
    let yaml = serde_yaml::to_string(&normalize_root(JsonValue::Object(root)))
        .expect("serialize profile yaml");
    fs::write(&profile_path, yaml).expect("write profile yaml");

    let request = CompileRequest {
        schema_version: REQUEST_SCHEMA_VERSION,
        profile_uuid: "test-profile".to_string(),
        profile_dir: temp_dir.to_string_lossy().into_owned(),
        profile_path: profile_path.to_string_lossy().into_owned(),
        overrides: Vec::new(),
        output_path: String::new(),
        age_secret_key: None,
        run_mode: RunMode::default(),
        skip_runtime_patches: false,
        preview: false,
    };

    let result = compile_request(request, false);
    let _ = fs::remove_dir_all(&temp_dir);
    result
}

#[test]
fn yaml_short_id_is_tagged_as_string() {
    let source = r#"
proxies:
  - name: example
    reality-opts:
      short-id: abc123
"#;
    let processed = add_yaml_tags_to_proxies_short_id(source, false);
    assert!(processed.contains("short-id: !!str abc123"));
}

#[test]
fn compile_request_accepts_explicit_geosite_matcher() {
    let result = compile_root_with_geosite_matcher(Some(json!("mph")))
        .expect("compile request should succeed");
    assert!(result.success);
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    assert_eq!(
        root.get("geosite-matcher").and_then(JsonValue::as_str),
        Some("mph")
    );
}

#[test]
fn compile_request_rejects_invalid_geosite_matcher() {
    let err = compile_root_with_geosite_matcher(Some(json!("invalid")))
        .expect_err("compile request should fail for invalid geosite-matcher");
    assert!(
        err.contains("geosite-matcher must be one of"),
        "unexpected error message: {err}"
    );
}

fn compile_raw_from_yaml(source_yaml: &str) -> Result<JsonValue, String> {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-merge-key-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, source_yaml).expect("write profile yaml");

    let result = compile_raw_request(test_request(&temp_dir, &profile_path));
    let _ = fs::remove_dir_all(&temp_dir);
    result.map(|raw| serde_json::from_str(&raw.config_raw).expect("parse config_raw json"))
}

#[test]
fn yaml_merge_keys_are_expanded_in_compiled_config() {
    // Anchors supply `type`/`behavior`/`format` via `<<: *anchor`, exactly like a real profile.
    let source = r#"
mode: rule
anchors:
  proxy_first: &proxy_first {type: select, proxies: [DIRECT]}
  domain: &domain {type: http, interval: 86400, behavior: domain, format: mrs}
proxy-groups:
  - {name: YouTube, <<: *proxy_first}
  - {name: Apple, type: url-test, <<: *proxy_first}
rule-providers:
  youtube_domain: {<<: *domain, url: "https://example.invalid/youtube.mrs"}
"#;
    let root = compile_raw_from_yaml(source).expect("compile profile with merge keys");

    let groups = root
        .get("proxy-groups")
        .and_then(JsonValue::as_array)
        .expect("proxy-groups array");

    // Merge supplies `type` when the group has no explicit type.
    let youtube = &groups[0];
    assert_eq!(
        youtube.get("type").and_then(JsonValue::as_str),
        Some("select"),
        "YouTube must inherit type from the anchor"
    );
    assert!(
        youtube.get("<<").is_none(),
        "merge key must be expanded, not left as a literal `<<` key"
    );

    // Explicit field wins over the merged value.
    let apple = &groups[1];
    assert_eq!(
        apple.get("type").and_then(JsonValue::as_str),
        Some("url-test"),
        "explicit type must win over the merged anchor type"
    );

    // Merge also applies to rule-provider mapping values.
    let provider = root
        .get("rule-providers")
        .and_then(|providers| providers.get("youtube_domain"))
        .expect("rule provider");
    assert_eq!(
        provider.get("behavior").and_then(JsonValue::as_str),
        Some("domain"),
        "rule provider must inherit behavior from the anchor"
    );
    assert_eq!(
        provider.get("format").and_then(JsonValue::as_str),
        Some("mrs")
    );
    assert!(provider.get("<<").is_none());
}

#[test]
fn yaml_override_is_applied_with_merge_order() {
    let root = json!({
        "mode": "rule",
        "proxies": [{ "name": "A", "type": "http", "server": "one", "port": 80 }]
    });
    let overrides = vec![LoadedOverride {
        path: "test.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "proxies-end:\n  - name: B\n    type: http\n    server: two\n    port: 81\n"
            .to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply yaml override");
    let proxies = result
        .root
        .get("proxies")
        .and_then(JsonValue::as_array)
        .expect("proxies");
    assert_eq!(proxies.len(), 2);
    assert_eq!(
        proxies[1].get("name").and_then(JsonValue::as_str),
        Some("B")
    );
}

#[test]
fn yaml_override_parse_error_includes_override_path() {
    let root = json!({ "mode": "rule" });
    let overrides = vec![LoadedOverride {
        path: "/tmp/custom-routing.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "proxy-groups:\n  -\n  name: Proxy\n".to_string(),
    }];

    let error = engine::apply_overrides(root, &overrides, false)
        .expect_err("broken yaml override should fail");
    assert!(
        error.contains("/tmp/custom-routing.yaml"),
        "unexpected error message: {error}"
    );
}

#[test]
fn js_override_can_use_yaml_helpers() {
    let root = json!({ "mode": "rule" });
    let overrides = vec![LoadedOverride {
        path: "test.js".to_string(),
        ext: "js".to_string(),
        content: r#"
function main(profile) {
  const tun = yaml.parse("tun:\n  enable: false\n  stack: mixed\n");
  return deepMerge(profile, tun, true);
}
"#
        .to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply js override");
    assert_eq!(result.root["tun"]["enable"], JsonValue::Bool(false));
    assert_eq!(
        result.root["tun"]["stack"],
        JsonValue::String("mixed".to_string())
    );
}

#[test]
fn js_override_supports_async_main_and_writes_log_file() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-js-async-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp override dir");

    let override_path = temp_dir.join("example.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: r#"
async function main(profile) {
  console.info("boot", { mode: profile.mode });
  await Promise.resolve();
  console.debug("after-await");
  profile.extra = "ok";
  return profile;
}
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(json!({ "mode": "rule" }), &overrides, false)
        .expect("apply async js override");
    assert!(
        result.warnings.is_empty(),
        "unexpected warnings: {:?}",
        result.warnings
    );
    assert_eq!(result.root["extra"], JsonValue::String("ok".to_string()));

    let log_path = override_path.with_extension("log");
    let log_content = fs::read_to_string(&log_path).expect("read js override log");
    assert!(log_content.contains("[info] 开始执行脚本"));
    assert!(log_content.contains("[info] \"boot\" {\"mode\":\"rule\"}"));
    assert!(log_content.contains("[debug] \"after-await\""));
    assert!(log_content.contains("[info] 脚本执行成功"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn js_override_fetch_helper_reads_http_payload() {
    let listener = TcpListener::bind(("127.0.0.1", 0)).expect("bind test listener");
    let address = listener.local_addr().expect("listener address");
    let server = thread::spawn(move || {
        let (mut stream, _) = listener.accept().expect("accept request");
        let mut buffer = [0u8; 1024];
        let _ = stream.read(&mut buffer);
        let body = r#"{"port":7890}"#;
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            body.len(),
            body
        );
        stream
            .write_all(response.as_bytes())
            .expect("write response");
    });

    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-js-fetch-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp override dir");
    let override_path = temp_dir.join("fetch.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: format!(
            r#"
async function main(profile) {{
  const response = await fetch("http://{address}/override");
  const payload = await response.json();
  console.log("status", response.status);
  profile.port = payload.port;
  return profile;
}}
"#
        ),
    }];

    let result = engine::apply_overrides(json!({ "mode": "rule" }), &overrides, false)
        .expect("apply js fetch override");
    assert!(
        result.warnings.is_empty(),
        "unexpected warnings: {:?}",
        result.warnings
    );
    assert_eq!(result.root["port"], JsonValue::from(7890));

    server.join().expect("join http server");
    let log_content =
        fs::read_to_string(override_path.with_extension("log")).expect("read fetch log");
    assert!(log_content.contains("[log] \"status\" 200"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn js_override_failure_is_reported_as_warning_and_keeps_original_profile() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-js-failure-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp override dir");

    let root = json!({ "mode": "rule", "port": 7890 });
    let override_path = temp_dir.join("broken.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: "function main(profile) { throw new Error('boom'); }".to_string(),
    }];

    let result =
        engine::apply_overrides(root.clone(), &overrides, false).expect("apply broken js override");
    assert_eq!(result.root, root);
    assert_eq!(result.warnings.len(), 1);
    assert!(result.warnings[0].contains("skip JS override"));
    assert!(result.warnings[0].contains("boom"));

    let log_content =
        fs::read_to_string(override_path.with_extension("log")).expect("read failure log");
    assert!(log_content.contains("[exception] 脚本执行失败"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn js_override_logs_are_redacted_for_encrypted_profiles() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-js-encrypted-log-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp override dir");

    let override_path = temp_dir.join("encrypted.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: r#"
function main(profile) {
  console.log("profile", profile);
  throw new Error(`secret mode ${profile.mode}`);
}
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(json!({ "mode": "secret-rule" }), &overrides, true)
        .expect("apply encrypted js override");
    assert_eq!(result.warnings.len(), 1);
    assert!(!result.warnings[0].contains("secret-rule"));
    assert!(!result.warnings[0].contains(&override_path.to_string_lossy().to_string()));

    let log_content =
        fs::read_to_string(override_path.with_extension("log")).expect("read encrypted log");
    assert!(log_content.contains("(redacted, encrypted profile)"));
    assert!(!log_content.contains("secret-rule"));
    assert!(!log_content.contains("secret mode"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_emits_warning_for_empty_override_file() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-empty-override-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let profile_path = temp_dir.join("profile.yaml");
    fs::write(&profile_path, "mode: rule\n").expect("write profile yaml");
    let empty_override_path = temp_dir.join("empty.js");
    fs::write(&empty_override_path, "  \n").expect("write empty override");

    let request = CompileRequest {
        schema_version: REQUEST_SCHEMA_VERSION,
        profile_uuid: "test-profile".to_string(),
        profile_dir: temp_dir.to_string_lossy().into_owned(),
        profile_path: profile_path.to_string_lossy().into_owned(),
        overrides: vec![crate::model::OverrideSpec {
            path: empty_override_path.to_string_lossy().into_owned(),
            ext: "js".to_string(),
        }],
        output_path: String::new(),
        age_secret_key: None,
        run_mode: RunMode::default(),
        skip_runtime_patches: false,
        preview: false,
    };

    let result = compile_request(request, false).expect("compile request should succeed");
    assert_eq!(result.warnings.len(), 1);
    assert!(result.warnings[0].contains("skip empty override file"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_preserves_existing_provider_path() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-provider-path-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let profile_path = temp_dir.join("profile.yaml");
    fs::write(
        &profile_path,
        r#"
mode: rule
rules:
  - RULE-SET,geolocation-!cn,PROXY
rule-providers:
  geolocation-!cn:
    type: http
    url: https://example.com/geolocation-!cn.yaml
    path: providers/rules/geolocation-!cn.yaml
    behavior: domain
    interval: 86400
    format: yaml
"#,
    )
    .expect("write profile yaml");

    let request = test_request(&temp_dir, &profile_path);

    let result = compile_request(request, false).expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, "geolocation-!cn.yaml");
    assert_eq!(
        root["rule-providers"]["geolocation-!cn"]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_preserves_existing_dot_provider_path() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-provider-relative-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let profile_path = temp_dir.join("profile.yaml");
    fs::write(
        &profile_path,
        r#"
mode: rule
rules:
  - RULE-SET,ads_domain,REJECT
rule-providers:
  ads_domain:
    type: http
    url: https://example.com/ads_domain.mrs
    path: ./providers/rules/ads_domain.mrs
    behavior: domain
    interval: 86400
    format: mrs
"#,
    )
    .expect("write profile yaml");

    let request = test_request(&temp_dir, &profile_path);

    let result = compile_request(request, false).expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, "ads_domain.mrs");
    assert_eq!(
        root["rule-providers"]["ads_domain"]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_rewrites_legacy_ruleset_provider_path() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-provider-legacy-path-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let profile_path = temp_dir.join("profile.yaml");
    fs::write(
        &profile_path,
        r#"
mode: rule
rules:
  - RULE-SET,advertising,REJECT
rule-providers:
  advertising:
    type: http
    url: https://example.com/advertising.yaml
    path: ./ruleset/advertising.yaml
    behavior: domain
    interval: 86400
    format: yaml
"#,
    )
    .expect("write profile yaml");

    let request = test_request(&temp_dir, &profile_path);

    let result = compile_request(request, false).expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, "advertising.yaml");
    assert_eq!(
        root["rule-providers"]["advertising"]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_normalizes_absolute_provider_path_to_profile_scope() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-provider-absolute-path-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let provider_path = temp_dir
        .join("providers")
        .join("rules")
        .join("geolocation-!cn.yaml");
    fs::create_dir_all(provider_path.parent().expect("provider parent"))
        .expect("create provider dir");
    fs::write(&provider_path, "payload").expect("write provider file");

    let profile_path = temp_dir.join("profile.yaml");
    fs::write(
        &profile_path,
        format!(
            r#"
mode: rule
rules:
  - RULE-SET,geolocation-!cn,PROXY
rule-providers:
  geolocation-!cn:
    type: http
    url: https://example.com/geolocation-!cn.yaml
    path: {}
    behavior: domain
    interval: 86400
    format: yaml
"#,
            provider_path.to_string_lossy().replace('\\', "/"),
        ),
    )
    .expect("write profile yaml");

    let request = test_request(&temp_dir, &profile_path);

    let result = compile_request(request, false).expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, "geolocation-!cn.yaml");
    assert_eq!(
        root["rule-providers"]["geolocation-!cn"]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_raw_request_decrypts_age_source_to_config_raw_json() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-age-raw-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let identity = age::x25519::Identity::generate();
    let plaintext = b"mode: rule\nmixed-port: 7890\n";
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, encrypt_age(plaintext, &identity)).expect("write encrypted profile");

    let mut request = test_request(&temp_dir, &profile_path);
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    let override_path = temp_dir.join("override.yaml");
    fs::write(&override_path, "mixed-port: 7891\n").expect("write yaml override");
    request.overrides = vec![crate::model::OverrideSpec {
        path: override_path.to_string_lossy().into_owned(),
        ext: "yaml".to_string(),
    }];

    let result = compile_raw_request(request).expect("compile encrypted raw config");
    assert!(result.success);
    assert!(result.config_raw.trim_start().starts_with('{'));
    assert!(!result.config_raw.contains("mode: rule"));

    let raw: JsonValue = serde_json::from_str(&result.config_raw).expect("parse raw config json");
    assert_eq!(raw["mode"], JsonValue::String("rule".to_string()));
    assert_eq!(raw["mixed-port"], JsonValue::from(7891));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn encrypted_empty_override_warning_redacts_override_path() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-age-empty-override-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");
    let empty_override_path = temp_dir.join("empty.js");
    fs::write(&empty_override_path, "  \n").expect("write empty override");

    let mut request = test_request(&temp_dir, &profile_path);
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    request.overrides = vec![crate::model::OverrideSpec {
        path: empty_override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
    }];

    let result = compile_raw_request(request).expect("compile encrypted raw config");
    assert_eq!(result.warnings.len(), 1);
    assert!(result.warnings[0].contains("skip empty override file"));
    assert!(!result.warnings[0].contains(&empty_override_path.to_string_lossy().to_string()));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn override_compile_raw_returns_structured_error_result() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-age-raw-abi-error-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let request = test_request(&temp_dir, &profile_path);
    let request_json = serde_json::to_string(&request).expect("encode raw request");
    let request_c = CString::new(request_json).expect("request has no nul bytes");

    let ptr = unsafe { override_compile_raw(request_c.as_ptr()) };
    assert!(!ptr.is_null());
    let response = unsafe { CStr::from_ptr(ptr).to_string_lossy().into_owned() };
    unsafe { override_free_string(ptr) };

    let result: JsonValue = serde_json::from_str(&response).expect("parse raw abi result");
    assert_eq!(result["success"], JsonValue::Bool(false));
    assert!(result["error"]
        .as_str()
        .expect("error should be present")
        .contains("requires ageSecretKey"));
    assert_eq!(result["configRaw"], JsonValue::String(String::new()));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_raw_request_requires_age_secret_key_for_encrypted_source() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-age-missing-key-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let request = test_request(&temp_dir, &profile_path);
    let error = compile_raw_request(request).expect_err("missing key should fail");
    assert!(error.contains("requires ageSecretKey"));

    let _ = fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_rejects_yaml_output_for_encrypted_source() {
    let temp_dir = std::env::temp_dir().join(format!(
        "flycat-age-yaml-output-test-{}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&temp_dir).expect("create temp profile dir");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let output_path = temp_dir.join("runtime.yaml");
    let mut request = test_request(&temp_dir, &profile_path);
    request.output_path = output_path.to_string_lossy().into_owned();
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    let override_path = temp_dir.join("override.js");
    fs::write(&override_path, "console.log('must-not-run');\n").expect("write js override");
    request.overrides = vec![crate::model::OverrideSpec {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
    }];

    let error = compile_request(request, true).expect_err("encrypted yaml output should fail");
    assert!(error.contains("YAML output is disabled"));
    assert!(!output_path.exists());
    assert!(!override_path.with_extension("log").exists());

    let _ = fs::remove_dir_all(&temp_dir);
}

fn rule_texts(root: &JsonValue) -> Vec<&str> {
    root["rules"]
        .as_array()
        .expect("rules")
        .iter()
        .map(|value| value.as_str().unwrap_or_default())
        .collect()
}

#[test]
fn map_merge_is_applied_after_same_document_replacement() {
    let root = json!({
        "proxy-providers": {
            "old": { "type": "file", "path": "old.yaml" }
        }
    });
    let overrides = vec![LoadedOverride {
        path: "providers.yaml".to_string(),
        ext: "yaml".to_string(),
        content: r#"
proxy-providers:
  base:
    type: http
    url: https://example.com/base.yaml
proxy-providers-merge:
  base:
    interval: 3600
  extra:
    type: file
    path: extra.yaml
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply provider merge");
    let providers = result.root["proxy-providers"]
        .as_object()
        .expect("proxy providers");
    assert!(!providers.contains_key("old"));
    assert_eq!(
        providers["base"]["type"],
        JsonValue::String("http".to_string())
    );
    assert_eq!(providers["base"]["interval"], JsonValue::from(3600));
    assert_eq!(
        providers["extra"]["path"],
        JsonValue::String("extra.yaml".to_string())
    );
}

#[test]
fn empty_override_list_is_noop() {
    let root = json!({ "mode": "rule", "port": 9 });
    let result = engine::apply_overrides(root.clone(), &[], false).expect("empty overrides");
    assert_eq!(result.root, root);
    assert!(result.warnings.is_empty());
}

#[test]
fn yaml_plus_rules_modifier_matches_rules_start() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,PROXY"]
    });
    let overrides = vec![LoadedOverride {
        path: "plus.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "+rules:\n  - DOMAIN-SUFFIX,baidu.com,DIRECT\n".to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply +rules");
    assert_eq!(
        rule_texts(&result.root),
        vec!["DOMAIN-SUFFIX,baidu.com,DIRECT", "MATCH,PROXY"]
    );
}

#[test]
fn multiple_yaml_overrides_accumulate_list_start_end() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,PROXY"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "a.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-start:\n  - DOMAIN-SUFFIX,a.com,DIRECT\n".to_string(),
        },
        LoadedOverride {
            path: "b.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-end:\n  - DOMAIN-SUFFIX,b.com,DIRECT\n".to_string(),
        },
    ];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply two yaml");
    assert_eq!(
        rule_texts(&result.root),
        vec![
            "DOMAIN-SUFFIX,a.com,DIRECT",
            "DOMAIN-SUFFIX,b.com,DIRECT",
            "MATCH,PROXY"
        ]
    );
}

#[test]
fn later_rules_start_applies_after_rules_replacement() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "replacement.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules:\n  - MATCH,REPLACED\n".to_string(),
        },
        LoadedOverride {
            path: "prepend.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-start:\n  - DOMAIN-SUFFIX,example.com,DIRECT\n".to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply ordered chain");
    assert_eq!(
        rule_texts(&result.root),
        vec!["DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,REPLACED"]
    );
}

#[test]
fn later_rules_end_is_inserted_before_global_match_rule() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "global.yaml".to_string(),
            ext: "yaml".to_string(),
            content: r#"
rules:
  - RULE-SET,global,PROXY
  - MATCH,PROXY
"#
            .to_string(),
        },
        LoadedOverride {
            path: "append.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-end:\n  - DOMAIN-SUFFIX,example.com,DIRECT\n".to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply ordered chain");
    assert_eq!(
        rule_texts(&result.root),
        vec![
            "RULE-SET,global,PROXY",
            "DOMAIN-SUFFIX,example.com,DIRECT",
            "MATCH,PROXY",
        ]
    );
}

#[test]
fn built_in_direct_rules_stack_after_built_in_global_rules() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "builtin-pudding-dog.yaml".to_string(),
            ext: "yaml".to_string(),
            content: include_str!("../../../../../data/assets/overrides/builtin/pudding_dog.yaml")
                .to_string(),
        },
        LoadedOverride {
            path: "builtin-add-direct-rules.yaml".to_string(),
            ext: "yaml".to_string(),
            content: include_str!(
                "../../../../../data/assets/overrides/builtin/add_direct_rules.yaml"
            )
            .to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply built-in chain");
    let rules = result.root["rules"].as_array().expect("rules");
    assert_eq!(rules[0].as_str(), Some("DOMAIN-SUFFIX,baidu.com,DIRECT"));
    assert_eq!(rules[1].as_str(), Some("DOMAIN-SUFFIX,tencent.com,DIRECT"));
    assert_eq!(
        rules.last().and_then(JsonValue::as_str),
        Some("MATCH,PROXY")
    );
    assert!(
        rules
            .iter()
            .any(|rule| rule.as_str() == Some("RULE-SET,geolocation-!cn,PROXY"))
    );
}

#[test]
fn one_yaml_override_combines_rules_replacement_and_all_list_modifiers() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![LoadedOverride {
        path: "combined.yaml".to_string(),
        ext: "yaml".to_string(),
        content: r#"
rules:
  - MATCH,REPLACED
+rules:
  - DOMAIN-SUFFIX,plus-start.example,DIRECT
rules-start:
  - DOMAIN-SUFFIX,start.example,DIRECT
rules+:
  - DOMAIN-SUFFIX,plus-end.example,DIRECT
rules-end:
  - DOMAIN-SUFFIX,end.example,DIRECT
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply combined");
    let rules = rule_texts(&result.root);
    // +rules and rules-start both prepend; rules and rules-end/append both append
    // Final order: prepends (in document order) → replacement → appends
    assert!(rules.contains(&"MATCH,REPLACED"));
    assert!(rules.contains(&"DOMAIN-SUFFIX,plus-start.example,DIRECT"));
    assert!(rules.contains(&"DOMAIN-SUFFIX,start.example,DIRECT"));
    assert!(rules.contains(&"DOMAIN-SUFFIX,plus-end.example,DIRECT"));
    assert!(rules.contains(&"DOMAIN-SUFFIX,end.example,DIRECT"));
    assert_eq!(rules.last(), Some(&"MATCH,REPLACED"));
}

#[test]
fn yaml_then_js_override_chain_applies_both() {
    let root = json!({ "mode": "rule", "port": 7890 });
    let overrides = vec![
        LoadedOverride {
            path: "yaml.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "mixed-port: 7892\n".to_string(),
        },
        LoadedOverride {
            path: "js.js".to_string(),
            ext: "js".to_string(),
            content: r#"
function main(profile) {
  profile.socksPort = 7891;
  return profile;
}
"#
            .to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply yaml+js chain");
    assert_eq!(result.root["mixed-port"], JsonValue::from(7892));
    assert_eq!(result.root["socksPort"], JsonValue::from(7891));
}

#[test]
fn two_js_overrides_apply_in_order() {
    let root = json!({ "mode": "rule" });
    let overrides = vec![
        LoadedOverride {
            path: "first.js".to_string(),
            ext: "js".to_string(),
            content: r#"
function main(profile) {
  profile.first = true;
  return profile;
}
"#
            .to_string(),
        },
        LoadedOverride {
            path: "second.js".to_string(),
            ext: "js".to_string(),
            content: r#"
function main(profile) {
  profile.second = profile.first === true;
  return profile;
}
"#
            .to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply two js");
    assert_eq!(result.root["first"], JsonValue::Bool(true));
    assert_eq!(result.root["second"], JsonValue::Bool(true));
}

#[test]
fn many_js_overrides_reuse_runtime_and_accumulate() {
    let root = json!({ "mode": "rule" });
    let overrides: Vec<LoadedOverride> = (0..8)
        .map(|i| LoadedOverride {
            path: format!("script_{i}.js"),
            ext: "js".to_string(),
            content: format!(
                r#"
function main(profile) {{
  profile["step_{i}"] = true;
  return profile;
}}
"#
            ),
        })
        .collect();

    let result = engine::apply_overrides(root, &overrides, false).expect("apply 8 js overrides");
    for i in 0..8 {
        assert_eq!(
            result.root[format!("step_{i}")],
            JsonValue::Bool(true),
            "step_{i} should be set"
        );
    }
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[test]
fn down_scale_traffic_byte_range() {
    assert_eq!(down_scale_traffic(0), 0);
    assert_eq!(down_scale_traffic(512), 512);
    assert_eq!(down_scale_traffic(1024), 1024);
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[test]
fn down_scale_traffic_kb_range() {
    let tag_kb = 1u64 << 30;
    let val = down_scale_traffic(2048);
    assert_eq!(val & 0xC000_0000, tag_kb);
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[test]
fn down_scale_traffic_mb_range() {
    let tag_mb = 2u64 << 30;
    let val = down_scale_traffic(5 * 1024 * 1024);
    assert_eq!(val & 0xC000_0000, tag_mb);
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[test]
fn down_scale_traffic_gb_range() {
    let tag_gb = 3u64 << 30;
    let val = down_scale_traffic(2u64 * 1024 * 1024 * 1024);
    assert_eq!(val & 0xC000_0000, tag_gb);
}

#[cfg(any(target_os = "linux", target_os = "android"))]
#[test]
fn pack_traffic_encodes_both_directions() {
    let packed = pack_traffic(2048, 4096);
    let bits = packed as u64;
    assert_eq!((bits >> 32) & 0xC000_0000, 1u64 << 30);
    assert_eq!(bits & 0xC000_0000, 1u64 << 30);
}
