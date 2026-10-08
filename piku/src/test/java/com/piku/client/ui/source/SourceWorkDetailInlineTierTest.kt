package com.piku.client.ui.source

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.ImageUpstream
import com.piku.client.data.remote.PikuJson
import com.piku.client.data.remote.NetworkRuntime
import com.piku.client.data.remote.pixiv.PixivPagesResponse
import com.piku.client.data.repository.toSourceWorkPage
import com.piku.client.domain.source.SourceWorkPage
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceWorkDetailInlineTierTest {

    private val small = "https://i.pximg.net/c/540x540_70/img-master/img/2026/09/26/00/05/02/150105774_p0_master1200.jpg"
    private val regular = "https://i.pximg.net/img-master/img/2026/09/26/00/05/02/150105774_p0_master1200.jpg"
    private val original = "https://i.pximg.net/img-original/img/2026/09/26/00/05/02/150105774_p0.png"

    private val controller = ImageRouteController(
        SettingsRepository(InMemorySharedPreferences()),
        InMemorySharedPreferences(),
        NetworkRuntime(now = { 1_000_000L }, sleeper = {}),
    )

    private fun page(url: String = small, fullUrl: String = regular, originalUrl: String = original) =
        SourceWorkPage(url = url, fullUrl = fullUrl, originalUrl = originalUrl)

    /** 1MB 一秒传完 ≈ 1MB/s */
    private fun measureFast() =
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 1_000)

    /** 1MB 八秒 ≈ 131KB/s */
    private fun measureSlow() =
        controller.recordSpeed(ImageUpstream.PIXIV, relay = false, bytes = 1_048_576, elapsedMs = 8_000)

    /**
     * 实测没撑起预算：内联图区用打底档（pixiv 是 540 / 45KB）。旧行为是直接拿清晰档
     * （master1200，实测 1.23MB）当内联图，直连要十几秒——正是这轮要修掉的那件事。
     */
    @Test
    fun inlineStaysOnTheUnderlayTierUntilTheMeasurementSaysItFits() {
        assertEquals(
            listOf(small, small),
            inlineImageUrls(listOf(page(), page()), upgradeToFull = false),
        )
    }

    /** 实测够快（境外/好网络）：照旧直接给清晰档，不多绕一步 */
    @Test
    fun inlineUsesTheFullTierWhenTheMeasurementSaysItFits() {
        assertEquals(
            listOf(regular, regular),
            inlineImageUrls(listOf(page(), page()), upgradeToFull = true),
        )
    }

    /** 打底档缺了才退到清晰档：宁可多花流量也不能让图区空着 */
    @Test
    fun inlineFallsBackToTheFullTierWhenTheUnderlayTierIsMissing() {
        assertEquals(
            listOf(regular),
            inlineImageUrls(listOf(page(url = "", fullUrl = regular)), upgradeToFull = false),
        )
    }

    /** 两个档位都缺时不退到原图：原图是保存的事，不该顺手拖进图区 */
    @Test
    fun inlineNeverFallsBackToTheOriginalTier() {
        val bare = page(url = "", fullUrl = "", originalUrl = original)

        listOf(true, false).forEach { upgrade ->
            val urls = inlineImageUrls(listOf(bare), upgradeToFull = upgrade)
            assertFalse("原图不该进内联图区", urls.contains(original))
        }
    }

    /** 没样本就不赌这 1MB：开局/换网络后先给打底档，用户进查看器再取清晰档 */
    @Test
    fun upgradeStaysOffWithoutAMeasurement() {
        assertFalse(worthUpgradingInline(listOf(page()), controller))
    }

    /** 实测够快就升；慢就不升——闸门是实测读数，不是固定策略 */
    @Test
    fun upgradeFollowsTheMeasuredRate() {
        measureSlow()
        assertFalse(worthUpgradingInline(listOf(page()), controller))
    }

    @Test
    fun upgradeGoesUpOnAFastMeasurement() {
        measureFast()
        assertTrue(worthUpgradingInline(listOf(page()), controller))
    }

    /** 判定按图所属上游各自的读数：pixiv 的样本不该替另一个主机拍板 */
    @Test
    fun upgradeIsPerUpstream() {
        measureFast()

        val poipikuPage = page(fullUrl = "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png")

        assertFalse("另一个上游没样本，不该跟着 pixiv 的读数升档", worthUpgradingInline(listOf(poipikuPage), controller))
    }

    /** 认不出的主机与没有完整档 URL 都不升：宁可停在打底档，也不去猜 */
    @Test
    fun upgradeStaysOffForUnknownHostOrMissingFullUrl() {
        measureFast()

        assertFalse(worthUpgradingInline(listOf(page(fullUrl = "")), controller))
        assertFalse(worthUpgradingInline(listOf(page(fullUrl = "https://example.com/a_p0_master1200.jpg")), controller))
        assertFalse(worthUpgradingInline(emptyList(), controller))
    }


    private val largeThumb =
        "https://i.pximg.net/c/600x1200_90/img-master/img/2026/09/26/00/05/02/150105774_p0_master1200.jpg"
    private val tinyThumb =
        "https://i.pximg.net/c/240x480/img-master/img/2026/09/26/00/05/02/150105774_p0_master1200.jpg"

    @Test
    fun sameFileHigherTierThumbnailIsKept() {
        assertEquals(
            listOf(largeThumb, small),
            inlineImageUrls(listOf(page(), page()), upgradeToFull = false, sourceThumbnailUrl = largeThumb),
        )
    }

    /** 排行榜场景：打底 240x480 低于内联 small，照旧换，不能因为防闪白把图钉在低清档 */
    @Test
    fun sameFileLowerTierThumbnailStillSwaps() {
        assertEquals(
            listOf(small, small),
            inlineImageUrls(listOf(page(), page()), upgradeToFull = false, sourceThumbnailUrl = tinyThumb),
        )
    }

    /** 内联选了无档位的清晰档：同文件也升级，不拿 large 去顶 img-master */
    @Test
    fun sameFileNoBoxTierStillSwaps() {
        assertEquals(
            listOf(regular, regular),
            inlineImageUrls(listOf(page(), page()), upgradeToFull = true, sourceThumbnailUrl = largeThumb),
        )
    }

    /** 首图与缩略图不是同一文件：照常替换 */
    @Test
    fun differentFileStillSwaps() {
        val other = "https://i.pximg.net/c/540x540_70/img-master/img/2026/09/26/00/05/02/150105775_p0_master1200.jpg"

        assertEquals(
            listOf(other),
            inlineImageUrls(listOf(page(url = other)), upgradeToFull = false, sourceThumbnailUrl = largeThumb),
        )
    }

    /** 缩略图空串：没有可沿用的来源档，原样选档 */
    @Test
    fun blankThumbnailKeepsInlineUrls() {
        assertEquals(
            listOf(small),
            inlineImageUrls(listOf(page()), upgradeToFull = false, sourceThumbnailUrl = ""),
        )
    }

    @Test
    fun inlineImageUrlsHandlesEmptyPages() {
        assertTrue(inlineImageUrls(emptyList(), upgradeToFull = false, sourceThumbnailUrl = largeThumb).isEmpty())
    }

    // 输入取自真实页表响应（/pixiv/pages-150105774.json），URL 不手搓
    private val realPages: List<SourceWorkPage> by lazy {
        val payload = javaClass.getResourceAsStream("/pixiv/pages-150105774.json")!!
            .readBytes().decodeToString()
        val response = PikuJson.decodeFromString<PixivPagesResponse>(payload)
        response.body.take(2).map { it.urls.toSourceWorkPage(it.width, it.height) }
    }

    /** 搜索页那种方形缩略图：同一张作品的裁切档，归一后能认出来，垫得上 */
    private val squareThumb =
        "https://i.pximg.net/c/360x360_70/img-master/img/2026/09/26/00/05/02/150105774_p0_square1200.jpg"

    /** 别的作品的缩略图：与首图无关，不能拿来垫 */
    private val otherWorkThumb =
        "https://i.pximg.net/c/360x360_70/img-master/img/2026/09/26/00/05/02/150105775_p0_square1200.jpg"

    /** 页 0 垫列表卡那张：图区首图打底用的同一张，缓存必中，主图还在下载也不会黑 */
    @Test
    fun viewerUnderlayKeepsTheListThumbnailOnPageZero() {
        val displayed = realPages.map { it.fullUrl }

        assertEquals(largeThumb, viewerUnderlayUrl(largeThumb, displayed, realPages, index = 0))
    }

    /** 图区停在打底档（未升档）同样垫列表卡那张，判定不看图区当前是哪一档 */
    @Test
    fun viewerUnderlayKeepsTheListThumbnailWhenInlineIsNotUpgraded() {
        val displayed = realPages.map { it.url }

        assertEquals(largeThumb, viewerUnderlayUrl(largeThumb, displayed, realPages, index = 0))
    }

    /**
     * 方裁/自定义裁切缩略图与首图是同一张图的另一档：归一后认得出，照旧垫它——它是列表刚
     * 渲染过、缓存必中的那张；当成"另一个文件"处理会让开图器现下载页表 small，反而先黑一下。
     */
    @Test
    fun viewerUnderlayUsesTheCroppedListThumbnailWhenItIsTheSameArtwork() {
        val displayed = realPages.map { it.fullUrl }

        assertEquals(squareThumb, viewerUnderlayUrl(squareThumb, displayed, realPages, index = 0))
    }

    /** 真正不是同一张图（别的作品）→ 退页表 small，绝不能垫 */
    @Test
    fun viewerUnderlayFallsBackToSmallWhenListThumbnailIsAnotherWork() {
        val displayed = realPages.map { it.fullUrl }

        assertEquals(realPages.first().url, viewerUnderlayUrl(otherWorkThumb, displayed, realPages, index = 0))
    }

    /** 深链进来的空缩略图：同样退 small */
    @Test
    fun viewerUnderlayFallsBackToSmallWhenListThumbnailIsBlank() {
        val displayed = realPages.map { it.fullUrl }

        assertEquals(realPages.first().url, viewerUnderlayUrl("", displayed, realPages, index = 0))
    }

    /** 其余页垫页表 small（几十 KB），不能拿图区同款 regular 大图当打底 */
    @Test
    fun viewerUnderlayUsesThePageSmallTierForOtherPages() {
        val displayed = realPages.map { it.fullUrl }

        assertEquals(realPages[1].url, viewerUnderlayUrl(largeThumb, displayed, realPages, index = 1))
    }
}
