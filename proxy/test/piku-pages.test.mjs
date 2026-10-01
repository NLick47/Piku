// Cloudflare Pages Functions（proxy/piku-pages/functions/[[path]].js）的离线测试。
// 行为类用例已删：mock 出来的 fetch/caches 不代表线上行为，验证靠对线上端点 curl。

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { onRequest } from "../piku-pages/functions/[[path]].js";

const here = path.dirname(fileURLToPath(import.meta.url));
const workerSrc = readFileSync(path.join(here, "..", "piku-image-worker.js"), "utf8");
const pagesSrc = readFileSync(path.join(here, "..", "piku-pages", "functions", "[[path]].js"), "utf8");

test("pages.dev hostname gets 403", async () => {
  const res = await onRequest({
    request: new Request("https://piku-img.pages.dev/health"),
    waitUntil: () => {},
  });
  assert.equal(res.status, 403);
  assert.equal(await res.text(), "Access Denied");
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
