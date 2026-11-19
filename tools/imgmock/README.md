# 图片翻译假服务

给 `ImageTranslateEngineDeviceTest`（androidTest）用的本地假服务：模拟 OpenAI 兼容的
多模态接口，收到带图的请求就回一张图，用于在真机/模拟器上验证「发图 → 解析 → 解码」全链路。

## 用法

```sh
python3 tools/imgmock/mock_server.py 18080
```

模拟器通过 `10.0.2.2:18080` 访问宿主机；真机用 `adb reverse tcp:18080 tcp:18080` 后访问 `127.0.0.1:18080`。

## 响应模式

`GET /mode/<name>` 切换响应形态，覆盖各家网关的差异：

| mode | 响应里的图片位置 |
|---|---|
| `content_array` | `choices[0].message.content[]` 里的 `image_url.url` |
| `message_images` | `choices[0].message.images[].b64_json` |
| `data_array` | `data[0].b64_json` |
| `plain_dataurl` | content 是 data URL 字符串 |
| `plain_b64` | content 是纯 base64 |
| `text_only` | 只回文字（应判为失败） |
| `e500` / `e429` | 错误码（429 带 Retry-After: 7） |

`GET /last` 回显最近一次收到的请求，用于确认 App 发的是图片而不是文本。
