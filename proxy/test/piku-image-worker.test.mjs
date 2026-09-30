// Cloudflare Worker（proxy/piku-image-worker.js）的离线测试。
// 用 Node 内置测试运行器，不依赖任何包：node --test proxy/test/
// Workerd 的 caches.default / fetch / ctx.waitUntil 由下面的桩替身提供。

import test from "node:test";
import assert from "node:assert/strict";
import worker from "../piku-image-worker.js";

const HOST = "https://pic-relay.cyou";
const GITHUB = "https://raw.githubusercontent.com/NLick47/piku-models/catalog/models.enc.json";
const JSDELIVR = "https://cdn.jsdelivr.net/gh/NLick47/piku-models@catalog/models.enc.json";
const CATALOG_PATH = "/catalog/models.enc.json";
const CIPHERTEXT = '{"alg":"AES-256-GCM","iv":"QKoCYS9rWih3N9py","data":"bI/P03Q"}';

/** 装一套桩替身：fetch 按 URL 派发、caches.default 用 Map 模拟、waitUntil 收集待结算任务 */
function setup(routes) {
  const calls = [];
  const store = new Map();
  const pending = [];

  globalThis.caches = {
    default: {
      async match(key) {
        const hit = store.get(key.url);
        return hit ? hit.clone() : undefined;
      },
      async put(key, resp) {
        store.set(key.url, resp.clone());
      },
    },
  };
  globalThis.fetch = async (input, init) => {
    const url = typeof input === "string" ? input : input.url;
    calls.push({ url, headers: init?.headers ?? {} });
    const handler = routes[url];
    if (!handler) throw new Error("unexpected upstream: " + url);
    return handler();
  };

  const ctx = { waitUntil: (p) => pending.push(p) };

  return {
    calls,
    store,
    ctx,
    settle: () => Promise.all(pending.splice(0)),
    async get(path, headers = {}) {
      const res = await worker.fetch(new Request(HOST + path, { headers }), {}, ctx);
      return { res, body: await res.text() };
    },
  };
}

test("catalog route serves the ciphertext and caches it at the edge", async () => {
  const h = setup({ [GITHUB]: () => new Response(CIPHERTEXT, { status: 200 }) });

  const first = await h.get(CATALOG_PATH);
  assert.equal(first.res.status, 200);
  assert.equal(first.body, CIPHERTEXT);
  assert.equal(first.res.headers.get("Cache-Control"), "public, max-age=300");
  assert.equal(first.res.headers.get("Access-Control-Allow-Origin"), "*");
  await h.settle();
  assert.deepEqual([...h.store.keys()], ["https://piku-catalog.internal/catalog/models.enc.json"]);

  const second = await h.get(CATALOG_PATH);
  assert.equal(second.body, CIPHERTEXT);
  assert.equal(h.calls.length, 1, "第二次应命中边缘缓存，不再回源");
});

test("catalog falls back to jsdelivr when github raw is unreachable", async () => {
  const h = setup({
    [GITHUB]: () => {
      throw new Error("connect timeout");
    },
    [JSDELIVR]: () => new Response(CIPHERTEXT, { status: 200 }),
  });

  const { res, body } = await h.get(CATALOG_PATH);
  assert.equal(res.status, 200);
  assert.equal(body, CIPHERTEXT);
  assert.deepEqual(h.calls.map((c) => c.url), [GITHUB, JSDELIVR]);
});

test("catalog falls back on upstream http error too", async () => {
  const h = setup({
    [GITHUB]: () => new Response("rate limited", { status: 429 }),
    [JSDELIVR]: () => new Response(CIPHERTEXT, { status: 200 }),
  });

  const { body } = await h.get(CATALOG_PATH);
  assert.equal(body, CIPHERTEXT);
});

test("empty upstream body is a miss, not a served catalog", async () => {
  const h = setup({
    [GITHUB]: () => new Response("", { status: 200 }),
    [JSDELIVR]: () => new Response(CIPHERTEXT, { status: 200 }),
  });

  const { body } = await h.get(CATALOG_PATH);
  assert.equal(body, CIPHERTEXT);
});

