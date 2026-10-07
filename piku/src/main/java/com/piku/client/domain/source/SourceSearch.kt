package com.piku.client.domain.source

import androidx.annotation.StringRes
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.WorkSource

/**
 * 搜索插件：源声明自己的检索能力，外壳照声明渲染，不穷举站点。
 * 没注册的源走外壳的默认搜索（现 poipiku 链路），行为不变。
 */
interface SourceSearch {

    val sourceId: WorkSource

    /** 结果卡片按原图比例排版（带尺寸的源），否则方图卡片 */
    val proportional: Boolean get() = false

    /** 用户 tab 可用；不可用的源外壳仍显示 tab，但列表给"本源不支持"终态 */
    val supportsUsers: Boolean get() = false

    /**
     * 检索筛选声明：外壳渲染成 chips 行 + 底部面板。空 = 该源没有筛选。
     * 选中值随 [searchWorks] 的 filters 传回：组 → 选中项 id，开关 → "1"/不传
     */
    val filterGroups: List<SearchFilterGroupSpec> get() = emptyList()

    val filterToggles: List<SearchFilterToggleSpec> get() = emptyList()

    /** 待机态热门标签（带代表作缩略图）；取不到或没声明就给空，外壳隐藏该区 */
    suspend fun trendingTags(): Result<List<SourceTrendingTag>> = Result.success(emptyList())

    /** 输入联想（标签 + 译名）；空 = 外壳不显示联想层 */
    suspend fun suggest(query: String): Result<List<SourceSuggestion>> = Result.success(emptyList())

    /**
     * 标签 tab 建议网格的标签：与 [suggest] 同词，但条目可带代表缩略图。
     * 输入联想不需要图，两条链路分开声明；默认与输入联想同路（无图）。
     */
    suspend fun suggestTags(query: String): Result<List<SourceSuggestion>> = suggest(query)

    /** 作品检索。filters = 各组当前选中项 id + 置真的开关；page 从 0 起 */
    suspend fun searchWorks(
        query: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage>

    /** 标签 tab 里选中某个标签后的作品列表；默认与普通检索同路 */
    suspend fun searchTagWorks(
        tag: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = searchWorks(tag, filters, page)

    /**
     * 小说检索。[FILTER_KIND] 组选中 [KIND_NOVEL] 时外壳走这里，与作品检索互斥。
     * 声明了 kind 组的源必须实现它。
     */
    suspend fun searchNovels(
        query: String,
        filters: Map<String, String>,
        page: Int,
    ): Result<SourcePage> = Result.failure(AppError.NotFound)

    /**
     * 当前选择下要隐藏的筛选组：档位不同，接口吃得的参数不同
     * （搜小说不支持按投稿期间、检索对象枚举也不同），隐藏比置灰诚实。
     */
    fun hiddenFilterGroups(selected: Map<String, String>): Set<String> = emptySet()

    /** 用户检索；仅 supportsUsers 的源会被调到 */
    suspend fun searchUsers(query: String, page: Int): Result<List<FollowUser>> =
        Result.success(emptyList())

    /** 用户行的关注切换，与列表关注按钮同一后端 */
    suspend fun toggleFollow(userId: Long, follow: Boolean): Result<Unit> = Result.success(Unit)

    /** 点用户的去向；null = 外壳按默认（poipiku 用户页）处理 */
    fun userPage(user: FollowUser): SourceAuthorOpen? = null

    companion object {
        /** 作品类型维度组：声明了它的源，外壳在小说档走 [searchNovels] */
        const val FILTER_KIND = "kind"
        const val KIND_ALL = "all"
        const val KIND_NOVEL = "novel"
    }
}

/** [SourceSearch] 的 filters 里开关置真值（关 = 键不出现） */
const val FILTER_TOGGLE_ON = "1"

/** 一组单选筛选（排序 / 检索对象 / 期间…）。default 项即"不带该参数"的中性态 */
data class SearchFilterGroupSpec(
    val id: String,
    @StringRes val labelRes: Int,
    val options: List<SearchFilterOptionSpec>,
)

data class SearchFilterOptionSpec(
    val id: String,
    @StringRes val labelRes: Int,
    val default: Boolean = false,
)

/** 布尔型筛选（隐藏 AI…），外壳渲染成开关行 */
data class SearchFilterToggleSpec(
    val id: String,
    @StringRes val labelRes: Int,
)

/** 待机态热门标签：tag + 译名 + 代表作（带原作宽高，供瀑布流按比例排版） */
data class SourceTrendingTag(
    val name: String,
    val translatedName: String? = null,
    val thumbnailUrl: String,
    val width: Int = 0,
    val height: Int = 0,
)

/** 输入联想条目：标签名 + 译名（译名缺省 = 该标签没有简中翻译）。缩略图仅标签建议网格消费 */
data class SourceSuggestion(
    val name: String,
    val translatedName: String? = null,
    val thumbnailUrl: String? = null,
)
