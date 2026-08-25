package com.piku.client.data.remote.translation

import android.util.Log
import com.piku.client.domain.translation.TranslationEngine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * 走 OpenAI 兼容 /chat/completions 的 LLM 翻译引擎（默认智谱 GLM-4-Flash）。
 *
 * 几个实测出来的坑决定了这里的设计：
 * 1. 免费小模型偶发"把原文原样返回"或输出解释性废话 —— 所以有
 *    [looksUntranslated] 校验 + 一次重试，仍失败则返回空串让调用方回退原文；
 * 2. 一次请求约 0.8~1s —— 所以短字段合并成一条带序号的请求批量翻译，
 *    避免逐字段串行等待；长正文单独发，防止超长 prompt 与串行错位；
 * 3. 小模型会自作主张把 [[n]] 规范成 [n] —— 批量提示词里必须带
 *    "DOUBLE square brackets" 的完整示例（few-shot 比规则描述有效得多）。
 */
class LlmTranslateEngine(
    private val api: LlmChatApi,
    private val config: () -> LlmConfig,
) : TranslationEngine {

    data class LlmConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        /** 合并后的请求参数（基础 + 目录默认 + 单模型覆盖），由引擎原样拼进请求体 */
        val params: JsonObject = JsonObject(emptyMap()),
        /** 目录下发的提示词（单条/批量 × 语言）；null 时回退内置 */
        val prompts: PromptSet? = null,
    )

    override val engineId: String
        get() = "llm:${config().model}"

    override suspend fun translate(texts: List<String>, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        // 空白项不发请求，直接占位返回，保证下标与入参严格对齐
        val indexed = texts.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return texts.map { "" }

        val result = MutableList(texts.size) { "" }
        // 长文本（小说正文）单独一条请求：混进批量会撑爆 prompt 且易错位
        val (long, short) = indexed.partition { it.value.length > LONG_TEXT_THRESHOLD }

        for ((index, text) in long) {
            result[index] = translateSingle(text, targetLang)
        }
        if (short.isNotEmpty()) {
            val batched = translateBatch(short.map { it.value }, targetLang)
            short.forEachIndexed { i, entry ->
                result[entry.index] = batched.getOrElse(i) { "" }
            }
        }
        return result
    }

    /** 单条翻译：整个回复就是译文 */
    private suspend fun translateSingle(text: String, targetLang: String): String {
        repeat(MAX_ATTEMPTS) { attempt ->
            val output = request(
                system = systemPrompt(targetLang, false),
                user = text,
            ) ?: return@repeat
            val cleaned = output.trim()
            if (!looksUntranslated(text, cleaned)) return cleaned
            Log.d(TAG, "translateSingle attempt=${attempt + 1} looks untranslated, retrying")
        }
        return ""
    }

    /**
     * 批量翻译：用 [[n]] 序号包裹每条，要求模型按同样序号回。
     * 条数对不上就退回逐条翻译（宁可慢，不能串错行）。
     */
    private suspend fun translateBatch(texts: List<String>, targetLang: String): List<String> {
        if (texts.size == 1) return listOf(translateSingle(texts.first(), targetLang))

        val payload = texts.withIndex().joinToString("\n") { (i, text) ->
            "${marker(i + 1)}\n$text"
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            val output = request(
                system = systemPrompt(targetLang, true),
                user = payload,
            ) ?: return@repeat
            val parsed = parseBatch(output, texts.size)
            if (parsed != null && parsed.indices.none { looksUntranslated(texts[it], parsed[it]) }) {
                return parsed
            }
            Log.d(TAG, "translateBatch attempt=${attempt + 1} parse/validate failed")
        }
        // 批量不可靠时逐条重试，保证尽量有译文
        return texts.map { translateSingle(it, targetLang) }
    }

    private suspend fun request(system: String, user: String): String? {
        val cfg = config()
        if (cfg.apiKey.isBlank()) {
            Log.d(TAG, "translate skipped: api key blank")
            return null
        }
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", JsonPrimitive("system")); put("content", JsonPrimitive(system)) })
            add(buildJsonObject { put("role", JsonPrimitive("user")); put("content", JsonPrimitive(user)) })
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(cfg.model))
            put("messages", messages)
            // 强制非流式：流式响应是 SSE 分片而非 JSON 对象，会让 content 解析为 null 而整体失败；
            // 目录 params 不得覆盖这些结构性字段（见 RESERVED_PARAM_KEYS）
            put("stream", JsonPrimitive(false))
            // 参数名全部来自目录，不写死；但结构性保留字段已强制，这里跳过
            cfg.params.forEach { (k, v) -> if (k !in RESERVED_PARAM_KEYS) put(k, v) }
        }
        return runCatching {
            api.chat(
                url = chatUrl(cfg.baseUrl),
                authorization = "Bearer ${cfg.apiKey}",
                body = body,
            ).content
        }.onFailure { error ->
            Log.d(TAG, "translate request failed: ${error::class.simpleName}: ${error.message}")
        }.getOrNull()
    }

    /**
     * 解析系统提示词：优先用目录下发的 prompts（单条/批量 × 语言），
     * 缺省回退到内置 [TranslationPrompts]，实现提示词远程热更新。
     */
    private fun systemPrompt(targetLang: String, isBatch: Boolean): String {
        val set = config().prompts
        if (set != null) {
            val map = if (isBatch) set.batch else set.single
            map[langKey(targetLang)]?.let { return it }
        }
        return if (isBatch) TranslationPrompts.batchSystemPrompt(targetLang)
        else TranslationPrompts.singleSystemPrompt(targetLang)
    }

    private fun langKey(targetLang: String): String = when (targetLang) {
        TARGET_EN -> "en"
        TARGET_JA -> "ja"
        else -> "zh"
    }

    companion object {
        private const val TAG = "PikuDiag"

        /** 校验失败后最多再试一次（免费模型偶发抽风） */
        private const val MAX_ATTEMPTS = 2

        /** 结构性保留字段：由引擎自己拼，目录 params 不可覆盖（尤其 stream 必须非流式） */
        private val RESERVED_PARAM_KEYS = setOf("model", "messages", "stream", "n")

        /** 超过这个长度的文本单独发一条请求 */
        internal const val LONG_TEXT_THRESHOLD = 600

        internal fun marker(n: Int) = "[[$n]]"

        /** baseUrl 结尾有无斜杠都能拼对 */
        internal fun chatUrl(baseUrl: String): String =
            baseUrl.trimEnd('/') + "/chat/completions"

        // ---- 目标语言名（与 TranslationRepository.targetLangName 保持一致） ----
        internal const val TARGET_EN = "English"
        internal const val TARGET_JA = "Japanese"

        private val LATIN_WORD = Regex("[A-Za-z]{4,}")

        /**
         * 目标语言预检：确定"文本已是目标语言"才返回 true，调用方据此透传原文，
         * 一分钱额度都不花。判定靠文字系统而非语义，三个目标各有一条规则：
         * - 英文目标：完全无 CJK 字符（汉字/假名）即视为已英文；
         * - 日语目标：含假名即是日文（跳过）；无假名（很可能是中文）必须送翻——
         *   这正是"日本用户读中文作品"场景的正确行为；
         * - 简中目标：正常日文必含假名，故「无假名且无英文单词」按已中文处理；
         *   纯汉字串（異世界転生等）中日互读无碍，跳过最省额度。
         */
        internal fun isAlreadyInTarget(text: String, targetLang: String): Boolean =
            when (targetLang) {
                TARGET_EN -> !containsCjk(text)
                TARGET_JA -> containsKana(text)
                else -> !containsKana(text) && !LATIN_WORD.containsMatchIn(text)
            }

        internal fun containsKana(text: String): Boolean = kanaCount(text) > 0

        private val PURE_LINK = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

        /** `[https://…]显示文本[/https://…]` 锚标记独占整条（LinkText.convert 的产物） */
        private val MARKER_LINK_ONLY = Regex(
            """^\[https?://[^\]]+](?:(?!\[/https?://).)*\[/https?://[^\]]+]$""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** 锚标记链接块（摘除时整块替换，含显示文本） */
        private val MARKER_LINK_BLOCK = Regex(
            """\[https?://[^\]]+](?:(?!\[/https?://).)*\[/https?://[^\]]+]""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /** 裸 URL（与 LinkText 的识别口径一致） */
        private val BARE_URL = Regex("""https?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+""")

        /** 摘除链接后残留的连续空白（不含换行） */
        private val RUN_OF_SPACES = Regex("""[ \t]{2,}""")

        /** 是否含任何链接（锚标记块或裸 URL） */
        internal fun containsLink(text: String): Boolean =
            MARKER_LINK_BLOCK.containsMatchIn(text) || BARE_URL.containsMatchIn(text)

        /**
         * 摘除行内混排的链接（锚标记块含显示文本、裸 URL），返回摘除后的文字与
         * 按出现顺序的链接列表。摘除后的文字送模型，链接由调用方在译文行尾原样拼回——
         * 链接不进模型，也无需模型保留任何占位符。整行都是链接的情形在拆分阶段
         * 已作为 Keep 提取，走不到这里。
         */
        internal fun extractLinks(text: String): Pair<String, List<String>> {
            val links = mutableListOf<String>()
            var out = MARKER_LINK_BLOCK.replace(text) { m ->
                links += m.value
                ""
            }
            out = BARE_URL.replace(out) { m ->
                links += m.value
                ""
            }
            // 摘除后收拢残留的多余空白：连续空格收拢为一个，各行首尾修剪
            // （不碰换行与全角空格，小说正文按整条摘除时保结构）
            out = RUN_OF_SPACES.replace(out, " ")
            out = out.split('\n').joinToString("\n") { it.trim(' ', '\t') }
            return out.trim() to links
        }

        /**
         * 纯链接判定：整条文本就是一个 URL（裸链允许一层 []/() 包裹与首尾空白），
         * 或一个完整的锚标记链接块（显示文本可含空格）。
         * 链接没有可译内容，塞进批量请求只会拖慢速度，还可能被小模型改坏路径。
         */
        internal fun isPureLink(rawText: String): Boolean {
            val text = rawText.trim()
            if (MARKER_LINK_ONLY.matches(text)) return true
            val unwrapped = text.let { t ->
                if (t.length >= 2 &&
                    ((t.first() == '[' && t.last() == ']') || (t.first() == '(' && t.last() == ')'))
                ) {
                    t.substring(1, t.length - 1).trim()
                } else {
                    t
                }
            }
            return PURE_LINK.matches(unwrapped)
        }

        private fun containsHan(text: String): Boolean = text.any {
            it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF'
        }

        private fun containsCjk(text: String): Boolean = containsKana(text) || containsHan(text)

        // ---- 提示词已抽取到 [TranslationPrompts]，与 [[n]] 批量格式/parseBatch 契约强耦合 ----

        /**
         * 按 [[n]] 切分批量回复。缺条、多条、序号不连续都返回 null（视为失败）。
         */
        internal fun parseBatch(output: String, expected: Int): List<String>? {
            val regex = Regex("""\[\[(\d+)]]""")
            val matches = regex.findAll(output).toList()
            if (matches.size != expected) return null
            val result = MutableList(expected) { "" }
            matches.forEachIndexed { i, match ->
                val index = match.groupValues[1].toIntOrNull() ?: return null
                if (index !in 1..expected) return null
                val start = match.range.last + 1
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else output.length
                result[index - 1] = output.substring(start, end).trim()
            }
            return if (result.any { it.isBlank() }) null else result
        }

        /**
         * 判定"没真翻译"：空、与原文相同、或原文含日文假名而译文仍大量残留假名。
         * 假名（而非汉字）是可靠信号——中文译文里不应出现平/片假名。
         */
        internal fun looksUntranslated(source: String, output: String): Boolean {
            if (output.isBlank()) return true
            if (output.trim() == source.trim()) return true
            val sourceKana = kanaCount(source)
            if (sourceKana == 0) return false
            // 残留假名超过原文的一半，说明基本没译（允许少量保留的拟声词/专名）
            return kanaCount(output) * 2 > sourceKana
        }

        private fun kanaCount(text: String): Int = text.count { ch ->
            ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF'
        }
    }
}
