package com.piku.client.data.remote.translation

import com.piku.client.BuildConfig
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.local.TranslationDao
import com.piku.client.data.local.TranslationEntity
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.TranslatedFields
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.translation.ChunkContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
 * 小说分块流式翻译的事件流。
 *
 * - [Progress]：一块完成即发；[translatedSoFar] 为已译部分拼接，
 *   [consumedOffset] 为已消费原文偏移量——UI 按原文切片得到剩余尾部，拼成
 *   "已译前缀 + 剩余原文"实现边翻边读；
 * - [Completed]：全部块处理完。[failedCount] > 0 表示有块失败（已回退原文展示），
 *   调用方可提示重试——重试重启流，已成功块全部缓存秒过，只有失败块真正上网。
 */
sealed interface NovelStreamEvent {
    data class Progress(
        val translatedSoFar: String,
        val consumedOffset: Int,
        val doneChunks: Int,
        val totalChunks: Int,
    ) : NovelStreamEvent

    data class Completed(
        val translatedSoFar: String,
        val failedCount: Int,
        val totalChunks: Int,
    ) : NovelStreamEvent
}

/**
 * 短文本字段译文（类型安全：替代裸 [List] 的下标约定）。
 * 顺序固定为 标题/简介/作者简介 + 标签组，调用方按名取用，避免新增字段时下标错位。
 */
internal data class ShortTranslation(
    val title: String = "",
    val description: String = "",
    val authorProfile: String = "",
    val tags: List<String> = emptyList(),
)

/**
 * 各场景当前生效的默认模型 id（已按可用 + 带内置 key 过滤）。
 * 模型选择器用它高亮"未手动选择时的默认项"，与实际翻译走同一套解析语义。
 */
