// Piku 图片中转 · Cloudflare Pages Functions
//
// 部署后访问 https://<项目名>.pages.dev/<原 cdn 路径>
// 例：https://piku-img.pages.dev/assets/img/poipiku_icon_512x512_2.png
//     -> 服务端回源 https://cdn.poipiku.com/assets/img/poipiku_icon_512x512_2.png 并缓存回传
//
// 客户端只需把图片域名从 cdn.poipiku.com 换成你的 pages.dev 域名。
// 运营商只看得到 *.pages.dev（实测国内可达），看不到 CloudFront 的 SNI/IP。

const UPSTREAM = "https://cdn.poipiku.com";
const REFERER = "https://poipiku.com/";
const CACHE_TTL = 604800; // 7 天，图片内容不可变
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,HEAD,OPTIONS",
};

export async function onRequest(context) {
  const { request, waitUntil } = context;
  const url = new URL(request.url);

  if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (url.pathname === "/__health") {
    return new Response("piku-image-relay ok", { status: 200, headers: CORS });
  }

  const upstreamUrl = UPSTREAM + url.pathname + url.search;
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
    "Referer": REFERER,
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
