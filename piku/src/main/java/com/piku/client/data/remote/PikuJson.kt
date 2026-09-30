package com.piku.client.data.remote

import kotlinx.serialization.json.Json

/**
 * 全 App 唯一的 JSON 解析配置。pixiv 官方接口在部分条目上是混型（数字页数、
 * 空数组分级），配置改动会直接影响线上解析，单测必须用同一份实例兜真实载荷。
 */
val PikuJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}
