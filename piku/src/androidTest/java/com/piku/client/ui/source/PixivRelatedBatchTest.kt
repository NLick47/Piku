package com.piku.client.ui.source

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.piku.client.domain.model.AppLanguage
import com.piku.client.domain.model.Work
import com.piku.client.domain.model.WorkDetail
import com.piku.client.domain.model.WorkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 相关作品按滚动分批：首批只出 20 张，滚到快到底再追加同量。
 * 除了分批本身，这里还锁一条不变量——分批条数必须与 scrollState 同寿：
 * 重建后位置被恢复而条数缩回 20 时，原偏移会被钳到新内容底部，`scroll.value` 会掉一大截
 */
@RunWith(AndroidJUnit4::class)
class PixivRelatedBatchTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun keepsScrollOffsetAndRenderedBatchAcrossRestore() {
        val works = (0 until 180).map { index -> work(index) }
        val restoration = StateRestorationTester(compose)
        var scrollState: ScrollState? = null

        restoration.setContent {
            val state = rememberScrollState()
            scrollState = state
            PixivDetailContent(
                detail = WorkDetail(
                    title = "测试作品",
                    authorName = "画师",
                    authorAvatarUrl = "",
                    categoryCd = -1,
                    categoryName = "",
                    // 无图：图区走空态，测试不碰网络
                    imageUrls = emptyList(),
                    tags = emptyList(),
                    r18 = false,
                ),
                stats = null,
                dark = false,
                language = AppLanguage.SYSTEM,
                scrollState = state,
                related = works,
                relatedLoading = false,
                onImageClick = {},
                onImageLongPress = {},
                onAuthorClick = {},
                onTagClick = {},
            )
        }
        compose.waitForIdle()

        // 首批只渲染 20 张
        compose.onNodeWithText(title(19), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(title(20), useUnmergedTree = true).assertDoesNotExist()

        // 滚到首批末尾：追加第二批
        compose.onNodeWithText(title(19), useUnmergedTree = true).performScrollTo()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(title(39), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // 再滚到 40 张的末尾，让偏移明显超出「只有 20 张」时的可滚范围
        compose.onNodeWithText(title(39), useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()

        val before = requireNotNull(scrollState).value
        assertTrue("前置条件：应当已经滚进内容深处，实测 $before", before > 0)

        // 组合重建：配置变化（显示大小/分屏）或从相关作品点进去再返回
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(
            "重建后分批条数缩回 20，偏移就会被钳到新内容底部",
            before,
            requireNotNull(scrollState).value,
        )
    }

    private fun work(index: Int) = Work(
        id = index.toLong() + 1,
        authorId = 1L,
        authorName = "画师",
        authorAvatarUrl = null,
        categoryCd = -1,
        categoryName = "",
        title = title(index),
        // 空串让 Coil 直接落错误态，不在测试里发网络请求
        thumbnailUrl = "",
        imageCount = 1,
        r18 = false,
        source = WorkSource.PIXIV,
    )

    private fun title(index: Int) = "作品$index"
}
