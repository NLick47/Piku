use std::sync::Arc;
use std::time::Duration;

use bytes::Bytes;
use h2::client;
use http::Request as HttpRequest;
use tokio::net::TcpStream;
use tokio_rustls::TlsConnector;

const MAX_REDIRECTS: usize = 5;
const CONNECT_TIMEOUT: Duration = Duration::from_secs(10);

pub struct FetchSpec {
    pub url: String,
    pub method: String,
    pub headers: Vec<(String, String)>,
    pub body: Option<Vec<u8>>,
    pub ip: String,
    pub ech_config: Vec<u8>,
    pub timeout_ms: u64,
}

pub struct FetchResult {
    pub status: u16,
    pub headers: Vec<(String, String)>,
    pub body: Vec<u8>,
    /// 握手后 rustls 报的 ECH 状态（Accepted / Rejected / NotOffered）
    pub ech: String,
}

struct Url {
    host: String,
    port: u16,
    path_and_query: String,
}

fn parse_url(raw: &str) -> Result<Url, String> {
    let rest = raw
        .strip_prefix("https://")
        .ok_or_else(|| format!("只支持 https 地址: {raw}"))?;
    let (authority, path) = match rest.find('/') {
        Some(i) => (&rest[..i], &rest[i..]),
        None => (rest, "/"),
    };
    let (host, port) = match authority.rsplit_once(':') {
        Some((h, p)) => (
            h.to_string(),
            p.parse::<u16>().map_err(|_| format!("端口不合法: {p}"))?,
        ),
        None => (authority.to_string(), 443),
    };
    if host.is_empty() {
        return Err("地址里没有主机名".into());
    }
    Ok(Url {
        host,
        port,
        path_and_query: path.to_string(),
    })
}

/// 把 Location 解析成目标地址（绝对 / 站内绝对路径 / 相对路径）
fn resolve_location(location: &str, from: &Url) -> Result<Url, String> {
    if location.starts_with("https://") {
        return parse_url(location);
    }
    if location.starts_with("//") {
        return parse_url(&format!("https:{location}"));
    }
    if location.starts_with('/') {
        return Ok(Url {
            host: from.host.clone(),
            port: from.port,
            path_and_query: location.to_string(),
        });
    }
    let base = match from.path_and_query.rfind('/') {
        Some(i) => &from.path_and_query[..=i],
        None => "/",
    };
    Ok(Url {
        host: from.host.clone(),
        port: from.port,
        path_and_query: format!("{base}{location}"),
    })
}

/// 同步入口：JNI 侧直接调它
pub fn fetch(spec: &FetchSpec) -> Result<FetchResult, String> {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|e| format!("运行时初始化失败: {e}"))?;
    runtime.block_on(fetch_async(spec))
}

async fn fetch_async(spec: &FetchSpec) -> Result<FetchResult, String> {
    let config = crate::tls::client_config(&spec.ech_config)?;
    let timeout = Duration::from_millis(spec.timeout_ms.max(1000));
    let mut url = parse_url(&spec.url)?;
    let mut method = spec.method.to_uppercase();
    let mut body = spec.body.clone();
    let mut cookies: Vec<(String, String)> = Vec::new();

    for _ in 0..=MAX_REDIRECTS {
        let result = tokio::time::timeout(
            timeout,
            round(
                &config,
                &spec.ip,
                &url,
                &method,
                &spec.headers,
                body.as_deref(),
                &cookies,
            ),
        )
        .await
        .map_err(|_| format!("请求超时（{} ms）", spec.timeout_ms))??;

        for (name, value) in &result.headers {
            if name.eq_ignore_ascii_case("set-cookie") {
                if let Some(pair) = value.split(';').next() {
                    let pair = pair.trim();
                    if let Some((k, v)) = pair.split_once('=') {
                        cookies.retain(|(ck, _)| ck != k);
                        cookies.push((k.to_string(), v.to_string()));
                    }
                }
            }
        }

        if !(300..400).contains(&result.status) {
            return Ok(result);
        }
        let location = result
            .headers
            .iter()
            .find(|(name, _)| name.eq_ignore_ascii_case("location"))
            .map(|(_, value)| value.clone());
        let Some(location) = location else {
            return Ok(result);
        };
        url = resolve_location(&location, &url)?;
        if downgrades_to_get(result.status, &method) {
            method = "GET".to_string();
            body = None;
        }
    }
    Err(format!("重定向超过 {MAX_REDIRECTS} 次"))
}

/// 303 一律转 GET；301/302 对非 GET 也转 GET（和浏览器一致；307/308 保留方法与请求体）
fn downgrades_to_get(status: u16, method: &str) -> bool {
    status == 303 || ((status == 301 || status == 302) && method != "GET" && method != "HEAD")
}

