// Piku 图片中转 · Cloudflare Worker
//
// 作用：App 把图片域名从 cdn.poipiku.com 改成你的 Worker 域名，
//       用户连 Worker（SNI=你的域名，不被运营商封）→ Worker 回源 CloudFront 取图并缓存。
//       运营商只看得到你的域名，看不到 CloudFront 的 SNI/IP。
//
// 部署要点：
//   1. wrangler deploy
//   2. 必须绑一个自定义域名（*.workers.dev 在国内经常被墙）
//   3. 回源 Referer 已带；需要登录/R-18 的图会转发客户端 Cookie（自建时无隐私顾虑）
//
// 客户端改写：https://cdn.poipiku.com/<path>  ->  https://<你的域名>/<path>

const UPSTREAM = "https://cdn.poipiku.com";
const REFERER = "https://poipiku.com/";
const CACHE_TTL = 604800; // 7 天，图片内容不可变
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,HEAD,OPTIONS",
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response("piku-image-relay ok", { status: 200, headers: CORS });
    }

    const upstreamUrl = UPSTREAM + url.pathname + url.search;
    const cookie = request.headers.get("Cookie");
    // 带 Cookie 的请求按用户不同，不进共享缓存
    const cacheable = request.method === "GET" && !cookie;
    const cacheKey = new Request(upstreamUrl, { method: "GET" });

    if (cacheable) {
      const hit = await caches.default.match(cacheKey);
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

    if (cacheable && origin.ok) ctx.waitUntil(caches.default.put(cacheKey, resp.clone()));
    return withCors(resp);
  },
};

function withCors(resp) {
  const r = new Response(resp.body, resp);
  for (const [k, v] of Object.entries(CORS)) r.headers.set(k, v);
  return r;
}
