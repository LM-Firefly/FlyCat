use std::collections::BTreeMap;
use std::fs::{self, OpenOptions};
use std::io::{Read, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};

use boa_engine::builtins::promise::PromiseState;
use boa_engine::object::builtins::JsPromise;
use boa_engine::object::FunctionObjectBuilder;
use boa_engine::property::Attribute;
use boa_engine::{js_string, Context, JsNativeError, JsValue, NativeFunction, Source};
use serde::{Deserialize, Serialize};
use serde_json::Value as JsonValue;

use crate::model::LoadedOverride;

pub struct JsOverrideOutcome {
    pub root: JsonValue,
    pub warnings: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FetchRequest {
    url: String,
    #[serde(default = "default_fetch_method")]
    method: String,
    #[serde(default)]
    headers: BTreeMap<String, String>,
    #[serde(default)]
    body: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct FetchResponsePayload {
    ok: bool,
    status: u16,
    status_text: String,
    url: String,
    headers: BTreeMap<String, String>,
    body: String,
}

pub fn apply_js_override(
    root: JsonValue,
    override_item: &LoadedOverride,
    encrypted: bool,
) -> JsOverrideOutcome {
    let log_path = override_log_path(&override_item.path);
    let mut warnings = Vec::new();
    if let Err(err) = reset_override_log(&log_path) {
        let warning = if encrypted {
            format!("initialize JS override log failed for encrypted profile: {err}")
        } else {
            format!(
                "initialize JS override log {} failed: {err}",
                log_path.to_string_lossy()
            )
        };
        warnings.push(warning);
    }
    let _ = append_override_log(&log_path, "info", "开始执行脚本");

    let original_root = root.clone();
    match try_apply_js_override(root, override_item, &log_path, encrypted) {
        Ok(next_root) => {
            let _ = append_override_log(&log_path, "info", "脚本执行成功");
            JsOverrideOutcome {
                root: next_root,
                warnings,
            }
        }
        Err(err) => {
            let failure_message = if encrypted {
                "脚本执行失败：(redacted, encrypted profile)".to_string()
            } else {
                format!("脚本执行失败：{err}")
            };
            let warning = if encrypted {
                "skip JS override: redacted error for encrypted profile".to_string()
            } else {
                format!("skip JS override {}: {err}", override_item.path)
            };
            let _ = append_override_log(&log_path, "exception", &failure_message);
            warnings.push(warning);
            JsOverrideOutcome {
                root: original_root,
                warnings,
            }
        }
    }
}

fn js_string_value(value: &str) -> JsValue {
    JsValue::new(js_string!(value))
}

fn try_apply_js_override(
    root: JsonValue,
    override_item: &LoadedOverride,
    log_path: &Path,
    encrypted: bool,
) -> Result<JsonValue, String> {
    let profile_json =
        serde_json::to_string(&root).map_err(|err| format!("encode profile payload: {err}"))?;
    let mut context = Context::default();

    register_native_helpers(&mut context)?;
    context
        .register_global_property(
            js_string!("__profileJson"),
            js_string_value(&profile_json),
            Attribute::all(),
        )
        .map_err(|err| format!("register __profileJson failed: {err}"))?;
    context
        .register_global_property(
            js_string!("__overridePath"),
            js_string_value(&override_item.path),
            Attribute::all(),
        )
        .map_err(|err| format!("register __overridePath failed: {err}"))?;
    context
        .register_global_property(
            js_string!("__overrideLogPath"),
            js_string_value(log_path.to_string_lossy().as_ref()),
            Attribute::all(),
        )
        .map_err(|err| format!("register __overrideLogPath failed: {err}"))?;
    context
        .register_global_property(
            js_string!("__encrypted"),
            js_string_value(if encrypted { "true" } else { "false" }),
            Attribute::all(),
        )
        .map_err(|err| format!("register __encrypted failed: {err}"))?;

    evaluate(&mut context, helper_script(), "override helper")?;
    evaluate(&mut context, &override_item.content, &override_item.path)?;
    let result = evaluate(
        &mut context,
        r#"
(() => {
  if (typeof main !== "function") {
    throw new Error("JS override must define main(profile)");
  }
  return main(JSON.parse(__profileJson));
})()
"#,
        "invoke main(profile)",
    )?;

    let resolved = resolve_main_result(result, &mut context)?;
    let result_json = resolved
        .to_json(&mut context)
        .map_err(|err| format!("convert JS override result to json: {err}"))?
        .ok_or_else(|| "JS override result cannot be converted to json".to_string())?;
    if !result_json.is_object() {
        return Err("JS override result must be an object".to_string());
    }
    Ok(result_json)
}

fn register_native_helpers(context: &mut Context) -> Result<(), String> {
    let yaml_parse = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let payload =
                crate::engine::yaml::parse_yaml_to_json_string(&content).map_err(js_error)?;
            Ok(js_string_value(&payload))
        }),
    )
    .name("__yamlParseNative")
    .length(1)
    .build();
    context
        .register_global_property(
            js_string!("__yamlParseNative"),
            yaml_parse,
            Attribute::all(),
        )
        .map_err(|err| format!("register __yamlParseNative failed: {err}"))?;

    let yaml_stringify = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let payload =
                crate::engine::yaml::stringify_json_to_yaml_string(&content).map_err(js_error)?;
            Ok(js_string_value(&payload))
        }),
    )
    .name("__yamlStringifyNative")
    .length(1)
    .build();
    context
        .register_global_property(
            js_string!("__yamlStringifyNative"),
            yaml_stringify,
            Attribute::all(),
        )
        .map_err(|err| format!("register __yamlStringifyNative failed: {err}"))?;

    let b64d = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let decoded = base64_decode_string(&content).map_err(js_error)?;
            Ok(js_string_value(&decoded))
        }),
    )
    .name("__b64dNative")
    .length(1)
    .build();
    context
        .register_global_property(js_string!("__b64dNative"), b64d, Attribute::all())
        .map_err(|err| format!("register __b64dNative failed: {err}"))?;

    let b64e = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let encoded = base64_encode_string(content.as_bytes());
            Ok(js_string_value(&encoded))
        }),
    )
    .name("__b64eNative")
    .length(1)
    .build();
    context
        .register_global_property(js_string!("__b64eNative"), b64e, Attribute::all())
        .map_err(|err| format!("register __b64eNative failed: {err}"))?;

    let console_log = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let level = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let message = args
                .get(1)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let _ = append_override_log_from_context(context, &level, &message);
            Ok(JsValue::undefined())
        }),
    )
    .name("__consoleLogNative")
    .length(2)
    .build();
    context
        .register_global_property(
            js_string!("__consoleLogNative"),
            console_log,
            Attribute::all(),
        )
        .map_err(|err| format!("register __consoleLogNative failed: {err}"))?;

    let fetch_native = FunctionObjectBuilder::new(
        context.realm(),
        NativeFunction::from_copy_closure(|_, args, context| {
            let request_json = args
                .get(0)
                .cloned()
                .unwrap_or_default()
                .to_string(context)?
                .to_std_string_escaped();
            let request: FetchRequest =
                serde_json::from_str(&request_json).map_err(|err| js_error(err.to_string()))?;
            let payload = execute_fetch(request).map_err(js_error)?;
            let payload_json =
                serde_json::to_string(&payload).map_err(|err| js_error(err.to_string()))?;
            Ok(js_string_value(&payload_json))
        }),
    )
    .name("__fetchNative")
    .length(1)
    .build();
    context
        .register_global_property(js_string!("__fetchNative"), fetch_native, Attribute::all())
        .map_err(|err| format!("register __fetchNative failed: {err}"))?;

    Ok(())
}

