// Piku 中转 · Cloudflare Pages Functions
//
// 部署后按路径分三类回源：
//   1. /dns-query      -> https://cloudflare-dns.com/dns-query（RFC8484 原样透传）
//   2. /px/<原路径>     -> https://www.pixiv.net/<原路径>
//   3. 其余 <原路径>    -> https://cdn.poipiku.com/<原路径>（原有图片中转）
//
// 为什么需要这两条新路由（2026-09 实测）：
//   · www.pixiv.net 在 Cloudflare 上。GFW 按 SNI 字符串拦 pixiv/pximg：同一个
//     Cloudflare IP，SNI 换成 poipiku.com 能拿到 301，换成 www.pixiv.net 则 64ms
//     被 RST；而清空 SNI 会被 Cloudflare 拒（ssl alert 40，CF 没有 SNI 无法选 zone）。
//     两头一夹，接口没有直连解法，只能经这里回源。
//   · 系统 DNS 与 alidns 对 pixiv 都返回投毒结果（Facebook 段 IP），DoH 需要一条
//     出境出口；pages.dev 实测国内可达，故用它转发到 Cloudflare 的解析服务。
//
// 客户端改写：
//   图片  https://cdn.poipiku.com/<path> -> https://<项目名>.pages.dev/<path>
//   接口  https://www.pixiv.net/         -> https://<项目名>.pages.dev/px/
//   DoH   https://<项目名>.pages.dev/dns-query
//
// 全功能版以本文件为准（/pxapi 路由、客户端头透传）；piku-image-worker.js 是最小同构版，
// 两份实现改任一处记得对齐另一份。

const IMAGE_UPSTREAM = "https://cdn.poipiku.com";
const IMAGE_REFERER = "https://poipiku.com/";
const PIXIV_UPSTREAM = "https://www.pixiv.net";
const PIXIV_PREFIX = "/px";
/** pixiv 图床：直连只有几十 KB/s（详情页 1–3 MB 的图必然超时），走边缘回源快 6–8 倍 */
const PIXIV_IMG_UPSTREAM = "https://i.pximg.net";
const PIXIV_IMG_PREFIX = "/pximg";
const PIXIV_IMG_REFERER = "https://www.pixiv.net/";
/** 应用接口是另一个 zone，bot 策略未必与主站一致，单独开一条便于对比 */
const PIXIV_APP_UPSTREAM = "https://app-api.pixiv.net";
const PIXIV_APP_PREFIX = "/pxapi";
const DOH_UPSTREAM = "https://cloudflare-dns.com/dns-query";

const CACHE_TTL = 604800; // 图片：内容不可变，7 天
const PIXIV_CACHE_TTL = 300; // 榜单等接口：短缓存，别把过期数据钉住
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,HEAD,POST,OPTIONS",
};
/** 客户端可覆盖/需要透传到 pixiv 的头 */
const FORWARD_HEADERS = [
  "User-Agent",
  "Accept",
  "Authorization",
  "App-OS",
  "App-OS-Version",
  "App-Version",
  "X-Client-Hash",
];
// pixiv 网页接口对 UA 敏感；Referer 必须是站点页。
// 注意：下面这组头**不足以**让 Cloudflare 出口可用 —— 2026-09 实测，带全
// Sec-Fetch-* / sec-ch-ua 与不带，pixiv 回的是同一个 373085 字节的整站封锁页
//（文案「あなたの環境からはpixivにアクセスできません」），说明拦截依据是
// 出口 IP / ASN 而不是指纹。留着是为了让请求形态贴近浏览器，别当成解药。
// 接口要能跑，出口必须是非 Cloudflare 的（自建 VPS / 本地代理）。
const PIXIV_HEADERS = {
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
  "Referer": "https://www.pixiv.net/",
  "Accept-Language": "ja,en-US;q=0.9,en;q=0.8",
  "Accept-Encoding": "gzip, deflate, br",
  "sec-ch-ua": '"Chromium";v="126", "Not)A;Brand";v="24"',
  "sec-ch-ua-mobile": "?0",
  "sec-ch-ua-platform": '"Windows"',
  "Sec-Fetch-Dest": "document",
  "Sec-Fetch-Mode": "navigate",
  "Sec-Fetch-Site": "same-origin",
  "Sec-Fetch-User": "?1",
  "Upgrade-Insecure-Requests": "1",
};

