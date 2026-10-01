use std::time::Duration;

use bytes::Bytes;
use h2::client;
use http::Request as HttpRequest;
use rustls::internal::msgs::codec::Codec;
use rustls::internal::msgs::handshake::EchConfigPayload;
use tokio::net::TcpStream;
use tokio_rustls::client::TlsStream;
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
    let timeout = Duration::from_millis(spec.timeout_ms.max(1000));
    let mut url = parse_url(&spec.url)?;
    let mut method = spec.method.to_uppercase();
    let mut body = spec.body.clone();
    let mut cookies: Vec<(String, String)> = Vec::new();

    for _ in 0..=MAX_REDIRECTS {
        let result = tokio::time::timeout(
            timeout,
            round(
                &spec.ech_config,
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
    ech_config: &[u8],
    ip: &str,
    url: &Url,
    method: &str,
    headers: &[(String, String)],
    body: Option<&[u8]>,
    cookies: &[(String, String)],
) -> Result<FetchResult, String> {
    let (tls, ech, alpn) = handshake(ech_config, ip, url).await?;

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

async fn handshake(
    ech_config: &[u8],
    ip: &str,
    url: &Url,
) -> Result<(TlsStream<TcpStream>, String, String), String> {
    match connect(ech_config, ip, url).await {
        Err(Refusal::EchRejected(retry)) => {
            connect(&retry, ip, url).await.map_err(|e| e.to_string())
        }
        Ok(ok) => Ok(ok),
        Err(other) => Err(other.to_string()),
    }
}

/// 握手被拒的原因，只有 ECH 那一种值得换配置重连
enum Refusal {
    EchRejected(Vec<u8>),
    Other(String),
}

impl std::fmt::Display for Refusal {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::EchRejected(_) => write!(f, "服务端拒绝了 ECH 配置"),
            Self::Other(message) => write!(f, "{message}"),
        }
    }
}

async fn connect(
    ech_config: &[u8],
    ip: &str,
    url: &Url,
) -> Result<(TlsStream<TcpStream>, String, String), Refusal> {
    let addr = format!("{}:{}", ip, url.port);
    let tcp = tokio::time::timeout(CONNECT_TIMEOUT, TcpStream::connect(&addr))
        .await
        .map_err(|_| Refusal::Other(format!("连接 {addr} 超时")))?
        .map_err(|e| Refusal::Other(format!("连接 {addr} 失败: {e}")))?;
    tcp.set_nodelay(true).ok();

    let config = crate::tls::client_config(ech_config).map_err(Refusal::Other)?;
    let name = crate::tls::server_name(&url.host).map_err(Refusal::Other)?;
    let tls = TlsConnector::from(config)
        .connect(name, tcp)
        .await
        .map_err(|e| match retry_configs(&e) {
            Some(retry) => Refusal::EchRejected(retry),
            None => Refusal::Other(format!("TLS 握手失败: {e}")),
        })?;
    let (_, connection) = tls.get_ref();
    let ech = crate::tls::ech_status(connection);
    let alpn = connection
        .alpn_protocol()
        .map(|p| String::from_utf8_lossy(p).to_string())
        .unwrap_or_else(|| "无".into());
    Ok((tls, ech, alpn))
}

/// rustls 遇到 retry_configs 直接把握手判死，这里把它抠出来自己用
fn retry_configs(error: &std::io::Error) -> Option<Vec<u8>> {
    let rejected = error.get_ref()?.downcast_ref::<rustls::Error>()?;
    let rustls::Error::PeerIncompatible(
        rustls::PeerIncompatible::ServerRejectedEncryptedClientHello(configs),
    ) = rejected
    else {
        return None;
    };
    encode_config_list(configs.as_deref()?)
}

fn encode_config_list(configs: &[EchConfigPayload]) -> Option<Vec<u8>> {
    let mut inner = Vec::new();
    for config in configs {
        config.encode(&mut inner);
    }
    if inner.is_empty() || inner.len() > u16::MAX as usize {
        return None;
    }
    let mut out = Vec::with_capacity(inner.len() + 2);
    out.extend_from_slice(&(inner.len() as u16).to_be_bytes());
    out.extend_from_slice(&inner);
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use rustls::internal::msgs::codec::Reader;
    use rustls::PeerIncompatible;

    /// 一次真实抓取的 ECHConfigList（AliDNS 的 cloudflare-ech.com 记录，base64 解出来）
    const REAL_CONFIG: &str = "00 45 fe 0d 00 41 e3 00 20 00 20 b9 ba 0d e1 16 89 dd 01 58 a0 4d \
         ce 09 00 76 91 9d 9a 1e 14 51 c9 27 b4 e6 ff 90 66 8b fc eb 15 00 04 00 01 00 01 00 \
         12 63 6c 6f 75 64 66 6c 61 72 65 2d 65 63 68 2e 63 6f 6d 00 00";

    fn real_config() -> Vec<u8> {
        REAL_CONFIG
            .split_whitespace()
            .map(|byte| u8::from_str_radix(byte, 16).unwrap())
            .collect()
    }

    fn real_payloads() -> Vec<EchConfigPayload> {
        let bytes = real_config();
        Vec::<EchConfigPayload>::read(&mut Reader::init(&bytes)).unwrap()
    }

    #[test]
    fn re_encodes_a_real_config_list_byte_for_byte() {
        // 服务端给的重试配置就长这样，编回去必须和原样一致，否则重连照样被拒
        assert_eq!(real_config(), encode_config_list(&real_payloads()).unwrap());
    }

    #[test]
    fn picks_retry_configs_out_of_a_rejected_handshake() {
        let error = std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            rustls::Error::PeerIncompatible(
                PeerIncompatible::ServerRejectedEncryptedClientHello(Some(real_payloads())),
            ),
        );

        assert_eq!(real_config(), retry_configs(&error).unwrap());
    }

    #[test]
    fn ignores_rejections_without_configs() {
        let error = std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            rustls::Error::PeerIncompatible(
                PeerIncompatible::ServerRejectedEncryptedClientHello(None),
            ),
        );

        assert!(retry_configs(&error).is_none());
    }

    #[test]
    fn ignores_unrelated_handshake_failures() {
        let error = std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "connection reset");

        assert!(retry_configs(&error).is_none());
    }

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
