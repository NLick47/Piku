package com.piku.client.source.ao3.data.remote

/**
 * 标签页路径里的标签名。站点把 `.` `/` `&` `#` 换成 `*d*` `*s*` `*a*` `*h*`，
 * 显示名原样拼路径稳定 404；查询参数用的仍是显示名
 */
internal object Ao3TagPath {

    fun of(name: String): String = name
        .replace(".", "*d*")
        .replace("/", "*s*")
        .replace("&", "*a*")
        .replace("#", "*h*")
}