#[allow(clippy::too_many_arguments)]
async fn round(
    config: &Arc<rustls::ClientConfig>,
    ip: &str,
    url: &Url,
    method: &str,
    headers: &[(String, String)],
    body: Option<&[u8]>,
    cookies: &[(String, String)],
) -> Result<FetchResult, String> {
    let addr = format!("{}:{}", ip, url.port);
    let tcp = tokio::time::timeout(CONNECT_TIMEOUT, TcpStream::connect(&addr))
        .await
        .map_err(|_| format!("连接 {addr} 超时"))?
        .map_err(|e| format!("连接 {addr} 失败: {e}"))?;
    tcp.set_nodelay(true).ok();

    let name = crate::tls::server_name(&url.host)?;
    let tls = TlsConnector::from(config.clone())
        .connect(name, tcp)
        .await
        .map_err(|e| format!("TLS 握手失败: {e}"))?;
    let (_, connection) = tls.get_ref();
    let ech = crate::tls::ech_status(connection);
    let alpn = connection
        .alpn_protocol()
        .map(|p| String::from_utf8_lossy(p).to_string())
        .unwrap_or_else(|| "无".into());

    let mut builder = HttpRequest::builder()
        .method(method)
        .uri(format!("https://{}{}", url.host, url.path_and_query));
    for (name, value) in headers {
        // HTTP/2 里主机名走 :authority，再带一个 host 头会被判协议错误
        if name.eq_ignore_ascii_case("host") {
            continue;
        }
        builder = builder.header(name.as_str(), value.as_str());
    }
    if !cookies.is_empty() {
        let cookie = cookies
            .iter()
            .map(|(k, v)| format!("{k}={v}"))
            .collect::<Vec<_>>()
            .join("; ");
        builder = builder.header("cookie", cookie);
    }
    let request = builder
        .body(())
        .map_err(|e| format!("请求构造失败: {e}"))?;

    let (mut sender, h2_connection) = client::handshake(tls)
        .await
        .map_err(|e| format!("[ECH={ech} ALPN={alpn}] HTTP/2 握手失败: {e}"))?;
    // 连接状态机必须并行驱动，否则 SETTINGS 发不出去
    let driver = tokio::spawn(async move {
        let _ = h2_connection.await;
    });

    let outcome = async {
        let (response, mut stream) = sender
            .send_request(request, body.is_none())
            .map_err(|e| format!("发送请求失败: {e}"))?;
        if let Some(bytes) = body {
            stream
                .send_data(Bytes::copy_from_slice(bytes), true)
                .map_err(|e| format!("发送请求体失败: {e}"))?;
        }
        let response = response.await.map_err(|e| format!("读取响应头失败: {e}"))?;
        let status = response.status().as_u16();
        let headers = response
            .headers()
            .iter()
            .map(|(k, v)| (k.as_str().to_string(), v.to_str().unwrap_or("").to_string()))
            .collect::<Vec<_>>();
        let mut body = response.into_body();
        let mut out = Vec::new();
        while let Some(chunk) = body.data().await {
            let chunk = chunk.map_err(|e| format!("读取响应体失败: {e}"))?;
            let _ = body.flow_control().release_capacity(chunk.len());
            out.extend_from_slice(&chunk);
        }
        Ok::<FetchResult, String>(FetchResult {
            status,
            headers,
            body: out,
            ech: ech.clone(),
        })
    }
    .await;

    driver.abort();
    outcome.map_err(|e| format!("[ECH={ech} ALPN={alpn}] {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_https_urls() {
        let url = parse_url("https://www.pixiv.net/ajax/illust/123?lang=zh").unwrap();
        assert_eq!("www.pixiv.net", url.host);
        assert_eq!(443, url.port);
        assert_eq!("/ajax/illust/123?lang=zh", url.path_and_query);

        let bare = parse_url("https://example.com").unwrap();
        assert_eq!("/", bare.path_and_query);
    }

    #[test]
    fn rejects_plain_http() {
        assert!(parse_url("http://example.com/").is_err());
    }

    #[test]
    fn resolves_redirect_targets() {
        let from = parse_url("https://www.pixiv.net/ajax/illust/1").unwrap();
        assert_eq!(
            "/v1/illus",
            resolve_location("/v1/illus", &from).unwrap().path_and_query
        );
        assert_eq!(
            "www.pixiv.net",
            resolve_location("https://www.pixiv.net/x", &from).unwrap().host
        );
        assert_eq!(
            "/ajax/illust/sibling",
            resolve_location("sibling", &from).unwrap().path_and_query
        );
    }

    #[test]
    fn redirect_status_decides_whether_the_method_survives() {
        assert!(downgrades_to_get(303, "POST"));
        assert!(downgrades_to_get(301, "POST"));
        assert!(downgrades_to_get(302, "POST"));
        assert!(!downgrades_to_get(307, "POST"));
        assert!(!downgrades_to_get(308, "POST"));
        assert!(!downgrades_to_get(302, "GET"));
        assert!(!downgrades_to_get(301, "HEAD"));
    }
}
