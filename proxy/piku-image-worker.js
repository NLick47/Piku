// Piku 中转 · Cloudflare Worker（生产入口）
// Pages 版 piku-pages/functions/[[path]].js 保留作回退，改任一处记得对齐另一份。
//
// 部署：cd proxy && npx wrangler deploy
//   wrangler.toml 里 workers_dev = false，只绑 pic-relay.cyou 自定义域；
//   *.workers.dev 在国内常被墙，本来也只走自定义域。
//
// 回源：/dns-query -> cloudflare-dns.com，/px/ -> www.pixiv.net，
//       /pxapi/ -> app-api.pixiv.net，/pximg/ -> i.pximg.net，
//       /catalog/models.enc.json -> 模型目录，其余 -> cdn.poipiku.com

const IMAGE_UPSTREAM = "https://cdn.poipiku.com";
const IMAGE_REFERER = "https://poipiku.com/";
const PIXIV_UPSTREAM = "https://www.pixiv.net";
const PIXIV_PREFIX = "/px";
/** pixiv 图床：直连只有几十 KB/s，大图必然超时，走边缘回源快 6–8 倍 */
const PIXIV_IMG_UPSTREAM = "https://i.pximg.net";
const PIXIV_IMG_PREFIX = "/pximg";
const PIXIV_IMG_REFERER = "https://www.pixiv.net/";
const DOH_UPSTREAM = "https://cloudflare-dns.com/dns-query";
/** 应用接口是另一个 zone，bot 策略未必与主站一致，单独开一条便于对比 */
const PIXIV_APP_UPSTREAM = "https://app-api.pixiv.net";
const PIXIV_APP_PREFIX = "/pxapi";

/** 模型目录密文（piku-models 的 catalog 分支）：国内直连 GitHub raw 被墙，这里由 CF 出口回源 */
const CATALOG_PATH = "/catalog/models.enc.json";
const CATALOG_UPSTREAMS = [
  "https://raw.githubusercontent.com/NLick47/piku-models/catalog/models.enc.json",
  "https://cdn.jsdelivr.net/gh/NLick47/piku-models@catalog/models.enc.json",
];
const CATALOG_TTL = 300; // 目录很少变，短缓存让新版本及时上边缘
// 缓存键用内部主机名，跟图片缓存（键是回源 URL）互不相干
const CATALOG_CACHE_KEY = "https://piku-catalog.internal/catalog/models.enc.json";

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
// 出口 IP 被 pixiv 整站封锁时，换 UA、补齐浏览器指纹都没用，别把下面这组头当解药。
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

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (url.pathname === "/health" || url.pathname === "/__health") {
      return new Response("piku-relay ok", { status: 200, headers: CORS });
    }
    if (url.pathname === "/dns-query") return doh(request);
    if (url.pathname === PIXIV_APP_PREFIX || url.pathname.startsWith(PIXIV_APP_PREFIX + "/")) {
      return pixiv(request, url, ctx, PIXIV_APP_UPSTREAM, PIXIV_APP_PREFIX);
    }
    if (url.pathname === PIXIV_PREFIX || url.pathname.startsWith(PIXIV_PREFIX + "/")) {
      return pixiv(request, url, ctx, PIXIV_UPSTREAM, PIXIV_PREFIX);
    }
    if (url.pathname === PIXIV_IMG_PREFIX || url.pathname.startsWith(PIXIV_IMG_PREFIX + "/")) {
      return pixivImage(request, url, ctx);
    }
    // 精确匹配：宽松匹配会跟图片回源路径打架（其余路径都是透传上游）
    if (url.pathname === CATALOG_PATH) return catalog(request, (p) => ctx.waitUntil(p));
    return image(request, url, ctx);
  },
};

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

async function pixiv(request, url, ctx, upstream, prefix) {
  const path = url.pathname.slice(prefix.length) || "/";
  const upstreamUrl = upstream + path + url.search;
  const cookie = request.headers.get("Cookie");
  const auth = request.headers.get("Authorization");
  // 带 Cookie / Authorization 的请求按用户不同，不进共享缓存
  const cacheable = request.method === "GET" && !cookie && !auth;
  const cacheKey = new Request(upstreamUrl, { method: "GET" });

  if (cacheable) {
    const hit = await caches.default.match(cacheKey);
    if (hit) return withCors(hit);
  }

  const headers = { ...PIXIV_HEADERS };
  // 客户端自带的应用侧头要能覆盖默认值——App 接口靠 UA / App-OS 判客户端
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
    ctx.waitUntil(caches.default.put(cacheKey, resp.clone()));
  }
  return withCors(resp);
}

/**
 * pixiv 图床回源：Referer 必须是站点页（图床防盗链）；**不转发客户端 Cookie**
 * （客户端带的是 poipiku 会话）；图片不可变，成功响应打 7 天缓存。
 */
async function pixivImage(request, url, ctx) {
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

  if (cacheable && origin.ok) ctx.waitUntil(cache.put(cacheKey, resp.clone()));
  return withCors(resp);
}

async function image(request, url, ctx) {
  const upstreamUrl = IMAGE_UPSTREAM + url.pathname + url.search;
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

  if (cacheable && origin.ok) ctx.waitUntil(caches.default.put(cacheKey, resp.clone()));
  return withCors(resp);
}

/**
 * 模型目录：从 Cloudflare 出口回源（GitHub raw 只拦国内直连，CF 出口不受影响，
 * jsDelivr 作备选），成功即缓存到边缘；失败不缓存，交给下次请求重试。
 */
async function catalog(request, waitUntil) {
  if (request.method !== "GET") {
    return new Response("method not allowed", { status: 405, headers: CORS });
  }

  const cacheKey = new Request(CATALOG_CACHE_KEY, { method: "GET" });
  const hit = await caches.default.match(cacheKey);
  if (hit) return withCors(hit);

  let lastError = "no upstream tried";
  for (const upstream of CATALOG_UPSTREAMS) {
    try {
      const origin = await fetch(upstream, {
        headers: { "User-Agent": "Mozilla/5.0", "Accept": "application/json,text/plain,*/*" },
      });
      if (!origin.ok) {
        lastError = `${upstream} -> HTTP ${origin.status}`;
        continue;
      }
      const body = await origin.arrayBuffer();
      if (body.byteLength === 0) {
        lastError = `${upstream} -> empty body`;
        continue;
      }
      const resp = new Response(body, {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": `public, max-age=${CATALOG_TTL}`,
        },
      });
      waitUntil(caches.default.put(cacheKey, resp.clone()));
      return withCors(resp);
    } catch (e) {
      lastError = `${upstream} -> ${e.message}`;
    }
  }

  // 一次上游抖动不该被边缘钉住：错误响应不缓存，下次请求重新回源
  return new Response("catalog upstream error: " + lastError, {
    status: 502,
    headers: { ...CORS, "Cache-Control": "no-store" },
  });
}

function withCors(resp) {
  const r = new Response(resp.body, resp);
  for (const [k, v] of Object.entries(CORS)) r.headers.set(k, v);
  return r;
}