export async function onRequest(context) {
  const { request, waitUntil } = context;
  const url = new URL(request.url);

  if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (url.pathname === "/__health" || url.pathname === "/health") {
    return new Response("piku-relay ok", { status: 200, headers: CORS });
  }
  if (url.pathname === "/dns-query") return doh(request);
  if (url.pathname === PIXIV_APP_PREFIX || url.pathname.startsWith(PIXIV_APP_PREFIX + "/")) {
    return pixiv(request, url, waitUntil, PIXIV_APP_UPSTREAM, PIXIV_APP_PREFIX);
  }
  if (url.pathname === PIXIV_PREFIX || url.pathname.startsWith(PIXIV_PREFIX + "/")) {
    return pixiv(request, url, waitUntil, PIXIV_UPSTREAM, PIXIV_PREFIX);
  }
  if (url.pathname === PIXIV_IMG_PREFIX || url.pathname.startsWith(PIXIV_IMG_PREFIX + "/")) {
    return pixivImage(request, url, waitUntil);
  }
  return image(request, url, waitUntil);
}

/** RFC8484 DoH 透传。不解析 DNS 报文——GET 是 ?dns=<base64url>，POST 是二进制 wireformat */
async function doh(request) {
  const url = new URL(request.url);
  const headers = { Accept: request.headers.get("Accept") || "application/dns-message" };
  const contentType = request.headers.get("Content-Type");
  if (contentType) headers["Content-Type"] = contentType;

  const init = { method: request.method, headers };
  if (request.method !== "GET" && request.method !== "HEAD") init.body = request.body;

  let origin;
  try {
    origin = await fetch(DOH_UPSTREAM + url.search, init);
  } catch (e) {
    return new Response("doh upstream error: " + e.message, { status: 502, headers: CORS });
  }

  // 只透传内容类型：Worker 的 fetch 已经解压过 body，再带上 content-encoding /
  // content-length 会让客户端二次解压失败
  const h = new Headers({
    "Content-Type": origin.headers.get("Content-Type") || "application/dns-message",
    "Cache-Control": "no-store",
  });
  for (const [k, v] of Object.entries(CORS)) h.set(k, v);
  return new Response(origin.body, { status: origin.status, headers: h });
}

async function pixiv(request, url, waitUntil, upstream, prefix) {
  const path = url.pathname.slice(prefix.length) || "/";
  const upstreamUrl = upstream + path + url.search;
  const cookie = request.headers.get("Cookie");
  const auth = request.headers.get("Authorization");
  // 带 Cookie / Authorization 的请求按用户不同，不进共享缓存
  const cacheable = request.method === "GET" && !cookie && !auth;
  const cache = caches.default;
  const cacheKey = new Request(upstreamUrl, { method: "GET" });

  if (cacheable) {
    const hit = await cache.match(cacheKey);
    if (hit) return withCors(hit);
  }

  const headers = { ...PIXIV_HEADERS };
  // 客户端自带的应用侧头要能覆盖默认值——App 接口靠 UA / App-OS 判客户端，
  // 以后带 Authorization 也要透传
  for (const name of FORWARD_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers[name] = value;
  }
  if (cookie) headers["Cookie"] = cookie;

  let origin;
  try {
    origin = await fetch(upstreamUrl, { method: request.method, headers });
  } catch (e) {
    return new Response("pixiv upstream error: " + e.message, { status: 502, headers: CORS });
  }

  const h = new Headers(origin.headers);
  h.delete("content-encoding");
  h.delete("content-length");
  if (origin.ok) {
    h.set("Cache-Control", `public, max-age=${PIXIV_CACHE_TTL}`);
  } else {
    // 错误响应也打长缓存会被边缘/下游钉住，恢复后仍吐错
    h.set("Cache-Control", "no-store");
  }
  const resp = new Response(origin.body, {
    status: origin.status,
    statusText: origin.statusText,
    headers: h,
  });

  // 带 Set-Cookie 的响应是会话相关的，不进共享缓存
  if (cacheable && origin.ok && !origin.headers.get("Set-Cookie")) {
    waitUntil(cache.put(cacheKey, resp.clone()));
  }
  return withCors(resp);
}

