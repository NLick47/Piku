//! 主机侧端到端验证：读一份 ECHConfigList（二进制文件）→ 带 ECH 打 pixiv
//! 用法: cargo run --release --example probe -- <ip> <path> <ech_config_file>

use piku_ech::http2::{fetch, FetchSpec};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let ip = args.get(1).cloned().unwrap_or_else(|| "172.64.145.17".into());
    let path = args
        .get(2)
        .cloned()
        .unwrap_or_else(|| "/ajax/illust/80000000".into());
    let Some(config_path) = args.get(3) else {
        eprintln!("用法: probe <ip> <path> <ech_config_file>");
        std::process::exit(2);
    };
    let ech_config = std::fs::read(config_path).expect("读 ECH 配置失败");
    println!("[ech] 配置 {} 字节", ech_config.len());

    let result = fetch(&FetchSpec {
        url: format!("https://www.pixiv.net{path}"),
        method: "GET".into(),
        headers: vec![
            ("user-agent".into(), "PixivIOSApp/5.8.0".into()),
            ("referer".into(), "https://www.pixiv.net/".into()),
            ("accept".into(), "application/json".into()),
        ],
        body: None,
        ip,
        ech_config,
        timeout_ms: 30_000,
    });
    match result {
        Ok(r) => {
            println!("[pixiv] ECH={} HTTP={} 长度={}", r.ech, r.status, r.body.len());
            let body = String::from_utf8_lossy(&r.body);
            let limit = if std::env::var("PIKU_PROBE_FULL").is_ok() { body.len() } else { body.len().min(300) };
            println!("[body] {}", &body[..limit]);
        }
        Err(e) => println!("[pixiv] 失败: {e}"),
    }
}
