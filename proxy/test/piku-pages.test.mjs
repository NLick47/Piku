// Cloudflare Pages Functions（proxy/piku-pages/functions/[[path]].js）的离线测试。
// 与 worker 版是两份实现（脚本约定：改任一处记得对齐），故这里既测行为也测两份常量一致。

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { onRequest } from "../piku-pages/functions/[[path]].js";

const HOST = "https://piku-img.pages.dev";
const CATALOG_PATH = "/catalog/models.enc.json";
const CIPHERTEXT = '{"alg":"AES-256-GCM","iv":"QKoCYS9rWih3N9py","data":"bI/P03Q"}';
const here = path.dirname(fileURLToPath(import.meta.url));
const workerSrc = readFileSync(path.join(here, "..", "piku-image-worker.js"), "utf8");
const pagesSrc = readFileSync(path.join(here, "..", "piku-pages", "functions", "[[path]].js"), "utf8");

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

  const context = { waitUntil: (p) => pending.push(p) };

  return {
    calls,
    store,
    async get(path, headers = {}) {
      const response = await onRequest({ ...context, request: new Request(HOST + path, { headers }) });
      return { res: response, body: await response.text() };
    },
    settle: () => Promise.all(pending.splice(0)),
  };
}

test("pages catalog route serves the ciphertext and caches it", async () => {
  const github = "https://raw.githubusercontent.com/NLick47/piku-models/catalog/models.enc.json";
  const h = setup({ [github]: () => new Response(CIPHERTEXT, { status: 200 }) });

  const first = await h.get(CATALOG_PATH);
  assert.equal(first.res.status, 200);
  assert.equal(first.body, CIPHERTEXT);
  assert.equal(first.res.headers.get("Cache-Control"), "public, max-age=300");
  await h.settle();

  await h.get(CATALOG_PATH);
  assert.equal(h.calls.length, 1, "第二次应命中边缘缓存");
});

test("pages catalog route falls back and does not pin failures", async () => {
  const github = "https://raw.githubusercontent.com/NLick47/piku-models/catalog/models.enc.json";
  const h = setup({ [github]: () => new Response("boom", { status: 500 }) });

  const bad = await h.get(CATALOG_PATH);
  assert.equal(bad.res.status, 502);
  assert.equal(bad.res.headers.get("Cache-Control"), "no-store");
  await h.settle();
  assert.equal(h.store.size, 0);
});

test("pages image passthrough is untouched", async () => {
  const h = setup({
    "https://cdn.poipiku.com/assets/img/z.png": () => new Response("img-bytes", { status: 200 }),
  });

  const { body } = await h.get("/assets/img/z.png");
  assert.equal(body, "img-bytes");
  assert.equal(h.calls[0].headers.Referer, "https://poipiku.com/");
});

test("catalog constants stay aligned between the two implementations", () => {
  const constants = (src) => {
    const block = src.match(/const CATALOG_UPSTREAMS = \[([\s\S]*?)\];/)?.[1] ?? "";
    return {
      path: src.match(/const CATALOG_PATH = "([^"]+)"/)?.[1],
      ttl: src.match(/const CATALOG_TTL = (\d+)/)?.[1],
      cacheKey: src.match(/const CATALOG_CACHE_KEY = "([^"]+)"/)?.[1],
      upstreams: [...block.matchAll(/"([^"]+)"/g)].map((m) => m[1]),
    };
  };

  const worker = constants(workerSrc);
  const pages = constants(pagesSrc);
  assert.ok(worker.path, "worker 版缺少 CATALOG_PATH");
  assert.deepEqual(pages, worker, "两版目录常量不一致（脚本约定：改任一处记得对齐）");
});
