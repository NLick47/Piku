//! 通用探针：拿一份 ECH 配置打任意主机（pixiv 专用那条见 probe.rs）
//! 用法: cargo run --release --example probe_host -- <ip> <url> <ech_config_file> [name:value ...]

use piku_ech::http2::{fetch, FetchSpec};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let (Some(ip), Some(url), Some(config_path)) = (args.get(1), args.get(2), args.get(3)) else {
        eprintln!("用法: probe_host <ip> <url> <ech_config_file> [name:value ...]");
        std::process::exit(2);
    };
    let ech_config = std::fs::read(config_path).expect("读 ECH 配置失败");
    println!(
        "[ech] 配置 {} 字节{}",
        ech_config.len(),
        if ech_config.is_empty() { "（空 = 不带 ECH）" } else { "" }
    );

    // 主机侧没有 AndroidCAStore，用内置根集；App 侧走系统库（见 tls.rs）
    let mut store = rustls::RootCertStore::empty();
    store.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    piku_ech::tls::install_roots(store).expect("装根证书失败");

    let mut headers = vec![
        ("user-agent".to_string(), "Piku/0.1".to_string()),
        ("accept".to_string(), "*/*".to_string()),
    ];
    for raw in &args[4..] {
        match raw.split_once(':') {
            Some((name, value)) => headers.push((name.to_string(), value.to_string())),
            None => {
                eprintln!("请求头要写成 name:value: {raw}");
                std::process::exit(2);
            }
        }
    }

    let started = std::time::Instant::now();
    let result = fetch(&FetchSpec {
        url: url.clone(),
        method: "GET".into(),
        headers,
        body: None,
        ip: ip.clone(),
        ech_config,
        timeout_ms: 20_000,
    });
    let elapsed = started.elapsed().as_millis();
    match result {
        Ok(r) => {
            println!(
                "[{} @ {}] ECH={} HTTP={} 长度={} 用时={}ms",
                url, ip, r.ech, r.status, r.body.len(), elapsed
            );
            // 正文落盘：页面类响应前 200 字只有 <!DOCTYPE，看不出结构
            let dump = "/data/local/tmp/probe_body.bin";
            if std::fs::write(dump, &r.body).is_ok() {
                println!("[dump] {dump}");
            }
            let body = String::from_utf8_lossy(&r.body);
            let head = &body[..body.len().min(200)];
            println!("[body] {}", head.replace('\n', " "));
        }
        Err(e) => println!("[{} @ {}] 失败({elapsed}ms): {e}", url, ip),
    }
}