data class RoleDefaultIds(
    val text: String? = null,
    val novel: String? = null,
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

    /** 距上次淘汰后累计写入行数；超过阈值才触发 COUNT(*) + 淘汰，避免每次 persist 都全表扫描 */
    private var rowsSinceEviction = 0

    /**
     * 写缓存并按容量 FIFO 淘汰：超过 [CACHE_MAX_ROWS] 时删除最旧的行，
     * 防止重度用户缓存表无限膨胀。命中不续期（真 LRU 需要读时写时间戳）——
     * 收藏作品长期后被挤出只会重新翻译一次，可接受。
     */
    private suspend fun persist(rows: List<TranslationEntity>) {
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) {
            dao.upsertAll(rows)
            rowsSinceEviction += rows.size
            if (rowsSinceEviction >= EVICTION_CHECK_THRESHOLD) {
                val total = dao.count()
                if (total > CACHE_MAX_ROWS) dao.deleteOldest(total - CACHE_MAX_ROWS)
                rowsSinceEviction = 0
            }
        }
    }

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
     * 各场景默认模型 id 流：目录列表或 defaults.roles 变化时重算。
     * 供抽屉模型选择器高亮默认项（用户未手动选择时实际会走的那个模型）。
     */
    val roleDefaultIds: Flow<RoleDefaultIds> = combine(
        modelCatalogRepository.models,
        modelCatalogRepository.catalogDefaults,
    ) { models, defaults ->
        val roleDefaults = defaults?.roles ?: emptyMap()
        RoleDefaultIds(
            text = ModelCatalog.resolveRoleDefault(Role.TEXT, models, roleDefaults)?.id,
            novel = ModelCatalog.resolveRoleDefault(Role.NOVEL, models, roleDefaults)?.id,
        )
    }

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
        // 小说正文单独走小说专用通道；解析不出可用小说模型时正文保留原文（宁缺毋滥），
        // 绝不静默借用文本模型——两条通道彻底隔离。正文整条单单元送翻，
        // 散文跨行句保持上下文，行内罕见链接走摘除+末尾拼回兜底。
        // 一次性重翻时，短字段已用 forcedEntry，小说正文同样强制用它以保证一致。
        val novelTranslated = if (novelSource == null) {
            null
        } else {
            val novelEntry = forcedEntry ?: effectiveNovelEntry()
            novelEntry?.let {
                translateAll(
                    listOf(novelSource),
                    targetLang,
                    entryOverride = it,
                    role = Role.NOVEL,
                )?.firstOrNull()
            }
        }
        // 两段都缺席（网络/限速导致整批失败）才判真失败；
        // 仅一段失败则保留另一段成果，不阻塞展示。
        if (shortTranslated == null && novelTranslated == null) {
            return TranslationOutcome(fields = null, failed = true)
        }
        val shortList = shortTranslated ?: List(3 + detail.tags.size) { "" }
        val short = ShortTranslation(
            title = shortList.getOrNull(0) ?: "",
            description = shortList.getOrNull(1) ?: "",
            authorProfile = shortList.getOrNull(2) ?: "",
            tags = shortList.subList(3, shortList.size),
        )
        val fields = buildFields(
            titleSrc = detail.title,
            descriptionSrc = detail.description,
            profileSrc = detail.authorProfile,
            tagsSrc = detail.tags,
            shortOut = short,
            novelSrc = novelSource ?: "",
            novelOut = novelTranslated,
        ) ?: return TranslationOutcome(fields = null, failed = false)
        return TranslationOutcome(fields = fields, failed = false)
    }

    /**
     * 小说正文分块流式翻译（阅读器"译"入口专用）：
     * 切分 → 逐块顺序翻译，每块完成即发射事件并独立落缓存。
     *
     * - 每块携带跨块上下文（上块尾部原文+译文 + 标签译名对照表），保人名与腔调连贯；
     * - 预检透传 / 缓存命中 / 失败回退原文续流，语义与 [translateAll] 对齐；
     * - 缓存键 = (摘链后原文 SHA-256, 目标语言, 引擎 id)：断点续翻、二次打开全免费；
     * - 不抢全局 [mutex]：块本身顺序执行已足够温和，短字段翻译可与之并行
     *   （免费 key 的瞬时并发碰撞概率极低，v1 接受）；
     * - 解析不出可用小说模型时发空 [NovelStreamEvent.Completed]——宁缺毋滥，
     *   绝不向文本通道借模型。
     */
    fun translateNovelStreaming(
        detail: WorkDetail,
        language: AppLanguage,
        forcedEntry: ModelEntry? = null,
    ): Flow<NovelStreamEvent> = flow {
        val source = detail.novelText
        if (source.isBlank()) return@flow
        val entry = forcedEntry ?: effectiveNovelEntry()
        val engine = entry?.let {
            engineFactory.create(apiKeyFor(it), Role.NOVEL, it, modelCatalogRepository.catalogDefaults.value)
        }
        if (engine == null) {
            emit(NovelStreamEvent.Completed(translatedSoFar = "", failedCount = 0, totalChunks = 0))
            return@flow
        }
        val targetLang = targetLangName(language)
        val glossary = tagGlossaryBlock(detail)
        // 缓存键追加对照表指纹：glossary 影响译文（人名锚定）却不改变原文哈希，
        // 不入键则同一块正文在不同作品（不同标签译名表）间会共享错误缓存
        val engineId = engine.engineId + (glossary?.let { "#g:" + hash(it).take(8) }.orEmpty())
        val chunks = NovelChunker.split(source)
        val total = chunks.size

        val translated = StringBuilder()
        var consumed = 0
        var failedCount = 0
        var tailOriginal = ""
        var tailTranslated = ""
        // 累积待落盘实体，达到 STREAM_PERSIST_BATCH 或流结束时批量写入
        val pendingPersist = mutableListOf<TranslationEntity>()
        suspend fun flushPersist() {
            if (pendingPersist.isNotEmpty()) {
                persist(pendingPersist.toList())
                pendingPersist.clear()
            }
        }

        for ((index, chunk) in chunks.withIndex()) {
            val context = if (index == 0) {
                null
            } else {
                ChunkContext(
                    originalTail = tailOriginal,
                    translatedTail = tailTranslated,
                    glossaryBlock = glossary,
                )
            }
            // 块级透传契约（勿上提到作品级、勿删除）：整块已是目标语言 → 跳过不花请求；
            // 掺杂任何其他语言（假名/英文句/其他字系，含半角片假名）→ 必须送翻。
            // 中日混排块在 ja 目标下由 hanDominantOverKana 保证中文为主时仍送翻，
            // zh 目标下"汉字≥英文词×2"仅容忍点缀性英文词。逐条语义见 isAlreadyInTarget 测试矩阵。
            val value: String? = when {
                LlmTranslateEngine.isAlreadyInTarget(chunk.text, targetLang) -> chunk.text
                else -> {
                    // 缓存键与主通道一致：摘链接后的纯文字哈希；命中值含拼回的链接
                    val (stripped, links) = LlmTranslateEngine.extractLinks(chunk.text)
                    if (stripped.isBlank()) {
                        // 整块都是链接/空白（如正文里单独成行的 URL）：无翻内容，
                        // 原样保留且不计入失败——否则会产生假"N 段未译出"警报
                        chunk.text
                    } else {
                        val cached = withContext(Dispatchers.IO) {
                            dao.get(cacheKey(stripped, links), targetLang, engineId)
                        }
                        cached ?: run {
                            var translatedText: String? = null
                            repeat(1 + CHUNK_RETRY_ATTEMPTS) { attempt ->
                                if (translatedText != null) return@repeat
                                try {
                                    val out = engine.translate(listOf(stripped), targetLang, context)
                                        .firstOrNull().orEmpty()
                                    if (out.isBlank()) {
                                        if (attempt < CHUNK_RETRY_ATTEMPTS) delay(chunkRetryDelay(attempt))
                                        return@repeat
                                    }
                                    val full = if (links.isEmpty()) out
                                    else out.trimEnd() + links.joinToString("") { " $it" }
                                    pendingPersist += TranslationEntity(
                                        srcHash = cacheKey(stripped, links),
                                        targetLang = targetLang,
                                        engineId = engineId,
                                        translated = full,
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                    if (pendingPersist.size >= STREAM_PERSIST_BATCH) flushPersist()
                                    translatedText = full
                                } catch (e: TranslationApiException) {
                                    if (attempt < CHUNK_RETRY_ATTEMPTS && e.code != 0) {
                                        delay(chunkRetryDelay(attempt))
                                    }
                                }
                            }
                            if (translatedText == null) failedCount++
                            translatedText
                        }
                    }
                }
            }
            // 失败块回退原文继续流：局部失败不整体放弃（与主通道哲学一致）
            val piece = value ?: chunk.text
            translated.append(piece)
            consumed += chunk.text.length + chunk.separatorAfter.length
            emit(
                NovelStreamEvent.Progress(
                    translatedSoFar = translated.toString(),
                    consumedOffset = consumed.coerceAtMost(source.length),
                    doneChunks = index + 1,
                    totalChunks = total,
                ),
            )
            tailOriginal = NovelChunker.tail(chunk.text, NovelChunker.TAIL_ORIGINAL_CHARS)
            tailTranslated = NovelChunker.tail(piece, NovelChunker.TAIL_TRANSLATED_CHARS)
        }
        flushPersist()
        emit(
            NovelStreamEvent.Completed(
                translatedSoFar = translated.toString(),
                failedCount = failedCount,
                totalChunks = total,
            ),
        )
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
     * 链接处理：每条文本整条摘除链接后作为单一单元送翻（段数不随行数膨胀，
     * 小模型友好），纯链接文本整体跳过；译文回来后链接原样拼在末尾——
     * URL 既不进 prompt 拖慢请求，也不会被小模型改坏。
     *
     * 整批失败（网络错/限速/模型抽风导致全部空返回）时做一次故障转移：
     * 随机换一个同场景（[role]）带内置 key 的其他免费模型，加全抖动短延迟后重试——
     * 时间与模型两个维度同时打散，多用户不会同步挤兑同一把 key。
     */
    suspend fun translateAll(
        texts: List<String>,
        targetLang: String,
        entryOverride: ModelEntry? = null,
        role: String = Role.TEXT,
    ): List<String>? =
        mutex.withLock {
            val entry = entryOverride ?: effectiveTextEntry()
            var activeEngine = engineFactory.create(
                apiKeyFor(entry), role, entry, modelCatalogRepository.catalogDefaults.value,
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
                val parts = decomposeLines(text)
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
                // 批量缓存读取：一次查询取回所有待查单元的译文，避免逐条 DB 往返（N+1）
                val toLookup = units.withIndex().filter { it.value.value == null }
                if (toLookup.isNotEmpty()) {
                    val hashes = toLookup.map { cacheKey(it.value.stripped, it.value.links) }
                    val cachedMap = dao.getAll(hashes, targetLang, engineId)
                        .associate { it.srcHash to it.translated }
                    toLookup.forEachIndexed { i, indexed ->
                        val cached = cachedMap[hashes[i]]
                        if (cached != null) {
                            units[indexed.index].value = cached
                        } else {
                            pending += indexed.index
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                // 送翻的都是摘除链接后的纯文字，URL 不出现在 prompt 里
                val strippedPending = pending.map { units[it].stripped }
                var fresh = activeEngine.translate(strippedPending, targetLang)
                var failedOver = false
                if (fresh.all { it.isBlank() }) {
                    val alternative = ModelCatalog.failoverCandidate(
                        entry, role, modelCatalogRepository.models.value,
                    )
                    val fallbackEngine = alternative?.let {
                        engineFactory.create(
                            it.apiKey.orEmpty(), role, it, modelCatalogRepository.catalogDefaults.value,
                        )
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
                                    srcHash = cacheKey(unit.stripped, unit.links),
                                    targetLang = targetLang,
                                engineId = engine,
                                translated = value,
                                updatedAt = now,
                            )
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    persist(rows)
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
     * 文本翻译当前生效的模型条目：地址、模型名、key 三者必须同源，所以降级是整条切换。
     * key 只来自加密远程目录（debug 构建回退注入的调试 key）：
     * - 选中项带内置共享 key 就用它；
     * - 否则（如历史遗留的自选模型没有内置 key）降级到文本场景默认，
     *   再降级到任一带 key 的可用模型，避免整段翻译静默不可用。
     */
    internal fun effectiveTextEntry(): ModelEntry? {
        val selected = selectedEntry()
        if (selected != null && !selected.apiKey.isNullOrBlank()) return selected
        return defaultRoleEntry(Role.TEXT)
            ?: modelCatalogRepository.models.value.firstOrNull {
                it.available && !it.apiKey.isNullOrBlank()
            }
    }

    /**
     * 小说正文当前生效的模型条目：
     * - 单独选了小说模型（非空）则走它，无内置 key 时降级到小说场景默认；
     * - 未单独选（空串，默认）则直接走小说场景默认
     *   （目录 [CatalogDefaults.roles] 里 novel 指向的赞助付费模型）。
     *
     * 与文本通道的唯一交叉点是没有：解析不出可用小说模型时返回 null，
     * 调用方按宁缺毋滥让正文保留原文——绝不向文本通道借模型。
     */
    internal fun effectiveNovelEntry(): ModelEntry? {
        val novelSelected = selectedNovelEntry()
        if (novelSelected != null && !novelSelected.apiKey.isNullOrBlank()) return novelSelected
        return defaultRoleEntry(Role.NOVEL)
    }

    /** 场景默认模型：目录 defaults.roles[role] 优先，退到首个该 role 的可用带 key 条目 */
    private fun defaultRoleEntry(role: String): ModelEntry? =
        ModelCatalog.resolveRoleDefault(
            role,
            modelCatalogRepository.models.value,
            modelCatalogRepository.catalogDefaults.value?.roles ?: emptyMap(),
        )

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

    /** 块级重试延迟：指数退避 + 随机抖动 */
    private fun chunkRetryDelay(attempt: Int): Long {
        val exp = CHUNK_RETRY_DELAY_BASE_MS * (1L shl attempt.coerceAtMost(3))
        return exp + Random.nextLong(0, 500)
    }

    /**
     * 设置里存的是目录 id 或裸模型名（如默认 glm-4-flash），两种都做匹配。
     * 目录权威高于历史选中：指向已下架/被撤 key 条目的旧值视为未选择，
     * 回落到场景默认（见 [ModelCatalog.resolveStoredSelection]）。
     * role 过滤保证文本通道的旧选中值不会命中小说条目（反之亦然）。
     */
    private fun selectedEntry(): ModelEntry? =
        ModelCatalog.resolveStoredSelection(
            settingsRepository.llmModel.value,
            modelCatalogRepository.models.value,
            Role.TEXT,
        )

    /** 小说专用模型同规则匹配；空串或失效值表示未选择（走目录小说默认，不跟随文本） */
    private fun selectedNovelEntry(): ModelEntry? =
        ModelCatalog.resolveStoredSelection(
            settingsRepository.llmNovelModel.value,
            modelCatalogRepository.models.value,
            Role.NOVEL,
        )

    companion object {

        private val SHA_256 = object : ThreadLocal<MessageDigest>() {
            override fun initialValue() = MessageDigest.getInstance("SHA-256")
        }

        private val HEX_DIGITS = "0123456789abcdef".toCharArray()

        /** 自动/顶栏路径允许翻译的正文长度上限；更长的正文只在阅读器内显式触发 */
        const val AUTO_NOVEL_MAX_CHARS = 200

        /** 标签对照表最多注入的条数（防超长标签列表撑爆提示词） */
        private const val GLOSSARY_MAX_ENTRIES = 15

        /**
         * 译文缓存容量上限（行数）。每行约几十字节到 1KB 不等，
         * 2 万行 ≈ 几十 MB 以内，重度用户（数千作品）也够用；
         * 超出后按写入时间 FIFO 淘汰最旧行。
         */
        private const val CACHE_MAX_ROWS = 20_000

        /** 累计写入这么多行后才触发 COUNT(*) + 淘汰检查（允许暂时超出上限少量） */
        private const val EVICTION_CHECK_THRESHOLD = 500

        /**
         * 流式落盘批大小：正文字节逐块成功后先累积，达到该数量或流结束时一次性
         * upsert+淘汰，把每块一次 DB 往返降到约 1/STREAM_PERSIST_BATCH；
         * 仍周期性写入，流中途取消/崩溃时近期块已入缓存可复读。
         */
        private const val STREAM_PERSIST_BATCH = 8

        /** 块级重试次数（不含首次尝试） */
        private const val CHUNK_RETRY_ATTEMPTS = 2

        /** 块级重试延迟基数（ms）：指数退避 1s → 2s → 4s... */
        private const val CHUNK_RETRY_DELAY_BASE_MS = 1000L

        /** 故障转移前的全抖动延迟范围：把同时失败的重试在时间维度打散 */
        private const val FAILOVER_JITTER_MIN_MS = 800L
        private const val FAILOVER_JITTER_MAX_MS = 2500L

        /**
         * 把翻译结果对齐回字段。与原文相同的结果（预检透传/模型原样返回）视为无译文丢弃——
         * 否则纯中文作品在简中目标下会生成"全字段假译文"，hasAny=true 导致
         * 顶栏与各字段 chip 全部出现、点击却毫无变化。
         *
         * 短文本字段（标题/简介/作者简介 + 标签）与小说正文由不同模型分别翻译，
         * 故 [shortOut]（[ShortTranslation]）与 [novelOut] 分开传入；[novelOut] 为小说正文译文（无则 null）。
         */
        internal fun buildFields(
            titleSrc: String,
            descriptionSrc: String,
            profileSrc: String,
            tagsSrc: List<String>,
            shortOut: ShortTranslation,
            novelSrc: String,
            novelOut: String?,
        ): TranslatedFields? {
            val tags = if (tagsSrc.isNotEmpty()) {
                val mapped = tagsSrc.mapIndexed { i, src ->
                    (shortOut.tags.getOrNull(i) ?: "").ifBlank { src }
                }
                // 整组与原文相同（如全部被预检透传）则视为无译文
                mapped.takeIf { list -> list.withIndex().any { (i, v) -> v != tagsSrc[i] } }
            } else {
                null
            }
            fun clean(src: String, value: String?): String? =
                value?.takeIf { it.isNotBlank() && it != src }
            val fields = TranslatedFields(
                title = clean(titleSrc, shortOut.title),
                description = clean(descriptionSrc, shortOut.description),
                authorProfile = clean(profileSrc, shortOut.authorProfile),
                tags = tags,
                novelText = clean(novelSrc, novelOut),
            )
            return fields.takeIf { it.hasAny }
        }

        /** 目标语言：跟随系统时按中文处理（本 app 主要受众） */
        internal fun targetLangName(language: AppLanguage): String = when (language) {
            AppLanguage.EN -> LlmTranslateEngine.TARGET_EN
            AppLanguage.JA -> LlmTranslateEngine.TARGET_JA
            else -> LlmTranslateEngine.TARGET_ZH
        }

        /**
         * 标签译名对照表（同人场景的术语锚定）：
         * 作品 tags 原文 × 已有译文按位配对——原作角色/CP 名的圈子惯例译法
         * 由文本通道产出，直接复用，零额外请求。无标签或未翻完时返回 null，
         * 小说分块退化为纯尾部窗口模式。
         */
        internal fun tagGlossaryBlock(detail: WorkDetail): String? {
            val srcTags = detail.tags.map { it.trim() }.filter { it.isNotEmpty() }
            if (srcTags.isEmpty()) return null
            val translated = detail.translated?.tags ?: return null
            val pairs = srcTags.take(GLOSSARY_MAX_ENTRIES).mapIndexedNotNull { i, src ->
                val dst = translated.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() && it != src }
                    ?: return@mapIndexedNotNull null
                "$src→$dst"
            }
            if (pairs.isEmpty()) return null
            return "【本作标签对照 · 人名等专名严格照此翻译】\n" + pairs.joinToString("\n")
        }

        internal fun hash(text: String): String {
            val digest = SHA_256.get()!!.apply { reset() }
            val bytes = digest.digest(text.toByteArray())
            val hex = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                hex[i * 2] = HEX_DIGITS[v shr 4]
                hex[i * 2 + 1] = HEX_DIGITS[v and 0xF]
            }
            return String(hex)
        }

        /**
         * 缓存键由摘链后文字 [stripped] 与链接序列 [links] 共同决定，
         * 使文字相同但链接不同的行各自拥有独立缓存。
         */
        internal fun cacheKey(stripped: String, links: List<String>): String =
            hash(stripped + links.joinToString(""))

        /**
         * 行分解：整条文本作为单一处理单元——
         * - 整条是链接（或摘除后不剩文字）→ [Keep] 原样回填，永不送模型；
         * - 否则 → [Translate]：[stripped] 为摘除链接后的送翻文字，
         *   [links] 是按出现顺序的原链接片段，译文末尾原样拼回。
         *
         * 历史：曾对含链接文本按行拆分（链接行 Keep、文字行各成单元），让纯链接行
         * 零成本跳过——代价是批量段数随行数膨胀，小模型逐段输出显著变慢。
         * 现改为整条摘链单单元：段数恒定，速度优先；链接位置从"各行尾"退为"整条末尾"。
         */
        internal fun decomposeLines(text: String): List<LinePart> {
            if (LlmTranslateEngine.isPureLink(text)) return listOf(LinePart.Keep(text))
            val (stripped, links) = LlmTranslateEngine.extractLinks(text)
            // 摘除后不剩文字：整条都是链接/空白，原样保留不为空内容浪费请求
            if (stripped.isBlank()) return listOf(LinePart.Keep(text))
            return listOf(LinePart.Translate(stripped, links, text))
        }
    }
}