fn evaluate(context: &mut Context, source: &str, label: &str) -> Result<JsValue, String> {
    context.eval(Source::from_bytes(source)).map_err(|err| {
        let detail = err.to_string();
        format!("{label} failed: {detail}")
    })
}

fn helper_script() -> &'static str {
    r#"
const yaml = Object.freeze({
  parse(value) {
    return JSON.parse(__yamlParseNative(String(value)));
  },
  stringify(value) {
    return __yamlStringifyNative(JSON.stringify(value));
  }
});
const trimWrap = (value) => {
  if (typeof value !== "string") {
    return value;
  }
  if (value.startsWith("<") && value.endsWith(">")) {
    return value.slice(1, -1);
  }
  return value;
};
const isObject = (item) => item && typeof item === "object" && !Array.isArray(item);
const deepMerge = (target, other, isOverride = true) => {
  for (const key in other) {
    if (isObject(other[key])) {
      if (key.endsWith("!")) {
        const nextKey = trimWrap(key.slice(0, -1));
        target[nextKey] = other[key];
      } else {
        const nextKey = trimWrap(key);
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: {} });
        }
        deepMerge(target[nextKey], other[key], isOverride);
      }
    } else if (Array.isArray(other[key])) {
      if (isOverride && key.startsWith("+")) {
        const nextKey = trimWrap(key.slice(1));
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: [] });
        }
        target[nextKey] = [...other[key], ...target[nextKey]];
      } else if (isOverride && key.endsWith("+")) {
        const nextKey = trimWrap(key.slice(0, -1));
        if (!target[nextKey]) {
          Object.assign(target, { [nextKey]: [] });
        }
        target[nextKey] = [...target[nextKey], ...other[key]];
      } else {
        const nextKey = trimWrap(key);
        Object.assign(target, { [nextKey]: [...other[key]] });
      }
    } else {
      Object.assign(target, { [key]: other[key] });
    }
  }
  return target;
};
const formatLogValue = (value) => {
  if (__encrypted === "true") {
    return "(redacted, encrypted profile)";
  }
  if (value instanceof Error) {
    return `${value.name}: ${value.message}`;
  }
  try {
    const serialized = JSON.stringify(value);
    const text = serialized === undefined ? String(value) : serialized;
    return text;
  } catch (error) {
    return String(value);
  }
};
const console = Object.freeze({
  log: (...args) => __consoleLogNative("log", args.map(formatLogValue).join(" ")),
  info: (...args) => __consoleLogNative("info", args.map(formatLogValue).join(" ")),
  error: (...args) => __consoleLogNative("error", args.map(formatLogValue).join(" ")),
  debug: (...args) => __consoleLogNative("debug", args.map(formatLogValue).join(" ")),
  warn: (...args) => __consoleLogNative("warn", args.map(formatLogValue).join(" "))
});
const normalizeFetchRequest = (input, init = undefined) => {
  const base = typeof input === "string" ? { url: input } : { ...(input || {}) };
  const requestInit = init ? { ...init } : {};
  const url = String(requestInit.url ?? base.url ?? "");
  if (!url) {
    throw new Error("fetch(url) requires a non-empty url");
  }
  const method = String(requestInit.method ?? base.method ?? "GET").toUpperCase();
  const headers = { ...(base.headers || {}), ...(requestInit.headers || {}) };
  const bodySource = requestInit.body !== undefined ? requestInit.body : base.body;
  const request = { url, method, headers };
  if (bodySource !== undefined) {
    request.body = typeof bodySource === "string" ? bodySource : JSON.stringify(bodySource);
  }
  return request;
};
const createFetchResponse = (payload) => {
  const headerEntries = Object.freeze({ ...(payload.headers || {}) });
  return Object.freeze({
    ok: Boolean(payload.ok),
    status: Number(payload.status || 0),
    statusText: String(payload.statusText || ""),
    url: String(payload.url || ""),
    headers: Object.freeze({
      ...headerEntries,
      get(name) {
        const key = String(name).toLowerCase();
        return headerEntries[key] ?? null;
      },
      has(name) {
        const key = String(name).toLowerCase();
        return key in headerEntries;
      },
      toJSON() {
        return { ...headerEntries };
      }
    }),
    async text() {
      return String(payload.body ?? "");
    },
    async json() {
      return JSON.parse(String(payload.body ?? ""));
    },
    async yaml() {
      return yaml.parse(String(payload.body ?? ""));
    }
  });
};
const fetch = async (input, init = undefined) => {
  const request = normalizeFetchRequest(input, init);
  const payload = JSON.parse(__fetchNative(JSON.stringify(request)));
  return createFetchResponse(payload);
};
const b64d = (value) => __b64dNative(String(value));
const createBuffer = (base64Value) => Object.freeze({
  __isYumeBuffer: true,
  __base64: String(base64Value),
  toString(encoding = "utf8") {
    const normalizedEncoding = String(encoding).toLowerCase();
    if (normalizedEncoding === "base64") {
      return this.__base64;
    }
    if (normalizedEncoding === "utf8" || normalizedEncoding === "utf-8") {
      return __b64dNative(this.__base64);
    }
    throw new Error(`Buffer.toString() unsupported encoding: ${encoding}`);
  },
  valueOf() {
    return this.toString("utf8");
  }
});
const Buffer = Object.freeze({
  from(value, encoding = "utf8") {
    if (value && value.__isYumeBuffer === true && typeof value.__base64 === "string") {
      return createBuffer(value.__base64);
    }
    const normalizedEncoding = String(encoding).toLowerCase();
    if (normalizedEncoding === "base64") {
      return createBuffer(String(value));
    }
    if (normalizedEncoding === "utf8" || normalizedEncoding === "utf-8") {
      return createBuffer(__b64eNative(String(value)));
    }
    throw new Error(`Buffer.from() unsupported encoding: ${encoding}`);
  },
  isBuffer(value) {
    return Boolean(value && value.__isYumeBuffer === true && typeof value.__base64 === "string");
  }
});
const b64e = (value) => Buffer.isBuffer(value) ? value.toString("base64") : __b64eNative(String(value));
globalThis.yaml = yaml;
globalThis.deepMerge = deepMerge;
globalThis.b64d = b64d;
globalThis.b64e = b64e;
globalThis.Buffer = Buffer;
globalThis.console = console;
globalThis.fetch = fetch;
"#
}