/**
 * pixiv 图床回源。
 * 两点与 poipiku 图不同：
 *   · Referer 必须是 pixiv 站点页（图床有防盗链），UA 用普通浏览器串
 *   · **不转发客户端 Cookie** —— 客户端带的是 poipiku 会话，发给 pixiv 既没用也不该发
 * 图片内容不可变，成功响应打 7 天 immutable；错误响应不缓存，免得恢复后还吐错。
 */
async function pixivImage(request, url, waitUntil) {
  const path = url.pathname.slice(PIXIV_IMG_PREFIX.length) || "/";
  const upstreamUrl = PIXIV_IMG_UPSTREAM + path + url.search;
  const cacheable = request.method === "GET" || request.method === "HEAD";
  const cache = caches.default;
  const cacheKey = new Request(upstreamUrl, { method: "GET" });

  if (cacheable) {
    const hit = await cache.match(cacheKey);
    if (hit) return withCors(hit);
  }

  const headers = {
    "User-Agent": "Mozilla/5.0",
    "Referer": PIXIV_IMG_REFERER,
    "Accept": "image/avif,image/webp,image/*,*/*;q=0.8",
  };

  let origin;
  try {
    origin = await fetch(upstreamUrl, { method: request.method, headers });
  } catch (e) {
    return new Response("pximg upstream error: " + e.message, { status: 502, headers: CORS });
  }

  const h = new Headers(origin.headers);
  if (origin.ok) {
    h.set("Cache-Control", `public, max-age=${CACHE_TTL}, immutable`);
  } else {
    h.set("Cache-Control", "no-store");
  }
  const resp = new Response(origin.body, {
    status: origin.status,
    statusText: origin.statusText,
    headers: h,
  });

  if (cacheable && origin.ok) waitUntil(cache.put(cacheKey, resp.clone()));
  return withCors(resp);
}

async function image(request, url, waitUntil) {
  const upstreamUrl = IMAGE_UPSTREAM + url.pathname + url.search;
  const cookie = request.headers.get("Cookie");
  // 带 Cookie 的请求按用户不同，不进共享缓存
  const cacheable = request.method === "GET" && !cookie;
  const cache = caches.default;
  const cacheKey = new Request(upstreamUrl, { method: "GET" });

  if (cacheable) {
    const hit = await cache.match(cacheKey);
    if (hit) return withCors(hit);
  }

  const headers = {
    "User-Agent": "Mozilla/5.0",
    "Referer": IMAGE_REFERER,
    "Accept": "image/avif,image/webp,image/*,*/*;q=0.8",
  };
  if (cookie) headers["Cookie"] = cookie;

  let origin;
  try {
    origin = await fetch(upstreamUrl, { method: request.method, headers });
  } catch (e) {
    return new Response("relay upstream error: " + e.message, { status: 502, headers: CORS });
  }

  const h = new Headers(origin.headers);
  // 只对成功取到的图打长缓存；错误响应也打 7 天 immutable 会被边缘/下游钉住，恢复后仍吐错
  if (origin.ok) {
    h.set("Cache-Control", `public, max-age=${CACHE_TTL}, immutable`);
  } else {
    h.set("Cache-Control", "no-store");
  }
  const resp = new Response(origin.body, {
    status: origin.status,
    statusText: origin.statusText,
    headers: h,
  });

  if (cacheable && origin.ok) waitUntil(cache.put(cacheKey, resp.clone()));
  return withCors(resp);
}

function withCors(resp) {
  const r = new Response(resp.body, resp);
  for (const [k, v] of Object.entries(CORS)) r.headers.set(k, v);
  return r;
}
