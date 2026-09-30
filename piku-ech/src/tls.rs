use std::sync::{Arc, OnceLock};

use rustls::client::{EchConfig, EchMode};
use rustls::pki_types::{EchConfigListBytes, ServerName};
use rustls::RootCertStore;

use crate::hpke::ECH_HPKE_SUITE;

fn roots() -> Arc<RootCertStore> {
    static ROOTS: OnceLock<Arc<RootCertStore>> = OnceLock::new();
    ROOTS
        .get_or_init(|| {
            let mut store = RootCertStore::empty();
            store.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
            Arc::new(store)
        })
        .clone()
}

/// 每个 ECH 配置对应一个 ClientConfig（配置本身烧在 config 里），所以按次构建
pub fn client_config(ech_config: &[u8]) -> Result<Arc<rustls::ClientConfig>, String> {
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let builder = rustls::ClientConfig::builder_with_provider(provider);
    let mut config = if ech_config.is_empty() {
        // 取 ECH 配置本身（AliDNS）时不带 ECH
        builder
            .with_safe_default_protocol_versions()
            .map_err(|e| format!("TLS 版本初始化失败: {e:?}"))?
            .with_root_certificates(roots())
            .with_no_client_auth()
    } else {
        let ech = EchConfig::new(EchConfigListBytes::from(ech_config.to_vec()), ECH_HPKE_SUITE)
            .map_err(|e| format!("ECH 配置不可用: {e:?}"))?;
        builder
            .with_ech(EchMode::from(ech))
            .map_err(|e| format!("ECH 初始化失败: {e:?}"))?
            .with_root_certificates(roots())
            .with_no_client_auth()
    };
    // ECH 前端门只认 h2/h3；实测 HTTP/1.1 会被拒（少数带 ? 的路径才被放行）
    config.alpn_protocols = vec![b"h2".to_vec()];
    Ok(Arc::new(config))
}

pub fn server_name(host: &str) -> Result<ServerName<'static>, String> {
    ServerName::try_from(host.to_string()).map_err(|e| format!("域名不合法: {e}"))
}

/// 握手是否走了 ECH（Accepted 才算真的把内层名字藏住了）
pub fn ech_status(conn: &rustls::ClientConnection) -> String {
    format!("{:?}", conn.ech_status())
}
