package com.piku.client.ui.source

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.data.local.SettingsRepository
import com.piku.client.data.remote.ImageRouteController
import com.piku.client.data.remote.ImageUpstream
import com.piku.client.data.remote.NetworkRuntime
import com.piku.client.domain.source.SourceWorkPage
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

        val poipikuPage = page(fullUrl = "https://cdn.poipoiku.com/013955571/013349459_EsuN6ithm.png")

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
}
