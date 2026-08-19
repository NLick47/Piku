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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Volatile

@Singleton
class FeedRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val sessionMonitor: SessionMonitor,
    private val popularTagCacheRepository: PopularTagCacheRepository,
) {

    suspend fun getNewArrivals(page: Int, categoryCd: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getNewArrivals(page, categoryCd).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getPopularIllusts(page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getPopularIllusts(page).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getRandomPickups(): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getRandomPickup().string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getFollowFeed(page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            val html = api.getFollowFeed(page).string()
            if (authRepository.isLoggedIn() && FollowFeedParser.isLoginPage(html)) {
                sessionMonitor.notifySessionCleared()
            }
            FollowFeedParser.parse(html)
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getFollowUsers(page: Int): Result<FollowUserPage> =
        apiCall {
            val html = if (page == 0) {
                api.getFollowSettingPage("FOLLOW").string()
            } else {
                api.getFollowList(FOLLOW_LIST_MAX, 0, page).string()
            }
            if (authRepository.isLoggedIn() && FollowUserParser.isLoginPage(html)) {
                sessionMonitor.notifySessionCleared()
            }
            val users = FollowUserParser.parse(html)
            val total = if (page == 0) FollowUserParser.parseTotal(html) else null
            FollowUserPage(users = users, total = total ?: users.size)
        }

    suspend fun getUserWorks(userId: Long, page: Int): Result<UserWorksPage> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            val html = api.getUserIllusts(userId, "", page).string()
            val works = NewArrivalParser.parse(html)
                .let { if (adultEnabled) it else it.filter { !it.warning } }
            val pageInfo = if (page == 0) UserPageParser.parse(html) else null
            UserWorksPage(works = works, pageInfo = pageInfo)
        }

    /** 进程内热门标签 memo：每次冷启动失效（下次启动重新拉取） */
    @Volatile
    private var popularTagsMemo: List<PopularTag>? = null

    suspend fun getPopularTags(): Result<List<PopularTag>> {
        popularTagsMemo?.let { return Result.success(it) }
        val fetched = apiCall {
            PopularTagParser.parse(api.getPopularTags().string())
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
            val html = api.getUserSearch(keyword, page).string()
            if (authRepository.isLoggedIn() && UserSearchParser.isLoginPage(html)) {
                sessionMonitor.notifySessionCleared()
            }
            UserSearchParser.parse(html)
        }.onFailure { error ->
            if (authRepository.isLoggedIn() && (error as? AppError.Http)?.code == 404) {
                sessionMonitor.notifySessionCleared()
            }
        }

    /** 精确标签下的作品（SearchIllustByTagPcV） */
    suspend fun getTagFeed(tag: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getTagSearch(tag, page).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    /** 标签建议（SearchTagByKeywordPcV，返回包含关键字的标签卡片） */
    suspend fun getTagSuggestions(tag: String, page: Int): Result<List<TagCard>> =
        apiCall {
            TagCardParser.parse(api.getTagSuggestions(tag, page).string())
        }

    suspend fun getKeywordFeed(keyword: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getKeywordSearch(keyword, page).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }
}

private const val FOLLOW_LIST_MAX = 30