fn js_error(message: String) -> boa_engine::JsError {
    JsNativeError::typ().with_message(message).into()
}

fn resolve_main_result(result: JsValue, context: &mut Context) -> Result<JsValue, String> {
    let Some(object) = result.as_object() else {
        return Ok(result);
    };
    let promise = match JsPromise::from_object(object) {
        Ok(promise) => promise,
        Err(_) => return Ok(result),
    };
    for _ in 0..MAX_PROMISE_JOB_PASSES {
        context
            .run_jobs()
            .map_err(|err| format!("run JS promise jobs failed: {err}"))?;
        match promise.state() {
            PromiseState::Pending => continue,
            PromiseState::Fulfilled(value) => return Ok(value),
            PromiseState::Rejected(reason) => {
                let message = reason
                    .to_string(context)
                    .map_err(|err| format!("stringify JS rejection failed: {err}"))?
                    .to_std_string_escaped();
                return Err(format!("JS override rejected: {message}"));
            }
        }
    }
    Err("async main(profile) did not settle".to_string())
}

fn override_log_path(override_path: &str) -> PathBuf {
    Path::new(override_path).with_extension("log")
}

fn reset_override_log(log_path: &Path) -> Result<(), String> {
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    }
    fs::write(log_path, "").map_err(|err| err.to_string())
}

