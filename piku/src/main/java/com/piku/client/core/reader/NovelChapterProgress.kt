package com.piku.client.core.reader

object NovelChapterProgress {

    /** 章序号 + 章内百分比 */
    data class Position(val chapterIndex: Int, val localPercent: Int)

    /** 整部百分比 → 定位到哪一章、章内多少 */
    fun locate(lengths: List<Int>, overallPercent: Int): Position {
        if (lengths.isEmpty()) return Position(0, 0)
        val total = lengths.sumOf { it.toLong() }.coerceAtLeast(1L)
        val percent = overallPercent.coerceIn(0, 100)
        // 100% 恒落在全书末尾（末章 100%），否则末章会被"恰好等于末尾"的边界排到前一章
        if (percent >= 100) return Position(lengths.lastIndex, 100)
        val target = total * percent / 100L
        var before = 0L
        lengths.forEachIndexed { index, len ->
            if (target < before + len) {
                val local = if (len <= 0) 0 else ((target - before) * 100 / len).toInt()
                return Position(index, local.coerceIn(0, 100))
            }
            before += len
        }
        return Position(lengths.lastIndex, 100)
    }

    /** 章序号 + 章内百分比 → 整部百分比 */
    fun overall(lengths: List<Int>, chapterIndex: Int, localPercent: Int): Int {
        if (lengths.isEmpty()) return 0
        val index = chapterIndex.coerceIn(0, lengths.lastIndex)
        val total = lengths.sumOf { it.toLong() }.coerceAtLeast(1L)
        val before = lengths.take(index).sumOf { it.toLong() }
        val local = localPercent.coerceIn(0, 100)
        val chars = before + lengths[index].toLong() * local / 100L
        return (chars * 100 / total).toInt().coerceIn(0, 100)
    }
}
