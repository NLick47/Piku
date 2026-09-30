pub mod hpke;
pub mod http2;
pub mod tls;

use std::panic::AssertUnwindSafe;

use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jbyteArray, jint};
use jni::JNIEnv;

/// 响应帧：version(1) status(4) headerCount(4) [nameLen(2) name valueLen(2) value]* bodyLen(4) body
const FRAME_VERSION: u8 = 1;

struct Response {
    status: u16,
    headers: Vec<(String, String)>,
    body: Vec<u8>,
}

fn encode(response: Response) -> Vec<u8> {
    let mut out = Vec::with_capacity(32 + response.body.len());
    out.push(FRAME_VERSION);
    out.extend_from_slice(&(response.status as u32).to_be_bytes());
    out.extend_from_slice(&(response.headers.len() as u32).to_be_bytes());
    for (name, value) in &response.headers {
        let name = name.as_bytes();
        let value = value.as_bytes();
        out.extend_from_slice(&(name.len() as u16).to_be_bytes());
        out.extend_from_slice(name);
        out.extend_from_slice(&(value.len() as u16).to_be_bytes());
        out.extend_from_slice(value);
    }
    out.extend_from_slice(&(response.body.len() as u32).to_be_bytes());
    out.extend_from_slice(&response.body);
    out
}

#[no_mangle]
pub extern "system" fn Java_com_piku_client_data_remote_ech_NativeEch_fetch<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    url: JString<'local>,
    method: JString<'local>,
    headers: JObjectArray<'local>,
    body: JByteArray<'local>,
    ech_config: JByteArray<'local>,
    ip: JString<'local>,
    timeout_ms: jint,
) -> jbyteArray {
    let outcome = std::panic::catch_unwind(AssertUnwindSafe(|| {
        handle(&mut env, url, method, headers, body, ech_config, ip, timeout_ms)
    }));
    match outcome {
        Ok(Ok(bytes)) => match env.byte_array_from_slice(&bytes) {
            Ok(array) => array.into_raw(),
            Err(e) => {
                throw(&mut env, &format!("返回体写出失败: {e}"));
                std::ptr::null_mut()
            }
        },
        Ok(Err(message)) => {
            throw(&mut env, &message);
            std::ptr::null_mut()
        }
        // 兜底：Rust 侧 panic 不该把 App 带走（profile 里保留了 unwind）
        Err(_) => {
            throw(&mut env, "原生传输内部错误");
            std::ptr::null_mut()
        }
    }
}

fn throw(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/io/IOException", message);
}

#[allow(clippy::too_many_arguments)]
fn handle(
    env: &mut JNIEnv,
    url: JString,
    method: JString,
    headers: JObjectArray,
    body: JByteArray,
    ech_config: JByteArray,
    ip: JString,
    timeout_ms: jint,
) -> Result<Vec<u8>, String> {
    let url = string(env, &url)?;
    let method = string(env, &method)?;
    let ip = string(env, &ip)?;
    let ech_config = if ech_config.is_null() {
        return Err("缺少 ECH 配置".into());
    } else {
        env.convert_byte_array(&ech_config)
            .map_err(|e| format!("读 ECH 配置失败: {e}"))?
    };
    if ech_config.is_empty() {
        return Err("ECH 配置为空".into());
    }
    let body = if body.is_null() {
        None
    } else {
        Some(
            env.convert_byte_array(&body)
                .map_err(|e| format!("读请求体失败: {e}"))?,
        )
    };

    let count = env
        .get_array_length(&headers)
        .map_err(|e| format!("读请求头数量失败: {e}"))?;
    if count % 2 != 0 {
        return Err("请求头必须是 name,value 成对出现".into());
    }
    let mut pairs = Vec::with_capacity((count / 2) as usize);
    for index in (0..count).step_by(2) {
        let name = array_string(env, &headers, index)?;
        let value = array_string(env, &headers, index + 1)?;
        pairs.push((name, value));
    }

    let spec = http2::FetchSpec {
        url,
        method,
        headers: pairs,
        body,
        ip,
        ech_config,
        timeout_ms: if timeout_ms <= 0 {
            30_000
        } else {
            timeout_ms as u64
        },
    };
    let result = http2::fetch(&spec)?;
    Ok(encode(Response {
        status: result.status,
        headers: result.headers,
        body: result.body,
    }))
}

fn string(env: &mut JNIEnv, value: &JString) -> Result<String, String> {
    if value.is_null() {
        return Err("缺少必要参数".into());
    }
    env.get_string(value)
        .map(|s| s.into())
        .map_err(|e| format!("读取字符串失败: {e}"))
}

fn array_string(env: &mut JNIEnv, array: &JObjectArray, index: i32) -> Result<String, String> {
    let element = env
        .get_object_array_element(array, index)
        .map_err(|e| format!("读请求头失败: {e}"))?;
    let text = JString::from(element);
    string(env, &text)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_the_frame_the_kotlin_side_expects() {
        let frame = encode(Response {
            status: 200,
            headers: vec![("content-type".into(), "application/json".into())],
            body: b"{\"ok\":true}".to_vec(),
        });
        assert_eq!(FRAME_VERSION, frame[0]);
        assert_eq!(200u32, u32::from_be_bytes(frame[1..5].try_into().unwrap()));
        assert_eq!(1u32, u32::from_be_bytes(frame[5..9].try_into().unwrap()));
        assert_eq!(12u16, u16::from_be_bytes(frame[9..11].try_into().unwrap()));
        assert_eq!(b"content-type", &frame[11..23]);
        assert_eq!(16u16, u16::from_be_bytes(frame[23..25].try_into().unwrap()));
        assert_eq!(b"application/json", &frame[25..41]);
        let len = u32::from_be_bytes(frame[41..45].try_into().unwrap());
        assert_eq!(b"{\"ok\":true}".len() as u32, len);
        assert_eq!(b"{\"ok\":true}", &frame[45..]);
        assert_eq!(45 + len as usize, frame.len());
    }
}
