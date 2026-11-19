#!/usr/bin/env python3
"""OpenAI 兼容的图片翻译 mock 服务：收到图片就返回一张带标记的图。"""
import base64, io, json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

# 纯色 PNG（1x1 蓝色），用于生成"翻译结果"
def make_png(w=64, h=64, rgb=(30, 90, 200)):
    import zlib, struct
    raw = b''.join(b'\x00' + bytes(rgb) * w for _ in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw))
            + chunk(b'IEND', b''))

PNG_B64 = base64.b64encode(make_png()).decode()

class H(BaseHTTPRequestHandler):
    mode = 'content_array'  # 可通过 /mode/<x> 切换
    last_request = {}

    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.path == '/img.png':
            data = base64.b64decode(PNG_B64)
            self.send_response(200)
            self.send_header('Content-Type', 'image/png')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if self.path.startswith('/mode/'):
            H.mode = self.path.split('/')[-1]
            self._json(200, {'mode': H.mode})
        elif self.path == '/last':
            self._json(200, H.last_request)
        else:
            self._json(404, {'error': 'not found'})

    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0))
        raw = self.rfile.read(n)
        try:
            req = json.loads(raw)
        except Exception:
            self._json(400, {'error': {'message': 'bad json'}})
            return

        msgs = req.get('messages') or []
        content = (msgs[0] or {}).get('content') if msgs else None
        has_image = any(c.get('type') == 'image_url' for c in content) if isinstance(content, list) else False
        H.last_request = {
            'model': req.get('model'),
            'has_image': has_image,
            'auth': self.headers.get('Authorization'),
            'content_len': n,
        }

        if self.path.endswith('/images/edits'):
            has_img = bool(req.get('images'))
            H.last_request['has_image'] = has_img
            if not has_img:
                self._json(400, {'error': {'message': 'no images provided'}})
                return
            m = H.mode
            if m == 'url':
                body = {'created': 1, 'data': [{'url': 'http://10.0.2.2:%d/img.png' % PORT}]}
            elif m == 'e500':
                self._json(500, {'error': {'message': 'boom'}})
                return
            else:
                body = {'created': 1, 'data': [{'b64_json': PNG_B64}]}
            self._json(200, body)
            return

        if not has_image:
            self._json(400, {'error': {'message': 'no image provided'}})
            return

        m = H.mode
        if m == 'content_array':
            body = {'choices': [{'message': {'content': [
                {'type': 'text', 'text': 'done'},
                {'type': 'image_url', 'image_url': {'url': 'data:image/png;base64,' + PNG_B64}},
            ]}}]}
        elif m == 'message_images':
            body = {'choices': [{'message': {'content': 'done',
                    'images': [{'b64_json': PNG_B64}]}}]}
        elif m == 'data_array':
            body = {'data': [{'b64_json': PNG_B64}]}
        elif m == 'plain_dataurl':
            body = {'choices': [{'message': {'content': 'data:image/png;base64,' + PNG_B64}}]}
        elif m == 'plain_b64':
            body = {'choices': [{'message': {'content': PNG_B64}}]}
        elif m == 'text_only':
            body = {'choices': [{'message': {'content': 'I cannot edit images.'}}]}
        elif m == 'e500':
            self._json(500, {'error': {'message': 'boom'}})
            return
        elif m == 'e429':
            self.send_response(429)
            self.send_header('Retry-After', '7')
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'rate'}).encode())
            return
        else:
            body = {'choices': [{'message': {'content': []}}]}

        self._json(200, body)

    def _json(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18080

if __name__ == '__main__':
    HTTPServer(('127.0.0.1', PORT), H).serve_forever()