test("catalog upstream failure is 502 and is not pinned in cache", async () => {
  let healthy = false;
  const h = setup({
    [GITHUB]: () => new Response(healthy ? CIPHERTEXT : "boom", { status: healthy ? 200 : 500 }),
    [JSDELIVR]: () => new Response("down", { status: 503 }),
  });

  const bad = await h.get(CATALOG_PATH);
  assert.equal(bad.res.status, 502);
  assert.equal(bad.res.headers.get("Cache-Control"), "no-store");
  await h.settle();
  assert.equal(h.store.size, 0, "失败响应不该写进边缘缓存");

  healthy = true;
  const good = await h.get(CATALOG_PATH);
  assert.equal(good.res.status, 200);
  assert.equal(good.body, CIPHERTEXT);
});

test("catalog route only matches its exact path", async () => {
  const h = setup({
    "https://cdn.poipiku.com/catalog/models.enc.json.bak": () => new Response("img", { status: 200 }),
    "https://cdn.poipiku.com/catalog/other.json": () => new Response("img", { status: 200 }),
  });

  assert.equal((await h.get("/catalog/models.enc.json.bak")).body, "img");
  assert.equal((await h.get("/catalog/other.json")).body, "img");
  assert.ok(h.calls.every((c) => c.url.startsWith("https://cdn.poipiku.com/")));
});

test("image requests still proxy to the poipiku upstream", async () => {
  const h = setup({
    "https://cdn.poipiku.com/assets/img/x.png": () => new Response("img-bytes", { status: 200 }),
  });

  const { res, body } = await h.get("/assets/img/x.png");
  assert.equal(body, "img-bytes");
  assert.equal(h.calls[0].headers.Referer, "https://poipiku.com/");
  assert.equal(res.headers.get("Cache-Control"), "public, max-age=604800, immutable");
});

test("cookied image request bypasses the shared cache", async () => {
  const h = setup({
    "https://cdn.poipiku.com/assets/img/y.png": () => new Response("img-bytes", { status: 200 }),
  });

  await h.get("/assets/img/y.png", { Cookie: "POIPIKU_LK=abc" });
  await h.get("/assets/img/y.png", { Cookie: "POIPIKU_LK=abc" });

  assert.equal(h.calls.length, 2, "带 Cookie 的图不共享缓存");
  assert.equal(h.calls[0].headers.Cookie, "POIPIKU_LK=abc");
});

test("pixiv api route still rewrites to www.pixiv.net and caches briefly", async () => {
  const h = setup({
    "https://www.pixiv.net/users/123": () => new Response("{}", { status: 200 }),
  });

  const first = await h.get("/px/users/123");
  assert.equal(h.calls[0].url, "https://www.pixiv.net/users/123");
  assert.equal(h.calls[0].headers.Referer, "https://www.pixiv.net/");
  assert.equal(first.res.headers.get("Cache-Control"), "public, max-age=300");
  await h.settle();

  await h.get("/px/users/123");
  assert.equal(h.calls.length, 1, "第二次应命中边缘缓存");
});

test("pximg route keeps its own referer and long image cache", async () => {
  const h = setup({
    "https://i.pximg.net/img/2026/x.jpg": () => new Response("jpg-bytes", { status: 200 }),
  });

  const { res, body } = await h.get("/pximg/img/2026/x.jpg");
  assert.equal(body, "jpg-bytes");
  assert.equal(h.calls[0].url, "https://i.pximg.net/img/2026/x.jpg");
  assert.equal(h.calls[0].headers.Referer, "https://www.pixiv.net/");
  assert.equal(res.headers.get("Cache-Control"), "public, max-age=604800, immutable");
});

test("doh route passes through and is never cached", async () => {
  const h = setup({
    "https://cloudflare-dns.com/dns-query?dns=abc": () =>
      new Response("dns-wire", { status: 200, headers: { "Content-Type": "application/dns-message" } }),
  });

  const { res, body } = await h.get("/dns-query?dns=abc");
  assert.equal(body, "dns-wire");
  assert.equal(h.calls[0].url, "https://cloudflare-dns.com/dns-query?dns=abc");
  assert.equal(res.headers.get("Cache-Control"), "no-store");
  await h.settle();
  assert.equal(h.store.size, 0, "DoH 响应不进共享缓存");
});

test("health route and catalog method guard keep working", async () => {
  const h = setup({});

  const health = await worker.fetch(new Request(HOST + "/health"), {}, h.ctx);
  assert.equal(await health.text(), "piku-relay ok");

  const post = await worker.fetch(new Request(HOST + CATALOG_PATH, { method: "POST" }), {}, h.ctx);
  assert.equal(post.status, 405);
  assert.equal(h.calls.length, 0);
});