fn append_override_log(log_path: &Path, level: &str, message: &str) -> Result<(), String> {
    let mut file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_path)
        .map_err(|err| err.to_string())?;
    writeln!(file, "[{level}] {message}").map_err(|err| err.to_string())
}

fn append_override_log_from_context(
    context: &mut Context,
    level: &str,
    message: &str,
) -> Result<(), String> {
    let log_path = context
        .global_object()
        .clone()
        .get(js_string!("__overrideLogPath"), context)
        .map_err(|err| format!("read __overrideLogPath failed: {err}"))?
        .to_string(context)
        .map_err(|err| format!("stringify __overrideLogPath failed: {err}"))?
        .to_std_string_escaped();
    append_override_log(Path::new(&log_path), level, message)
}

fn execute_fetch(request: FetchRequest) -> Result<FetchResponsePayload, String> {
    if request.url.trim().is_empty() {
        return Err("fetch request url is empty".to_string());
    }
    let request_url = parse_fetch_url(&request.url)?;
    if request_url.scheme != "http" {
        return Err(format!(
            "fetch only supports http:// urls in the embedded override engine: {}",
            request.url
        ));
    }

    let method = request.method.trim().to_ascii_uppercase();
    let body = request.body.unwrap_or_default();
    let mut stream = TcpStream::connect((request_url.host.as_str(), request_url.port))
        .map_err(|err| format!("fetch {method} {} failed: {err}", request.url))?;
    let mut request_headers = request.headers;
    request_headers
        .entry("host".to_string())
        .or_insert_with(|| request_url.authority.clone());
    request_headers
        .entry("connection".to_string())
        .or_insert_with(|| "close".to_string());
    if !body.is_empty() {
        request_headers
            .entry("content-length".to_string())
            .or_insert_with(|| body.as_bytes().len().to_string());
    }

    let mut raw_request = format!("{method} {} HTTP/1.1\r\n", request_url.path_and_query);
    for (header_name, header_value) in &request_headers {
        raw_request.push_str(&format!("{header_name}: {header_value}\r\n"));
    }
    raw_request.push_str("\r\n");
    raw_request.push_str(&body);

    stream
        .write_all(raw_request.as_bytes())
        .map_err(|err| format!("send fetch request failed: {err}"))?;
    let mut response_bytes = Vec::new();
    stream
        .read_to_end(&mut response_bytes)
        .map_err(|err| format!("read fetch response failed: {err}"))?;
    let (status, status_text, headers, response_body) = parse_http_response(&response_bytes)?;

    Ok(FetchResponsePayload {
        ok: (200..=299).contains(&status),
        status,
        status_text,
        url: request.url,
        headers,
        body: String::from_utf8_lossy(&response_body).into_owned(),
    })
}

