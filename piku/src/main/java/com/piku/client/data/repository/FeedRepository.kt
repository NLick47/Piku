package com.piku.client.data.repository

import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.FollowFeedParser
import com.piku.client.data.remote.FollowUserParser
import com.piku.client.data.remote.NewArrivalParser
import com.piku.client.data.remote.PoipikuApi
import com.piku.client.data.remote.PopularTagParser
import com.piku.client.data.remote.SessionMonitor
import com.piku.client.data.remote.UserPageParser
import com.piku.client.data.remote.apiCall
import com.piku.client.domain.model.FollowUser
import com.piku.client.domain.model.FollowUserPage
import com.piku.client.domain.model.PopularTag
import com.piku.client.domain.model.UserWorksPage
import com.piku.client.domain.model.Work
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val api: PoipikuApi,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val sessionMonitor: SessionMonitor,
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
            // 登录态下拿到登录页说明会话已失效：通知自动重登（成功后会触发页面刷新）
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
            // 页头信息（页头图/头像/作品数/背景规则）只从第一页解析，分页复用第一页结果
            val pageInfo = if (page == 0) UserPageParser.parse(html) else null
            UserWorksPage(works = works, pageInfo = pageInfo)
        }

    suspend fun getPopularTags(): Result<List<PopularTag>> =
        apiCall {
            PopularTagParser.parse(api.getPopularTags().string())
        }

    suspend fun getTagFeed(tag: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getTagSearch(tag, page).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }

    suspend fun getKeywordFeed(keyword: String, page: Int): Result<List<Work>> =
        apiCall {
            val adultEnabled = settingsRepository.showAdultContent.first()
            NewArrivalParser.parse(api.getKeywordSearch(keyword, page).string())
                .let { if (adultEnabled) it else it.filter { !it.warning } }
        }
}

private const val FOLLOW_LIST_MAX = 30
