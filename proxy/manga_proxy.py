import asyncio
import base64
import hashlib
import json
import os
import re
import time
import logging
from pathlib import Path
from aiohttp import web, ClientSession, ClientTimeout

UPSTREAM = "http://127.0.0.1:8083"
UPSTREAM_API_KEY = os.environ["GEMINI_API_KEY"]
LISTEN_HOST = "0.0.0.0"
LISTEN_PORT = 43981
RATE_LIMIT = 20
RATE_WINDOW = 60.0

TRANSLATED_DIR = Path("/opt/gemini/translated")
STORAGE_QUOTA_BYTES = 10 * 1024 * 1024 * 1024  # 10GB

bucket = [RATE_LIMIT, 0.0]
bucket_lock = asyncio.Lock()
log = logging.getLogger("proxy")


async def rate_ok():
    now = time.monotonic()
    async with bucket_lock:
        if bucket[1] == 0.0:
            bucket[1] = now
        elapsed = now - bucket[1]
        bucket[0] = min(RATE_LIMIT, bucket[0] + elapsed * (RATE_LIMIT / RATE_WINDOW))
        bucket[1] = now
        if bucket[0] >= 1:
            bucket[0] -= 1
            return True
        return False


def enforce_storage_quota():
    if not TRANSLATED_DIR.exists():
        return
    files = sorted(
        list(TRANSLATED_DIR.glob("*.png")) + list(TRANSLATED_DIR.glob("*.jpg")),
        key=lambda f: f.stat().st_atime,
    )
    total = sum(f.stat().st_size for f in files)
    while total > STORAGE_QUOTA_BYTES and files:
        oldest = files.pop(0)
        total -= oldest.stat().st_size
        oldest.unlink(missing_ok=True)
        log.info("QUOTA_EVICT %s", oldest.name)


async def translate_image_handler(request: web.Request) -> web.StreamResponse:
    reader = await request.multipart()
    image_data = None
    prompt = None
    target_lang = None

    while True:
        part = await reader.next()
        if part is None:
            break
        if part.name == "image":
            image_data = await part.read()
        elif part.name == "prompt":
            prompt = (await part.read()).decode("utf-8")
        elif part.name == "target_lang":
            target_lang = (await part.read()).decode("utf-8")

    if image_data is None or prompt is None or target_lang is None:
        return web.json_response(
            {"error": "missing required fields: image, prompt, target_lang"},
            status=400,
        )

    # Check cache first: same prompt + lang + image = same hash
    hash_input = prompt.encode() + target_lang.encode() + image_data
    file_hash = hashlib.sha256(hash_input).hexdigest()[:32]
    for ext in ("png", "jpg"):
        cached = TRANSLATED_DIR / f"{file_hash}.{ext}"
        if cached.exists():
            log.info("CACHE_HIT %s", cached.name)
            return web.Response(
                body=cached.read_bytes(),
                content_type=f"image/{ext}",
                headers={"X-Image-Hash": file_hash, "X-Cache": "HIT"},
            )

    image_b64 = base64.b64encode(image_data).decode("ascii")

    # Build Gemini API request
    api_body = {
        "model": "gemini-image",
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/png;base64,{image_b64}"
                        },
                    },
                ],
            }
        ],
        "stream": False,
    }

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {UPSTREAM_API_KEY}",
    }

    try:
        async with ClientSession() as session:
            async with session.request(
                "POST",
                f"{UPSTREAM}/v1/chat/completions",
                headers=headers,
                json=api_body,
                timeout=ClientTimeout(total=180),
            ) as resp:
                if resp.status != 200:
                    error_text = await resp.text()
                    log.error("UPSTREAM_ERR %s %s", resp.status, error_text[:200])
                    return web.json_response(
                        {"error": f"upstream error: {resp.status}"},
                        status=502,
                    )
                result = await resp.json()
    except Exception as e:
        log.error("REQUEST_ERR %s", e)
        return web.json_response({"error": "upstream error"}, status=502)

    # Extract base64 image from response
    content = result.get("choices", [{}])[0].get("message", {}).get("content", "")
    match = re.search(r"data:image/(png|jpeg);base64,([A-Za-z0-9+/=]+)", content)
    if not match:
        log.error("NO_IMAGE_IN_RESPONSE content=%s", content[:200])
        return web.json_response(
            {"error": "no image in response"},
            status=500,
        )

    img_format = match.group(1)
    img_b64 = match.group(2)
    img_bytes = base64.b64decode(img_b64)

    # Store translated image (file_hash already computed above)
    file_ext = "png" if img_format == "png" else "jpg"
    file_path = TRANSLATED_DIR / f"{file_hash}.{file_ext}"

    # Store file
    TRANSLATED_DIR.mkdir(parents=True, exist_ok=True)
    file_path.write_bytes(img_bytes)
    enforce_storage_quota()

    log.info("STORED %s %d bytes", file_path.name, len(img_bytes))

    # Return binary image
    content_type = f"image/{file_ext}"
    return web.Response(
        body=img_bytes,
        content_type=content_type,
        headers={"X-Image-Hash": file_hash},
    )


async def handler(req: web.Request) -> web.StreamResponse:
    ip = req.remote
    path = req.path

    if path == "/healthz":
        return web.json_response({"status": "ok"})

    # Image translation endpoint (no auth, rate limited)
    if path == "/v1/translate-image" and req.method == "POST":
        if not await rate_ok():
            log.warning("RATE 429 %s %s", ip, path)
            return web.json_response(
                {"error": "rate limit exceeded (20/min global)"},
                status=429,
                headers={"Retry-After": "30"},
            )
        return await translate_image_handler(req)

    if not path.startswith("/v1"):
        return web.json_response({"error": "not found"}, status=404)

    if not await rate_ok():
        log.warning("RATE 429 %s %s", ip, path)
        return web.json_response(
            {"error": "rate limit exceeded (20/min global)"},
            status=429,
            headers={"Retry-After": "30"},
        )

    target = UPSTREAM + path
    headers = {
        k: v for k, v in req.headers.items()
        if k.lower() not in ("host", "content-length", "connection")
    }
    body = await req.read()
    log.info("%s %s %s", ip, req.method, path)

    try:
        async with ClientSession() as session:
            async with session.request(
                req.method, target, headers=headers,
                params=req.query, data=body,
                timeout=ClientTimeout(total=120),
            ) as resp:
                out = web.StreamResponse(
                    status=resp.status,
                    headers={
                        k: v for k, v in resp.headers.items()
                        if k.lower() not in (
                            "transfer-encoding", "content-length", "connection",
                        )
                    },
                )
                await out.prepare(req)
                async for chunk in resp.content.iter_any():
                    await out.write(chunk)
                await out.write_eof()
                return out
    except Exception as e:
        log.error("UPSTREAM_ERR %s %s", ip, e)
        return web.json_response({"error": "upstream error"}, status=502)


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )
    TRANSLATED_DIR.mkdir(parents=True, exist_ok=True)
    app = web.Application()
    app.router.add_route("*", "/{tail:.*}", handler)
    web.run_app(app, host=LISTEN_HOST, port=LISTEN_PORT, access_log=None)


if __name__ == "__main__":
    main()
