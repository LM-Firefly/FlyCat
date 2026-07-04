use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::JNIEnv;

use crate::compiler::compile_request;
use crate::model::{CompileRequest, CompileResult};

#[no_mangle]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Bridge_nativeCompilePreview(
    mut env: JNIEnv,
    _bridge: JObject,
    request_json: JString,
) -> jstring {
    handle_compile_request(&mut env, request_json)
}

fn handle_compile_request(env: &mut JNIEnv, request_json: JString) -> jstring {
    let payload = match env.get_string(&request_json) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(err) => {
            let _ = env.exception_clear();
            return new_java_string(env, error_result(format!("read JNI request: {err}")));
        }
    };

    let result = match serde_json::from_str::<CompileRequest>(&payload) {
        Ok(request) => compile_request(request, false),
        Err(err) => Err(format!("decode override request: {err}")),
    };

    let response_json = match result {
        Ok(result) => encode_result(result),
        Err(err) => error_result(err),
    };
    new_java_string(env, response_json)
}

fn encode_result(result: CompileResult) -> String {
    serde_json::to_string(&result).unwrap_or_else(|_| {
        "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override result encode failed\"}".to_string()
    })
}

fn error_result(message: impl Into<String>) -> String {
    serde_json::to_string(&CompileResult {
        success: false,
        fingerprint: String::new(),
        final_yaml: String::new(),
        warnings: Vec::new(),
        error: Some(message.into()),
    })
    .unwrap_or_else(|_| {
        "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override processor failed\"}".to_string()
    })
}

fn new_java_string(env: &mut JNIEnv, content: String) -> jstring {
    env.new_string(content)
        .expect("create JNI response string")
        .into_raw()
}
