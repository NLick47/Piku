package com.piku.client.domain.model


enum class WorkKind(val prefix: String) {
    ILLUST(""),
    NOVEL("n"),
    ;

    fun encode(id: Long): String = prefix + id
}

fun decodeWorkId(raw: String): Pair<Long, WorkKind> {
    val kind = WorkKind.entries.firstOrNull { it.prefix.isNotEmpty() && raw.startsWith(it.prefix) }
        ?: WorkKind.ILLUST
    val id = raw.removePrefix(kind.prefix).toLongOrNull() ?: 0L
    return id to kind
}