fn default_fetch_method() -> String {
    "GET".to_string()
}

struct ParsedFetchUrl {
    scheme: String,
    authority: String,
    host: String,
    port: u16,
    path_and_query: String,
}

fn parse_fetch_url(raw_url: &str) -> Result<ParsedFetchUrl, String> {
    let (scheme, rest) = raw_url
        .split_once("://")
        .ok_or_else(|| format!("invalid fetch url: {raw_url}"))?;
    let scheme = scheme.to_ascii_lowercase();
    let default_port = match scheme.as_str() {
        "http" => 80,
        "https" => 443,
        _ => return Err(format!("unsupported fetch scheme: {scheme}")),
    };
    let (authority, path_and_query) = match rest.find('/') {
        Some(index) => (&rest[..index], &rest[index..]),
        None => (rest, "/"),
    };
    if authority.is_empty() {
        return Err(format!("invalid fetch url authority: {raw_url}"));
    }

    let (host, port) = if authority.starts_with('[') {
        let end = authority
            .find(']')
            .ok_or_else(|| format!("invalid IPv6 fetch authority: {authority}"))?;
        let host = authority[..=end].to_string();
        let port = authority[end + 1..]
            .strip_prefix(':')
            .map(parse_fetch_port)
            .transpose()?
            .unwrap_or(default_port);
        (host, port)
    } else if let Some((host, port)) = authority.rsplit_once(':') {
        if host.contains(':') {
            (authority.to_string(), default_port)
        } else {
            (host.to_string(), parse_fetch_port(port)?)
        }
    } else {
        (authority.to_string(), default_port)
    };

    Ok(ParsedFetchUrl {
        scheme,
        authority: authority.to_string(),
        host,
        port,
        path_and_query: path_and_query.to_string(),
    })
}

fn parse_fetch_port(raw_port: &str) -> Result<u16, String> {
    raw_port
        .parse::<u16>()
        .map_err(|err| format!("invalid fetch port {raw_port}: {err}"))
}

fn parse_http_response(
    response_bytes: &[u8],
) -> Result<(u16, String, BTreeMap<String, String>, Vec<u8>), String> {
    let header_end = response_bytes
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or_else(|| "invalid HTTP response: missing header terminator".to_string())?;
    let header_bytes = &response_bytes[..header_end];
    let body_bytes = &response_bytes[header_end + 4..];
    let header_text = String::from_utf8_lossy(header_bytes);
    let mut lines = header_text.lines();
    let status_line = lines
        .next()
        .ok_or_else(|| "invalid HTTP response: missing status line".to_string())?;
    let mut status_parts = status_line.splitn(3, ' ');
    let _ = status_parts.next();
    let status = status_parts
        .next()
        .ok_or_else(|| "invalid HTTP response: missing status code".to_string())?
        .parse::<u16>()
        .map_err(|err| format!("invalid HTTP status code: {err}"))?;
    let status_text = status_parts.next().unwrap_or_default().trim().to_string();
    let mut headers = BTreeMap::new();
    for line in lines {
        if let Some((name, value)) = line.split_once(':') {
            headers.insert(name.trim().to_ascii_lowercase(), value.trim().to_string());
        }
    }

    let body = if headers
        .get("transfer-encoding")
        .map(|value| value.eq_ignore_ascii_case("chunked"))
        .unwrap_or(false)
    {
        decode_chunked_body(body_bytes)?
    } else {
        body_bytes.to_vec()
    };

    Ok((status, status_text, headers, body))
}

