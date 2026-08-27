package com.piku.client.data.remote.translation

import android.util.Log
import com.piku.client.domain.translation.ChunkContext
import com.piku.client.domain.translation.TranslationEngine
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import kotlin.random.Random

class LlmTranslateEngine(
    private val api: LlmChatApi,
    private val config: LlmConfig,
) : TranslationEngine {

    data class LlmConfig(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        /** 服务场景（[Role.TEXT]/[Role.NOVEL]）：单条路径按它选提示词组，也进缓存键 */
        val role: String = Role.TEXT,
        /** 合并后的请求参数（基础 + 目录默认 + 单模型覆盖），由引擎原样拼进请求体 */
        val params: JsonObject = JsonObject(emptyMap()),
        /** 模型级提示词（models[].prompts，如小模型的精简版）；null 时逐组回退目录默认 */
        val prompts: PromptSet? = null,
        /** 目录级提示词（defaults.prompts，富规则版），模型级未覆盖的组用它 */
        val defaultPrompts: PromptSet? = null,
    )

    /**
     * 缓存引擎标识：role + 模型 + 地址三元组。
     * role 隔离是关键——文本批量与小说正文即便共用同一模型名也绝不互串缓存；
     * baseUrl 隔离避免两家服务商同名模型共享命名空间。
     */
    override val engineId: String
        get() = "llm:${config.role}:${config.model}:${config.baseUrl.trimEnd('/')}"

    override suspend fun translate(
        texts: List<String>,
        targetLang: String,
        context: ChunkContext?,
    ): List<String> {
        if (texts.isEmpty()) return emptyList()
        // 空白项不发请求，直接占位返回，保证下标与入参严格对齐
        val indexed = texts.withIndex().filter { it.value.isNotBlank() }
        if (indexed.isEmpty()) return texts.map { "" }

        val result = MutableList(texts.size) { "" }
        // 长文本（小说正文）单独一条请求：混进批量会撑爆 prompt 且易错位。
        // 带 context 即小说分块模式：所有条目都走单条+novel 路径，
        // 否则末尾不足阈值的块会被误入批量分支，丢上下文和 novel 提示词
        val (long, short) = if (context != null) {
            indexed to emptyList()
        } else {
            indexed.partition { it.value.length > LONG_TEXT_THRESHOLD }
        }

        for ((index, text) in long) {
            result[index] = translateSingle(text, targetLang, context)
        }
        if (short.isNotEmpty()) {
            val batched = translateBatch(short.map { it.value }, targetLang)
            short.forEachIndexed { i, entry ->
                result[entry.index] = batched.getOrElse(i) { "" }
            }
        }
        return result
    }

    /** 单条翻译：整个回复就是译文；[context] 非空时按小说分块模式注入上下文 */
    private suspend fun translateSingle(
        text: String,
        targetLang: String,
        context: ChunkContext? = null,
    ): String {
        // 提示词组按场景路由：只有小说通道（role=NOVEL 或显式带上下文）才用 novel 组；
        // 文本通道的单条路径（批量仅剩 1 条、>600 长简介、批量失败逐条回退）
        // 一律走 single 组——小说条款（上下文标记/同人设定）绝不窜进小文本模型
        val system = if (config.role == Role.NOVEL || context != null) {
            novelSystemPrompt(targetLang, context)
        } else {
            systemPrompt(targetLang, isBatch = false)
        }
        val user = context?.let { contextUserPrefix(it) + text } ?: text
        repeat(MAX_ATTEMPTS) { attempt ->
            val output = try {
                request(system = system, user = user)
            } catch (e: TranslationApiException) {
                Log.d(TAG, "translateSingle attempt=${attempt + 1} api error: ${e.code}")
                if (attempt < MAX_ATTEMPTS - 1 && e.code != 0) delay(retryDelay(attempt))
                return@repeat
            }
            if (output.isBlank()) return ""
            val cleaned = stripEcho(output.trim(), context)
            if (!looksUntranslated(text, cleaned)) return cleaned
            Log.d(TAG, "translateSingle attempt=${attempt + 1} looks untranslated")
            if (attempt < MAX_ATTEMPTS - 1) delay(retryDelay(attempt))
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
            val output = try {
                request(
                    system = systemPrompt(targetLang, true),
                    user = payload,
                )
            } catch (e: TranslationApiException) {
                Log.d(TAG, "translateBatch attempt=${attempt + 1} api error: ${e.code}")
                if (attempt < MAX_ATTEMPTS - 1 && e.code != 0) delay(retryDelay(attempt))
                return@repeat
            }
            if (output.isBlank()) return texts.map { translateSingle(it, targetLang) }
            val parsed = parseBatch(output, texts.size)
            if (parsed != null && parsed.indices.none { looksUntranslated(texts[it], parsed[it]) }) {
                return parsed
            }
            Log.d(TAG, "translateBatch attempt=${attempt + 1} parse/validate failed")
            if (attempt < MAX_ATTEMPTS - 1) delay(retryDelay(attempt))
        }
        // 批量不可靠时逐条重试，保证尽量有译文
        return texts.map { translateSingle(it, targetLang) }
    }

    private suspend fun request(system: String, user: String): String {
        val cfg = config
        if (cfg.apiKey.isBlank()) {
            Log.d(TAG, "translate skipped: api key blank")
            return ""
        }
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", JsonPrimitive("system")); put("content", JsonPrimitive(system)) })
            add(buildJsonObject { put("role", JsonPrimitive("user")); put("content", JsonPrimitive(user)) })
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(cfg.model))
            put("messages", messages)
            put("stream", JsonPrimitive(false))
            cfg.params.forEach { (k, v) -> if (k !in RESERVED_PARAM_KEYS) put(k, v) }
        }
        try {
            val response = api.chat(
                url = chatUrl(cfg.baseUrl),
                authorization = "Bearer ${cfg.apiKey}",
                body = body,
            )
            val content = response.content
            if (content.isBlank()) {
                throw TranslationApiException(0, "empty response")
            }
            return content
        } catch (e: HttpException) {
            throw TranslationApiException(e.code(), e.message())
        } catch (e: TranslationApiException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "translate request failed: ${e::class.simpleName}: ${e.message}")
            throw TranslationApiException(0, e.message ?: "unknown error")
        }
    }

    /**
     * 解析系统提示词，两级远程 + 一级内置：
     * 1. 模型级 [LlmConfig.prompts]（models[].prompts，小模型在此挂精简版）；
     * 2. 目录级 [LlmConfig.defaultPrompts]（defaults.prompts，富规则版）；
     * 3. 内置 [TranslationPrompts] 兜底。
     * 每类（单条/批量/小说 × 语言）独立逐级回退：模型只覆盖自己想改的组，
     * 未覆盖的组自然落到目录默认，不会误跳到别的组。
     */
    private fun systemPrompt(targetLang: String, isBatch: Boolean, isNovel: Boolean = false): String {
        val key = langKey(targetLang)
        fun PromptSet.group(): Map<String, String> = when {
            isNovel -> novel
            isBatch -> batch
            else -> single
        }
        config.prompts?.group()?.get(key)?.let { return it }
        config.defaultPrompts?.group()?.get(key)?.let { return it }
        return when {
            isNovel -> TranslationPrompts.novelSystemPrompt(targetLang)
            isBatch -> TranslationPrompts.batchSystemPrompt(targetLang)
            else -> TranslationPrompts.singleSystemPrompt(targetLang)
        }
    }

    /** 小说分块模式的系统提示词：novel 组 + 可选的标签译名对照表拼尾 */
    private fun novelSystemPrompt(targetLang: String, context: ChunkContext?): String {
        val base = systemPrompt(targetLang, isBatch = false, isNovel = true)
        val glossary = context?.glossaryBlock?.takeIf { it.isNotBlank() } ?: return base
        return "$base\n\n$glossary"
    }

    /**
     * 上下文注入的用户消息前缀。标记串是 app 写死的结构性契约：
     * 回声剥离按精确字符串匹配，目录提示词可调措辞但不可改这些标记。
     */
    private fun contextUserPrefix(context: ChunkContext): String =
        "⟦上文原句⟧\n${context.originalTail}\n⟦上文译文⟧\n${context.translatedTail}\n⟦待翻正文⟧\n"

    private fun langKey(targetLang: String): String = when (targetLang) {
        TARGET_EN -> "en"
        TARGET_JA -> "ja"
        else -> "zh"
    }

    /** 指数退避 + 抖动，防止多客户端同步挤兑同一把 key */
    private fun retryDelay(attempt: Int): Long {
        val exp = 1500L * (1L shl attempt.coerceAtMost(3))
        return exp + Random.nextLong(0, 500)
    }

    companion object {
        private const val TAG = "PikuDiag"

        /** 校验失败后最多再试一次（免费模型偶发抽风） */
        private const val MAX_ATTEMPTS = 3

        /** 回声判定中 ⟦待翻正文⟧ 标记允许出现的最大偏移（相对注入前缀长度的富余量） */
        private const val ECHO_SLACK = 32

        /**
         * 确定性回声剥离：小模型可能不听话地把注入的前缀复读出来。
         * 注入串精确已知，逐级匹配剥掉——污染从"静默损坏"变成"可修复"：
         * 1. 整块复读（含全部标记）→ 按 ⟦待翻正文⟧ 标记截断；
         * 2. 只复读原文尾 / 译文尾（标记被丢弃）→ 按已知尾部串剥前缀。
         * 都不命中则原样返回，绝不误删真实译文。
         */
        internal fun stripEcho(output: String, context: ChunkContext?): String {
            if (context == null) return output
            var out = output
            val bodyMarker = "⟦待翻正文⟧"
            val markerIdx = out.indexOf(bodyMarker)
            if (markerIdx >= 0 && markerIdx <= contextPrefixLength(context) + ECHO_SLACK) {
                out = out.substring(markerIdx + bodyMarker.length).trimStart()
            }
            if (out.startsWith("⟦上文原句⟧")) {
                out = out.removePrefix("⟦上文原句⟧").trimStart()
            }
            if (out.startsWith(context.originalTail)) {
                out = out.removePrefix(context.originalTail).trimStart()
            }
            if (out.startsWith("⟦上文译文⟧")) {
                out = out.removePrefix("⟦上文译文⟧").trimStart()
            }
            if (out.startsWith(context.translatedTail)) {
                out = out.removePrefix(context.translatedTail).trimStart()
            }
            return out.trim()
        }

        private fun contextPrefixLength(context: ChunkContext): Int =
            "⟦上文原句⟧\n".length + context.originalTail.length +
                "\n⟦上文译文⟧\n".length + context.translatedTail.length +
                "\n⟦待翻正文⟧\n".length

        /** 结构性保留字段：由引擎自己拼，目录 params 不可覆盖（尤其 stream 必须非流式） */
        private val RESERVED_PARAM_KEYS = setOf("model", "messages", "stream", "n")

        /** 超过这个长度的文本单独发一条请求 */
        internal const val LONG_TEXT_THRESHOLD = 600

        private val BATCH_MARKER_REGEX = Regex("""\[\[(\d+)]]""")

        internal fun marker(n: Int) = "[[$n]]"

        /** baseUrl 结尾有无斜杠都能拼对 */
        internal fun chatUrl(baseUrl: String): String =
            baseUrl.trimEnd('/') + "/chat/completions"

        // ---- 目标语言名（与 TranslationRepository.targetLangName 保持一致） ----
        internal const val TARGET_EN = "English"
        internal const val TARGET_JA = "Japanese"
        internal const val TARGET_ZH = "Simplified Chinese"

        private val LATIN_WORD = Regex("[A-Za-z]{4,}")

        /**
         * 目标语言预检：确定"文本已是目标语言"才返回 true，调用方据此透传原文，
         * 一分钱额度都不花。判定靠文字系统而非语义，三个目标各有一条规则：
         * - 英文目标：完全无 CJK 字符（汉字/假名）且无其他外语字系即视为已英文；
         * - 日语目标：含假名且汉字未占压倒性多数（见 [hanDominantOverKana]）即按日文
         *   跳过；纯中文或中文为主的混排必须送翻——日本用户读不懂夹在里面的中文；
         * - 简中目标：正常日文必含假名，故「无假名、无英文单词、无其他字系」按已中文处理；
         *   纯汉字串（異世界転生等）中日互读无碍，跳过最省额度。
         *   中英混排但汉字明显占优的口语（"今天天气不错 nice day"）也按已中文透传：
         *   为一个点缀性英文词送翻整段纯属浪费，还可能被小模型改坏；
         *   判定用"汉字数 ≥ 英文单词数×2"，英文为主的句子不会被误透传。
         * 韩文/西里尔等字系既非英文也非中文汉字，任何目标下都不得透传，否则
         * 会被误判为"已是目标语言"而静默跳过（如韩文在简中/英文目标下）。
         */
        internal fun isAlreadyInTarget(text: String, targetLang: String): Boolean =
            when (targetLang) {
                TARGET_EN -> !containsCjk(text) && !containsOtherScript(text)
                TARGET_JA -> containsKana(text) && !hanDominantOverKana(text)
                else -> !containsKana(text) && !containsOtherScript(text) &&
                    (!LATIN_WORD.containsMatchIn(text) || hanDominant(text))
            }

        /** 中文为主的混排判定：汉字存在且数量达到英文单词数的两倍 */
        private fun hanDominant(text: String): Boolean =
            hanCount(text) > 0 && hanCount(text) >= LATIN_WORD.findAll(text).count() * 2

        /**
         * 日语目标下的"中文为主混排"判定：汉字数量超过假名的 3 倍视为中文占优。
         * 正常日文汉字:假名约 1:1~2:1（転生したらスライムだった件 ≈ 13:12），
         * 3 倍阈值留足余量不误伤；而"今天天气很好、今日はいい天気"这类中文为主体、
         * 只掺一句日文的混排会被正确送翻，日本用户不再看到整段未翻中文。
         */
        private fun hanDominantOverKana(text: String): Boolean {
            val kana = kanaCount(text)
            if (kana == 0) return false // 无假名本就不会透传，保持送翻语义
            return hanCount(text) > kana * 3
        }

        private fun hanCount(text: String): Int = text.count { ch ->
            ch in '\u3400'..'\u9FFF' || ch in '\uF900'..'\uFAFF'
        }

        /** 假名计数：平/片假名主区段 + 半角片假名（U+FF66-FF9F，老式日文网页偶见） */
        internal fun kanaCount(text: String): Int = text.count { ch ->
            ch in '\u3040'..'\u30FF' || ch in '\u31F0'..'\u31FF' || ch in '\uFF66'..'\uFF9F'
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

        private fun containsHan(text: String): Boolean = hanCount(text) > 0

        private fun containsCjk(text: String): Boolean = containsKana(text) || containsHan(text)

        /**
         * 拉丁字母与 CJK 之外的主要字系：希腊/西里尔/希伯来/阿拉伯/天城文/泰文/韩文。
         * 这些语言不可能是英文，也不是中日通读的汉字——出现即说明文本是相应外语，
         * 预检必须放行送翻。误放行（多花一次请求）远好于误透传（读者看不懂）。
         */
        private fun containsOtherScript(text: String): Boolean = text.any { ch ->
            ch in '\u0370'..'\u03FF' ||   // Greek
                ch in '\u0400'..'\u052F' ||   // Cyrillic + Supplement
                ch in '\u0590'..'\u05FF' ||   // Hebrew
                ch in '\u0600'..'\u06FF' ||   // Arabic
                ch in '\u0750'..'\u077F' ||   // Arabic Supplement
                ch in '\u0900'..'\u097F' ||   // Devanagari
                ch in '\u0E00'..'\u0E7F' ||   // Thai
                ch in '\u1100'..'\u11FF' ||   // Hangul Jamo
                ch in '\u3130'..'\u318F' ||   // Hangul Compatibility Jamo
                ch in '\uAC00'..'\uD7AF'      // Hangul Syllables
        }

        // ---- 提示词已抽取到 [TranslationPrompts]，与 [[n]] 批量格式/parseBatch 契约强耦合 ----

        /**
         * 按 [[n]] 切分批量回复。缺条、多条、序号不连续都返回 null（视为失败）。
         */
        internal fun parseBatch(output: String, expected: Int): List<String>? {
            val matches = BATCH_MARKER_REGEX.findAll(output).toList()
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
         * 判定"没真翻译"：空、与原文相同、或译文残留过半源假名。
         * 方向性保护：假名不减反增说明必然发生了翻译——典型是译入日语，
         * 正确译文比中文原文的假名多得多，旧逻辑会把它误杀成"没翻译"
         * 导致混排块整段回退原文。
         */
        internal fun looksUntranslated(source: String, output: String): Boolean {
            if (output.isBlank()) return true
            if (output.trim() == source.trim()) return true
            val sourceKana = kanaCount(source)
            if (sourceKana == 0) return false
            val outputKana = kanaCount(output)
            // 假名不减反增：必然发生了翻译（如中文为主混排 → 日语全文）
            if (outputKana >= sourceKana) return false
            // 残留假名超过原文的一半，说明基本没译（允许少量保留的拟声词/专名）
            return outputKana * 2 > sourceKana
        }
    }
}

/** API 层面错误（429 限速 / 5xx 服务端 / 空响应），用于区分校验失败 */
class TranslationApiException(val code: Int, message: String) : Exception(message)
