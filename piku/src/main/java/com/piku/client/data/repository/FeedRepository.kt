package com.piku.client.data.repository

import com.piku.client.data.local.PopularTagCacheRepository
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.FollowFeedParser
import com.piku.client.data.remote.FollowUserParser
import com.piku.client.data.remote.NewArrivalParser
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.PopularTagParser
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.TagCardParser
import com.piku.client.data.remote.UserPageParser
import com.piku.client.data.remote.UserSearchParser
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.AppError
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.PopularTag
import com.piku.client.domain.model.TagCard
import com.piku.client.domain.model.UserWorksPage
import com.piku.client.domain.model.Work
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class FeedRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val sessionMonitor: SessionMonitor,
    private val popularTagCacheRepository: PopularTagCacheRepository,
) {

    // 列表解析放 Default：正则扫描留在主线程会卡住"新一页到达"那一帧
    suspend fun getNewArrivals(page: Int, categoryCd: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            withContext(Dispatchers.Default) {
                NewArrivalParser.parse(api.getNewArrivals(page, categoryCd).string())
            }.let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getPopularIllusts(page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            withContext(Dispatchers.Default) {
                NewArrivalParser.parse(api.getPopularIllusts(page).string())
            }.let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getRandomPickups(): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            withContext(Dispatchers.Default) {
                NewArrivalParser.parse(api.getRandomPickup().string())
            }.let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getFollowFeed(page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            val html = withContext(Dispatchers.Default) { api.getFollowFeed(page).string() }
            withContext(Dispatchers.Default) { FollowFeedParser.parse(html) }
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getFollowUsers(page: Int): Result<FollowUserPage> =
        apiCall {
            val html = withContext(Dispatchers.Default) {
                if (page == 0) {
                    api.getFollowSettingPage("FOLLOW").string()
                } else {
                    api.getFollowList(FOLLOW_LIST_MAX, 0, page).string()
                }
            }
            val users = withContext(Dispatchers.Default) { FollowUserParser.parse(html) }
            val total = if (page == 0) {
                withContext(Dispatchers.Default) { FollowUserParser.parseTotal(html) }
            } else {
                null
            }
            FollowUserPage(users = users, total = total ?: users.size)
        }

    /**
     * 屏蔽列表（BlockListF，MD=1）。与关注列表不同，服务端不返回 TOTAL，
     * 分页只能以"返回空列表"为终点；列表项结构与 FollowListF 相同，复用同一解析器。
     */
    suspend fun getBlockUsers(page: Int): Result<List<FollowUser>> =
        apiCall {
            val html = withContext(Dispatchers.Default) {
                api.getBlockList(FOLLOW_LIST_MAX, BLOCK_LIST_MD, page).string()
            }
            withContext(Dispatchers.Default) { FollowUserParser.parse(html) }
        }


    suspend fun getUserWorks(userId: Long, page: Int): Result<UserWorksPage> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            val self = authRepository.currentUserId() == userId
            val html = withContext(Dispatchers.Default) {
                if (self) {
                    api.getMyIllusts(userId, page).string()
                } else {
                    api.getUserIllusts(userId, "", page).string()
                }
            }
            val pageInfo = withContext(Dispatchers.Default) {
                when {
                    page != 0 -> null
                    self -> runCatching { api.getUserIllusts(userId, "", 0).string() }
                        .getOrNull()
                        ?.let(UserPageParser::parse)
                    else -> UserPageParser.parse(html)
                }
            }
            val profile = profileRepository.userProfile.value
            val parsed = withContext(Dispatchers.Default) {
                if (self) {
                    // マイボックス列表块内不带作者区，用页主资料（页面优先，本地资料兜底）回填
                    NewArrivalParser.parse(
                        html = html,
                        authorFallbackId = userId,
                        authorFallbackName = pageInfo?.userName ?: profile?.name.orEmpty(),
                        authorFallbackAvatarUrl = pageInfo?.avatarUrl ?: profile?.avatarUrl,
                    )
                } else {
                    NewArrivalParser.parse(html)
                }
            }
            val works = if (adultEnabled) parsed else parsed.filter { !it.warning }
            UserWorksPage(works = works, pageInfo = pageInfo)
        }

    /** 进程内热门标签 memo：每次冷启动失效（下次启动重新拉取） */
    @Volatile
    private var popularTagsMemo: List<PopularTag>? = null

    suspend fun getPopularTags(): Result<List<PopularTag>> {
        popularTagsMemo?.let { return Result.success(it) }
        val fetched = apiCall {
            withContext(Dispatchers.Default) {
                PopularTagParser.parse(api.getPopularTags().string())
            }
        }
        if (fetched.isSuccess) {
            val tags = fetched.getOrThrow()
            popularTagsMemo = tags
            if (tags.isNotEmpty()) popularTagCacheRepository.save(tags)
            return fetched
        }
        // 网络失败时回退到上次缓存的标签
        return popularTagCacheRepository.load()?.let { Result.success(it) } ?: fetched
    }

    suspend fun getUserSearch(keyword: String, page: Int): Result<List<FollowUser>> =
        apiCall {
            val html = withContext(Dispatchers.Default) { api.getUserSearch(keyword, page).string() }
            withContext(Dispatchers.Default) { UserSearchParser.parse(html) }
        }.onFailure { error ->
            // 会话失效的正路是 cookie jar 的空白 POIPIKU_LK；这里兜的是
            // 过期 token 下该接口直接 404 那一路（注意 apiCall 把 404 映射成 NotFound）
            if (authRepository.isLoggedIn() && error is AppError.NotFound) {
                sessionMonitor.notifySessionCleared()
            }
        }

    /** 精确标签下的作品（SearchIllustByTagPcV） */
    suspend fun getTagFeed(tag: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            withContext(Dispatchers.Default) {
                NewArrivalParser.parse(api.getTagSearch(tag, page).string())
            }.let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    /** 标签建议（SearchTagByKeywordPcV，返回包含关键字的标签卡片） */
    suspend fun getTagSuggestions(tag: String, page: Int): Result<List<TagCard>> =
        apiCall {
            withContext(Dispatchers.Default) {
                TagCardParser.parse(api.getTagSuggestions(tag, page).string())
            }
        }

    /** 标签自动补全建议（GetTagSuggestionF，输入时实时调用） */
    suspend fun getTagAutoComplete(input: String): Result<List<String>> =
        apiCall {
            val response = api.getTagAutoComplete(input = input)
            if (response.result == 10) response.tags else emptyList()
        }

    suspend fun getKeywordFeed(keyword: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            withContext(Dispatchers.Default) {
                NewArrivalParser.parse(api.getKeywordSearch(keyword, page).string())
            }.let { if (adultEnabled) it else it.filter { !it.warning } }
        }
}

private const val FOLLOW_LIST_MAX = 30

/** BlockListF 的 MD 固定为 1（0 为关注列表） */
private const val BLOCK_LIST_MD = 1
