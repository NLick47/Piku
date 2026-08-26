package com.piku.client.data.remote.translation

/**
 * 小说正文分块器（纯函数，无状态，可单测）。
 *
 * 设计要点：
 * - 段落原子：按 \n 逐行装箱，绝不切断段落——对话与心理描写不被腰斩；
 * - 场景分隔线（***、---、＊＊＊ 等）优先断块且永不送模型（同人作者用它切场景/视角）；
 * - 单行超长（罕见）才在句末标点处兜底硬切；
 * - roundtrip 不变量：`split(source).joinToString("") { it.text + it.separatorAfter } == source`。
 */
internal object NovelChunker {

    /** 目标块大小（字符）：~3-8 秒生成，首段可见速度与请求次数的平衡点 */
    internal const val TARGET_CHUNK_CHARS = 1000

    /** 单行硬上限：超过才允许句界兜底切分 */
    internal const val HARD_MAX_LINE_CHARS = 1800

    /** 尾部窗口长度：原文尾供理解指代，译文尾供衔接腔调 */
    internal const val TAIL_ORIGINAL_CHARS = 400
    internal const val TAIL_TRANSLATED_CHARS = 300

    /** 一个待翻块：[text] 为送翻正文；[separatorAfter] 是它与下一块之间的原文分隔符（含分隔线行） */
    data class Chunk(val text: String, val separatorAfter: String)

    /** 场景分隔线行：仅由重复符号构成的行（同人常用 *** / --- / ――― / ＊＊＊ / ※※※ 等） */
    private val DIVIDER_LINE =
        Regex("""^[\s　]*(?:\*{3,}|[-‒―﹘－ー]{3,}|＝{2,}|={3,}|─{2,}|＊{3,}|※{3,}|☆{3,}|・{3,}|\.{6,})[\s　]*$""")

    /** 句末标点（含后续收尾引号），用于超长行兜底切分与尾部对齐 */
    private val SENTENCE_END = Regex("""[。！？…‼⁉]+[」』）〕”’]*""")

    fun isDividerLine(line: String): Boolean = DIVIDER_LINE.matches(line)

    fun split(source: String): List<Chunk> {
        if (source.isBlank()) return listOf(Chunk(source, ""))

        data class Atom(
            /** 送翻文字行；分隔内容（空行/分隔线行）只进 [leadingSep]，不进模型 */
            val text: String,
            val leadingSep: String,
        )

        val atoms = mutableListOf<Atom>()
        var pendingSep = ""
        val lines = source.split('\n')
        for ((idx, line) in lines.withIndex()) {
            // 除文档末元素外，每行后面都紧跟一个换行，归入该行的尾随分隔符
            val nl = if (idx < lines.lastIndex) "\n" else ""
            when {
                line.isBlank() || isDividerLine(line) -> pendingSep += line + nl
                else -> {
                    val pieces =
                        if (line.length > HARD_MAX_LINE_CHARS) splitLongLine(line)
                        else listOf(line)
                    pieces.forEachIndexed { i, piece ->
                        atoms += Atom(piece, if (i == 0) pendingSep else "")
                        pendingSep = ""
                    }
                    pendingSep += nl
                }
            }
        }
        // 全文只有空白/分隔线：无可翻内容，整体单块返回（预检透传会原样保留）
        if (atoms.isEmpty()) return listOf(Chunk(source, ""))
        val trailing = pendingSep

        val chunks = mutableListOf<Chunk>()
        var buffer = StringBuilder()
        var bufferChars = 0
        for (atom in atoms) {
            // 断块时机：已达目标大小，或本块开头紧跟在场景分隔线之后（语义边界优先）。
            // 分隔线属于"上一块的结尾"：断块时把分隔符在最后一个分隔线处切开，
            // 前半段归上一块的 separatorAfter，后半段留给新块的开头（roundtrip 不变量不变）。
            val dividerEnd = lastDividerEndIn(atom.leadingSep)
            if (buffer.isNotEmpty() && (bufferChars >= TARGET_CHUNK_CHARS || dividerEnd >= 0)) {
                val cut = maxOf(dividerEnd, 0)
                chunks += Chunk(buffer.toString(), atom.leadingSep.substring(0, cut))
                buffer = StringBuilder()
                bufferChars = 0
                buffer.append(atom.leadingSep.substring(cut))
            } else {
                buffer.append(atom.leadingSep)
            }
            buffer.append(atom.text)
            bufferChars += atom.text.length
        }
        if (buffer.isNotEmpty()) chunks += Chunk(buffer.toString(), trailing)
        return chunks
    }

    /** [sep] 中最后一个分隔线行的结束位置（含其后换行）；无分隔线返回 -1 */
    private fun lastDividerEndIn(sep: String): Int {
        var end = -1
        var from = 0
        while (from <= sep.length) {
            val nl = sep.indexOf('\n', from)
            val lineEnd = if (nl == -1) sep.length else nl
            if (isDividerLine(sep.substring(from, lineEnd))) {
                end = if (nl == -1) sep.length else nl + 1
            }
            if (nl == -1) break
            from = nl + 1
        }
        return end
    }

    /** 句界尾部截取：取结尾至多 [maxChars]，起点对齐到句末标点之后（避免半句开头） */
    internal fun tail(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val raw = text.takeLast(maxChars)
        val end = SENTENCE_END.find(raw)?.range?.last?.plus(1) ?: 0
        // 句界太靠后才截到的话宁可保留半句，也不丢掉过半内容
        return if (end in 1..raw.length / 2) raw.substring(end) else raw
    }

    /** 超长行按句末标点切片；无标点则按硬上限硬切（极端兜底，不丢字） */
    private fun splitLongLine(line: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        for (m in SENTENCE_END.findAll(line)) {
            val end = m.range.last + 1
            if (end - start >= HARD_MAX_LINE_CHARS / 2 || end == line.length) {
                pieces += line.substring(start, end)
                start = end
            }
        }
        if (start < line.length) {
            val rest = line.substring(start)
            if (rest.length <= HARD_MAX_LINE_CHARS) {
                pieces += rest
            } else {
                rest.chunked(HARD_MAX_LINE_CHARS).forEach { pieces += it }
            }
        }
        return pieces.ifEmpty { listOf(line) }
    }
}
