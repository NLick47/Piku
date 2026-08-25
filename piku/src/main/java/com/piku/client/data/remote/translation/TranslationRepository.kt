package com.piku.client.data.remote.translation

import com.piku.client.BuildConfig
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.TranslationDao
import com.piku.client.data.local.TranslationEntity
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.WorkDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 一次翻译编排的结局。
 *
 * - [fields] 非空：有可展示的新译文；
 * - [fields] 为 null 且 [failed] 为 false：正常无产出——整篇命中目标语言预检
 *   透传（如简中用户看纯中文作品）或作品本身没有文本字段，不是错误；
 * - [failed] 为 true：网络/引擎层面的真实失败，手动入口据此提示重试。
 */
data class TranslationOutcome(
    val fields: TranslatedFields?,
    val failed: Boolean,
)

/**
 * 译文编排：缓存优先 → 批量请求 → 回写缓存。
 *
 * - 缓存键为 (原文 SHA-256, 目标语言, 引擎 id)，换模型/换语言互不污染；
 * - 同一次请求内先做去重（标签常有重复），命中缓存的不再上网；
 * - [mutex] 串行化整个翻译动作：详情页只有一个作品在翻，不需要并发，
 *   串行反而能避免免费额度被瞬时打满。
 */
@Singleton
class TranslationRepository @Inject constructor(
    private val dao: TranslationDao,
    private val settingsRepository: SettingsRepository,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val engineFactory: TranslationEngineFactory,
) {

    private val mutex = Mutex()

    /** 只看 key 是否可用（手动翻译入口的显示条件，不依赖自动开关）。
     *  文本模型或小说正文模型（含默认小说模型）任一有可用 key 即视为可翻译。 */
    fun hasKey(): Boolean {
        if (effectiveTextEntry()?.let { apiKeyFor(it) }?.isNotBlank() == true) return true
        return effectiveNovelEntry()?.let { apiKeyFor(it) }?.isNotBlank() == true
    }

    /**
     * key 可用性流：远程目录（内置共享 key）变化时发射。
     * 冷启动时目录晚于首个详情页到达，UI 靠它同步手动入口并对被跳过的作品补翻。
     */
    val hasKeyFlow: Flow<Boolean> = modelCatalogRepository.models.map { hasKey() }

    /**
     * 翻译作品的可见文本字段。任一字段失败则该字段为 null（UI 回退原文），
     * 不会因为局部失败而整体放弃。
     * 只校验 key：自动/手动入口的开关语义由调用方决定。
     *
     * [includeLongNovel] = false 时（自动/顶栏路径）只翻短正文，
     * 长篇正文留给阅读器内的显式触发（见 [AUTO_NOVEL_MAX_CHARS]）。
     */
    suspend fun translate(
        detail: WorkDetail,
        language: AppLanguage,
        includeLongNovel: Boolean = false,
        /** 一次性重翻覆盖：传入则短字段与小说正文都强制用此模型（不写入默认设置） */
        forcedEntry: ModelEntry? = null,
    ): TranslationOutcome {
        if (!hasKey()) return TranslationOutcome(fields = null, failed = false)
        val targetLang = targetLangName(language)
        // 短文本字段（标题/简介/作者简介/标签）走文本模型
        val novelSource = detail.novelText.takeIf {
            includeLongNovel || it.length <= AUTO_NOVEL_MAX_CHARS
        }
        val shortSources = buildList {
            add(detail.title)
            add(detail.description)
            add(detail.authorProfile)
            addAll(detail.tags)
        }
        val shortTranslated = translateAll(shortSources, targetLang, forcedEntry)
        // 小说正文单独走小说专用模型（默认走赞助小说模型）；正文不按行拆分，
        // 散文跨行句必须保持上下文，行内罕见链接走摘除+行尾拼回兜底。
        // 一次性重翻时，短字段已用 forcedEntry，小说正文同样强制用它以保证一致。
        val novelTranslated = if (novelSource != null) {
            translateAll(
                listOf(novelSource),
                targetLang,
                forcedEntry ?: effectiveNovelEntry(),
                splitLines = false,
            )?.firstOrNull()
        } else {
            null
        }
        // 两段都缺席（网络/限速导致整批失败）才判真失败；
        // 仅一段失败则保留另一段成果，不阻塞展示。
        if (shortTranslated == null && novelTranslated == null) {
            return TranslationOutcome(fields = null, failed = true)
        }
        val fields = buildFields(
            titleSrc = detail.title,
            descriptionSrc = detail.description,
            profileSrc = detail.authorProfile,
            tagsSrc = detail.tags,
            shortOut = shortTranslated ?: List(3 + detail.tags.size) { "" },
            novelSrc = novelSource ?: "",
            novelOut = novelTranslated,
        ) ?: return TranslationOutcome(fields = null, failed = false)
        return TranslationOutcome(fields = fields, failed = false)
    }

    /**
     * 一条原文的行分解结果。
     * - [Keep]：链接行/空行——原样回填，永不送模型；
     * - [Translate]：待翻文字行——[stripped] 为摘除链接后的送翻文字，
     *   [links] 是按出现顺序的原链接片段，译文行尾原样拼回。
     */
    internal sealed interface LinePart {
        data class Keep(val text: String) : LinePart
        data class Translate(val stripped: String, val links: List<String>, val original: String) : LinePart
    }

    /**
     * 批量翻译任意文本列表，返回与入参等长的结果（失败项为空串）。
     * 缓存命中的不产生网络请求。
     *
     * 链接处理：含链接的文本先按行拆分，链接行原样保留不进模型；行内混排的
     * 残余链接在引擎边界摘除、译文回来后拼回行尾——
     * URL 既不拖慢批量请求，也不会被小模型改坏。
     *
     * 整批失败（网络错/限速/模型抽风导致全部空返回）时做一次故障转移：
     * 随机换一个带内置 key 的其他免费模型，加全抖动短延迟后重试——
     * 时间与模型两个维度同时打散，多用户不会同步挤兑同一把 key。
     */
    suspend fun translateAll(
        texts: List<String>,
        targetLang: String,
        entryOverride: ModelEntry? = null,
        splitLines: Boolean = true,
    ): List<String>? =
        mutex.withLock {
            val entry = entryOverride ?: effectiveTextEntry()
            var activeEngine = engineFactory.create(
                apiKeyFor(entry), entry, modelCatalogRepository.catalogDefaults.value,
            ) ?: return null
            val primaryEngineId = activeEngine.engineId
            var engineId = activeEngine.engineId
            val result = MutableList(texts.size) { "" }

            // —— 行分解与单元去重：相同行只翻一次（标签、重复描述、常见寒暄行） ——
            data class Unit(val stripped: String, val links: List<String>, val original: String, var value: String?)
            val partsByIndex = Array(texts.size) { emptyList<LinePart>() }
            val unitIdsByIndex = Array(texts.size) { IntArray(0) }
            val unitIndex = LinkedHashMap<String, Int>()
            val units = mutableListOf<Unit>()
            texts.forEachIndexed { index, text ->
                if (text.isBlank()) return@forEachIndexed
                val parts = decomposeLines(text, splitLines)
                partsByIndex[index] = parts
                val ids = mutableListOf<Int>()
                parts.forEach { part ->
                    if (part !is LinePart.Translate) return@forEach
                    // 去重键必须是原文：不同链接的行摘除后可能得到相同文字
                    // （"主页 https://a" 与 "主页 https://b" 都剩 "主页"），
                    // 按 stripped 去重会让第二行拼回第一行的链接。
                    // 缓存键仍用 stripped（见下），跨作品同文字行的网络开销照样省。
                    val existing = unitIndex[part.original]
                    if (existing != null) {
                        ids += existing
                    } else {
                        val id = units.size
                        unitIndex[part.original] = id
                        units += Unit(part.stripped, part.links, part.original, null)
                        ids += id
                    }
                }
                unitIdsByIndex[index] = ids.toIntArray()
            }

            // —— 预检透传 + 缓存命中，得到真正要上网的 pending 单元 ——
            val pending = mutableListOf<Int>()
            units.forEach { unit ->
                if (LlmTranslateEngine.isAlreadyInTarget(unit.original, targetLang)) {
                    // 已是目标语言：本地透传原文，不查缓存不发请求（共享免费 key 的关键省钱点）
                    unit.value = unit.original
                }
            }
            withContext(Dispatchers.IO) {
                for ((id, unit) in units.withIndex()) {
                    if (unit.value != null) continue
                    val cached = dao.get(hash(unit.stripped), targetLang, engineId)
                    if (cached != null) {
                        unit.value = cached
                    } else {
                        pending += id
                    }
                }
            }

            if (pending.isNotEmpty()) {
                // 送翻的都是摘除链接后的纯文字，URL 不出现在 prompt 里
                val strippedPending = pending.map { units[it].stripped }
                var fresh = activeEngine.translate(strippedPending, targetLang)
                var failedOver = false
                if (fresh.all { it.isBlank() }) {
                    val alternative = randomAlternativeEntry(entry)
                    val fallbackEngine = alternative?.let {
                        engineFactory.create(it.apiKey.orEmpty(), it, modelCatalogRepository.catalogDefaults.value)
                    }
                    if (fallbackEngine != null) {
                        delay(Random.nextLong(FAILOVER_JITTER_MIN_MS, FAILOVER_JITTER_MAX_MS))
                        activeEngine = fallbackEngine
                        engineId = fallbackEngine.engineId
                        fresh = fallbackEngine.translate(strippedPending, targetLang)
                        failedOver = true
                    }
                }
                val rows = mutableListOf<TranslationEntity>()
                val now = System.currentTimeMillis()
                // 故障转移成功的批次同时写主/备两个引擎 id：否则下次仍按主模型读缓存
                // 必定 miss，故障期间每次都白付一次失败请求 + 抖动延迟
                val cacheEngineIds =
                    if (failedOver && fresh.any { it.isNotBlank() }) {
                        listOf(primaryEngineId, engineId).distinct()
                    } else {
                        listOf(engineId)
                    }
                pending.forEachIndexed { i, id ->
                    val unit = units[id]
                    val translated = fresh.getOrElse(i) { "" }
                    if (translated.isNotBlank()) {
                        // 链接按出现顺序原样拼回行尾：缓存里存的是含完整可点击链接的译文
                        val value = if (unit.links.isEmpty()) {
                            translated
                        } else {
                            translated.trimEnd() + unit.links.joinToString("") { " $it" }
                        }
                        unit.value = value
                        cacheEngineIds.forEach { engine ->
                            rows += TranslationEntity(
                                srcHash = hash(unit.stripped),
                                targetLang = targetLang,
                                engineId = engine,
                                translated = value,
                                updatedAt = now,
                            )
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    withContext(Dispatchers.IO) { dao.upsertAll(rows) }
                }
            }

            // —— 按行位拼装：失败行回退原文（整体失败时与原文相同，buildFields 会丢弃） ——
            texts.forEachIndexed { index, text ->
                if (text.isBlank()) return@forEachIndexed
                var cursor = 0
                result[index] = partsByIndex[index].joinToString("\n") { part ->
                    when (part) {
                        is LinePart.Keep -> part.text
                        is LinePart.Translate -> {
                            val value = units[unitIdsByIndex[index][cursor++]].value
                            // 翻译失败（null）回退原文行，避免译文里凭空丢行
                            value ?: part.original
                        }
                    }
                }
            }
            result
        }

    /**
     * 故障转移候选：其他带内置共享 key 的可用免费模型，随机挑一个。
     * 注意必须用候选自带的内置 key 去打它对应的端点，key/地址/模型名保持同源。
     */
    private fun randomAlternativeEntry(current: ModelEntry?): ModelEntry? =
        modelCatalogRepository.models.value
            .filter { it.free && it.available && !it.apiKey.isNullOrBlank() && it.id != current?.id }
            .randomOrNull()

    /**
     * 文本翻译当前生效的模型条目：地址、模型名、key 三者必须同源，所以降级是整条切换。
     * key 只来自加密远程目录（debug 构建回退注入的调试 key）：
     * - 选中项带内置共享 key 就用它；
     * - 否则（如历史遗留的自选模型没有内置 key）降级到默认文本模型，再降级到任一带 key 的可用模型，
     *   避免整段翻译静默不可用。
     */
    internal fun effectiveTextEntry(): ModelEntry? {
        val selected = selectedEntry()
        if (selected != null && !selected.apiKey.isNullOrBlank()) return selected
        return defaultTextEntry()
            ?: modelCatalogRepository.models.value.firstOrNull {
                it.available && !it.apiKey.isNullOrBlank()
            }
    }

    /** 文本翻译默认模型：目录里标记 [ModelEntry.defaultText] 的优先，否则首个文本类可用模型 */
    internal fun defaultTextEntry(): ModelEntry? {
        val models = modelCatalogRepository.models.value
        return models.firstOrNull { it.defaultText && it.available && !it.apiKey.isNullOrBlank() }
            ?: models.firstOrNull {
                (it.kind == null || it.kind == "text") && it.available && !it.apiKey.isNullOrBlank()
            }
    }

    /**
     * 小说正文当前生效的模型条目：
     * - 单独选了小说模型（非空）则走它，无内置 key 时同样降级到默认小说模型，再降级到任一带 key 的可用模型；
     * - 未单独选（空串，默认）则走默认小说模型（目录标记 [ModelEntry.defaultNovel]，即赞助付费模型），
     *   仍无则降级到文本翻译默认模型，保证小说永远能翻而不静默跟随文本。
     */
    internal fun effectiveNovelEntry(): ModelEntry? {
        val novelSelected = selectedNovelEntry()
        if (novelSelected != null) {
            if (!novelSelected.apiKey.isNullOrBlank()) return novelSelected
            return defaultNovelEntry()
                ?: modelCatalogRepository.models.value.firstOrNull { it.available && !it.apiKey.isNullOrBlank() }
        }
        return defaultNovelEntry() ?: effectiveTextEntry()
    }

    /** 小说正文默认模型：目录里标记 [ModelEntry.defaultNovel] 的优先，否则首个小说类可用模型 */
    internal fun defaultNovelEntry(): ModelEntry? {
        val models = modelCatalogRepository.models.value
        return models.firstOrNull { it.defaultNovel && it.available && !it.apiKey.isNullOrBlank() }
            ?: models.firstOrNull {
                it.kind == "novel" && it.available && !it.apiKey.isNullOrBlank()
            }
    }

    /**
     * 生效 key：选中模型自带的内置共享 key（来自加密远程目录，零配置可用）；
     * 调试构建最后回退 BuildConfig 注入的调试 key
     * （来自环境变量 PIKU_LLM_API_KEY / local.properties，不入库）。
     */
    private fun apiKeyFor(entry: ModelEntry?): String {
        entry?.apiKey?.takeIf { it.isNotBlank() }?.let { return it }
        if (BuildConfig.DEBUG) return BuildConfig.DEBUG_LLM_API_KEY
        return ""
    }

    /** 设置里存的是目录 id 或裸模型名（如默认 glm-4-flash），两种都做匹配 */
    private fun selectedEntry(): ModelEntry? {
        val selected = settingsRepository.llmModel.value.trim()
        if (selected.isEmpty()) return null
        val models = modelCatalogRepository.models.value
        return models.firstOrNull { it.id == selected }
            ?: models.firstOrNull { it.model == selected }
    }

    /** 小说专用模型同规则匹配；空串表示未单独选（走默认小说模型，不跟随文本） */
    private fun selectedNovelEntry(): ModelEntry? {
        val selected = settingsRepository.llmNovelModel.value.trim()
        if (selected.isEmpty()) return null
        val models = modelCatalogRepository.models.value
        return models.firstOrNull { it.id == selected }
            ?: models.firstOrNull { it.model == selected }
    }

    companion object {

        /** 自动/顶栏路径允许翻译的正文长度上限；更长的正文只在阅读器内显式触发 */
        const val AUTO_NOVEL_MAX_CHARS = 200

        /** 故障转移前的全抖动延迟范围：把同时失败的重试在时间维度打散 */
        private const val FAILOVER_JITTER_MIN_MS = 800L
        private const val FAILOVER_JITTER_MAX_MS = 2500L

        /**
         * 把翻译结果对齐回字段。与原文相同的结果（预检透传/模型原样返回）视为无译文丢弃——
         * 否则纯中文作品在简中目标下会生成"全字段假译文"，hasAny=true 导致
         * 顶栏与各字段 chip 全部出现、点击却毫无变化。
         *
         * 短文本字段（标题/简介/作者简介 + 标签）与小说正文由不同模型分别翻译，
         * 故 [shortOut] 与 [novelOut] 分开传入：[shortOut] 顺序严格为
         * 标题/简介/作者简介 + 标签组；[novelOut] 为小说正文译文（无则 null）。
         */
        internal fun buildFields(
            titleSrc: String,
            descriptionSrc: String,
            profileSrc: String,
            tagsSrc: List<String>,
            shortOut: List<String>,
            novelSrc: String,
            novelOut: String?,
        ): TranslatedFields? {
            val tags = if (tagsSrc.isNotEmpty()) {
                val mapped = tagsSrc.mapIndexed { i, src ->
                    (shortOut.getOrNull(3 + i) ?: "").ifBlank { src }
                }
                // 整组与原文相同（如全部被预检透传）则视为无译文
                mapped.takeIf { list -> list.withIndex().any { (i, v) -> v != tagsSrc[i] } }
            } else {
                null
            }
            fun clean(src: String, value: String?): String? =
                value?.takeIf { it.isNotBlank() && it != src }
            val fields = TranslatedFields(
                title = clean(titleSrc, shortOut.getOrNull(0)),
                description = clean(descriptionSrc, shortOut.getOrNull(1)),
                authorProfile = clean(profileSrc, shortOut.getOrNull(2)),
                tags = tags,
                novelText = clean(novelSrc, novelOut),
            )
            return fields.takeIf { it.hasAny }
        }

        /** 目标语言：跟随系统时按中文处理（本 app 主要受众） */
        internal fun targetLangName(language: AppLanguage): String = when (language) {
            AppLanguage.EN -> "English"
            AppLanguage.JA -> "Japanese"
            else -> "Simplified Chinese"
        }

        internal fun hash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * 行分解：含链接的文本按行拆分（链接行提取为 [Keep]，文字行各自成翻译单元）；
         * 不含链接或 [splitLines] = false（小说正文，避免切碎散文上下文）时整条一个单元，
         * 缓存键与历史行为保持一致。
         */
        internal fun decomposeLines(text: String, splitLines: Boolean): List<LinePart> {
            if (LlmTranslateEngine.isPureLink(text)) return listOf(LinePart.Keep(text))
            if (!splitLines || !LlmTranslateEngine.containsLink(text)) {
                val (stripped, links) = LlmTranslateEngine.extractLinks(text)
                return listOf(LinePart.Translate(stripped, links, text))
            }
            return text.split('\n').map { line ->
                when {
                    line.isBlank() -> LinePart.Keep(line)
                    LlmTranslateEngine.isPureLink(line) -> LinePart.Keep(line)
                    else -> {
                        val (stripped, links) = LlmTranslateEngine.extractLinks(line)
                        // 摘除后不剩文字（极端边角）按原样保留，避免为空行浪费请求
                        if (stripped.isBlank()) LinePart.Keep(line)
                        else LinePart.Translate(stripped, links, line)
                    }
                }
            }
        }
    }
}
