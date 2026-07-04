use std::io::{self, Read, Write};

use crate::compiler::compile_request;
use crate::engine::yaml::{parse_yaml_to_json_string, stringify_json_to_yaml_string};
use crate::model::{CliMode, CompileRequest, CompileResult};

pub fn run_cli() -> i32 {
    let mut args = std::env::args().skip(1);
    let mode = match args.next().as_deref() {
        Some("preview") => CliMode::Preview,
        Some("compile") => CliMode::Compile,
        Some("helper") => return run_helper(args.next().as_deref()),
        Some(other) => {
            let _ = writeln!(io::stderr(), "unknown command: {other}");
            let _ = writeln!(io::stderr(), "usage: YumeOverride <preview|compile>");
            return 1;
        }
        None => {
            let _ = writeln!(io::stderr(), "usage: YumeOverride <preview|compile>");
            return 1;
        }
    };

    let mut stdin = String::new();
    if let Err(err) = io::stdin().read_to_string(&mut stdin) {
        let payload = error_result(format!("read stdin: {err}"));
        let _ = writeln!(io::stdout(), "{payload}");
        return 2;
    }

    let result = match serde_json::from_str::<CompileRequest>(&stdin) {
        Ok(request) => compile_request(request, mode == CliMode::Compile),
        Err(err) => Err(format!("decode override request: {err}")),
    };

    let success = result.is_ok();
    let payload = match result {
        Ok(result) => encode_result(result),
        Err(err) => error_result(err),
    };
    let _ = writeln!(io::stdout(), "{payload}");

    if !success && matches!(mode, CliMode::Compile) {
        3
    } else if !success {
        2
    } else {
        0
    }
}

fn run_helper(helper_name: Option<&str>) -> i32 {
    let mut stdin = String::new();
    if let Err(err) = io::stdin().read_to_string(&mut stdin) {
        let _ = writeln!(io::stderr(), "read helper stdin failed: {err}");
        return 1;
    }

    let result = match helper_name {
        Some("yaml-parse") => parse_yaml_to_json_string(&stdin),
        Some("yaml-stringify") => stringify_json_to_yaml_string(&stdin),
        Some(other) => {
            let _ = writeln!(io::stderr(), "unknown helper command: {other}");
            return 1;
        }
        None => {
            let _ = writeln!(
                io::stderr(),
                "usage: YumeOverride helper <yaml-parse|yaml-stringify>"
            );
            return 1;
        }
    };

    match result {
        Ok(payload) => {
            let _ = write!(io::stdout(), "{payload}");
            0
        }
        Err(err) => {
            let _ = writeln!(io::stderr(), "{err}");
            1
        }
    }
}

fn error_result(message: impl Into<String>) -> String {
    serde_json::to_string(&CompileResult {
        success: false,
        fingerprint: String::new(),
        final_yaml: String::new(),
        warnings: Vec::new(),
        error: Some(message.into()),
    })
    .unwrap_or_else(|_| "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override processor failed\"}".to_string())
}

fn encode_result(result: CompileResult) -> String {
    serde_json::to_string(&result)
    .unwrap_or_else(|_| "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override result encode failed\"}".to_string())
}