fn decode_chunked_body(body_bytes: &[u8]) -> Result<Vec<u8>, String> {
    let mut decoded = Vec::new();
    let mut cursor = 0usize;
    while cursor < body_bytes.len() {
        let size_end = body_bytes[cursor..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .map(|index| cursor + index)
            .ok_or_else(|| "invalid chunked response: missing chunk size terminator".to_string())?;
        let size_text = String::from_utf8_lossy(&body_bytes[cursor..size_end]);
        let chunk_size = usize::from_str_radix(size_text.trim(), 16)
            .map_err(|err| format!("invalid chunk size: {err}"))?;
        cursor = size_end + 2;
        if chunk_size == 0 {
            break;
        }
        let chunk_end = cursor + chunk_size;
        if chunk_end > body_bytes.len() {
            return Err("invalid chunked response: chunk exceeds body length".to_string());
        }
        decoded.extend_from_slice(&body_bytes[cursor..chunk_end]);
        cursor = chunk_end + 2;
    }
    Ok(decoded)
}

fn base64_encode_string(data: &[u8]) -> String {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut encoded = String::new();
    let mut index = 0;
    while index < data.len() {
        let first = data[index];
        let second = if index + 1 < data.len() {
            data[index + 1]
        } else {
            0
        };
        let third = if index + 2 < data.len() {
            data[index + 2]
        } else {
            0
        };
        let value = ((first as u32) << 16) | ((second as u32) << 8) | (third as u32);
        encoded.push(TABLE[((value >> 18) & 0x3F) as usize] as char);
        encoded.push(TABLE[((value >> 12) & 0x3F) as usize] as char);
        encoded.push(if index + 1 < data.len() {
            TABLE[((value >> 6) & 0x3F) as usize] as char
        } else {
            '='
        });
        encoded.push(if index + 2 < data.len() {
            TABLE[(value & 0x3F) as usize] as char
        } else {
            '='
        });
        index += 3;
    }
    encoded
}

fn base64_decode_string(content: &str) -> Result<String, String> {
    let mut bytes = Vec::new();
    let mut chunk = [0u8; 4];
    let mut chunk_len = 0usize;
    for byte in content.bytes().filter(|value| !value.is_ascii_whitespace()) {
        chunk[chunk_len] = byte;
        chunk_len += 1;
        if chunk_len == 4 {
            decode_base64_chunk(&chunk, &mut bytes)?;
            chunk_len = 0;
        }
    }
    if chunk_len != 0 {
        return Err("invalid base64 padding".to_string());
    }
    String::from_utf8(bytes).map_err(|err| err.to_string())
}

fn decode_base64_chunk(chunk: &[u8; 4], bytes: &mut Vec<u8>) -> Result<(), String> {
    let mut values = [0u8; 4];
    let mut padding = 0usize;
    for (index, item) in chunk.iter().enumerate() {
        values[index] = match item {
            b'A'..=b'Z' => item - b'A',
            b'a'..=b'z' => item - b'a' + 26,
            b'0'..=b'9' => item - b'0' + 52,
            b'+' => 62,
            b'/' => 63,
            b'=' => {
                padding += 1;
                0
            }
            _ => return Err(format!("invalid base64 character: {}", *item as char)),
        };
    }
    let combined = ((values[0] as u32) << 18)
        | ((values[1] as u32) << 12)
        | ((values[2] as u32) << 6)
        | values[3] as u32;
    bytes.push(((combined >> 16) & 0xFF) as u8);
    if padding < 2 {
        bytes.push(((combined >> 8) & 0xFF) as u8);
    }
    if padding < 1 {
        bytes.push((combined & 0xFF) as u8);
    }
    Ok(())
}

const MAX_PROMISE_JOB_PASSES: usize = 1024;
