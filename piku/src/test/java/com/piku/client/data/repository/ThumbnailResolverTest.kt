package com.piku.client.data.repository

import com.piku.client.data.local.InMemorySharedPreferences
import com.piku.client.domain.model.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailResolverTest {

    private val listThumb = "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg"
    private val appendThumb = "https://cdn.poipiku.com/013955571/013349459_030732415_1NkRpwujF.png_640.jpg"

    private fun work(thumbnailUrl: String) = Work(
        id = 13349459L,
        authorId = 13955571L,
        authorName = "author",
        authorAvatarUrl = null,
        categoryCd = 0,
        categoryName = "",
        title = "title",
        thumbnailUrl = thumbnailUrl,
        imageCount = 2,
        r18 = false,
    )

    @Test
    fun isPlaceholderImageDetectsAllPlaceholderKinds() {
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/publish_login.png_640.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/publish_follower.png_640.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/publish_pass.png_360.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/warning.png_360.jpg"))
        assertTrue(ThumbnailResolver.isPlaceholderImage("https://cdn.poipiku.com/img/R-18.png_640.jpg"))
    }

    @Test
    fun isPlaceholderImageRejectsRealWorkImages() {
        assertFalse(
            ThumbnailResolver.isPlaceholderImage(
                "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
            ),
        )
        assertFalse(
            ThumbnailResolver.isPlaceholderImage(
                "https://cdn.poipiku.com/014194276/013349362_wCpodr7j2.jpeg_640.jpg",
            ),
        )
    }

    @Test
    fun mergeKeepsRealImagesInOrderAndDropsPlaceholders() {
        val detail = listOf(
            "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
            "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
        )
        val append = listOf(
            "https://cdn.poipiku.com/013955571/013349459_030732415_1NkRpwujF.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_pass.png_360.jpg",
        )

        assertEquals(
            listOf(
                "https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg",
                "https://cdn.poipiku.com/013955571/013349459_030732415_1NkRpwujF.png_640.jpg",
            ),
            ThumbnailResolver.mergeWorkImages(detail, append),
        )
    }

    @Test
    fun mergeDeduplicatesAcrossSources() {
        val detail = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")
        val append = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, append))
    }

    @Test
    fun mergeFallsBackToDetailImagesWhenOnlyPlaceholders() {
        val detail = listOf("https://cdn.poipiku.com/img/publish_login.png_640.jpg")
        val append = listOf("https://cdn.poipiku.com/img/publish_pass.png_360.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, append))
    }

    @Test
    fun mergeWithEmptyAppendKeepsRealDetailImages() {
        val detail = listOf("https://cdn.poipiku.com/013955571/013349459_EsuN6ithm.png_640.jpg")

        assertEquals(detail, ThumbnailResolver.mergeWorkImages(detail, emptyList()))
    }

    /**
     * 回归用例：多图作品（append 非空）从列表点进详情页时，列表缩略图必须原样保留。
     *
     * append 首图是作品第 2 张，而列表缩略图是第 1 张（详情页 HTML 的主图）。过去无条件
     * 回填，卡片缩略图被换成第 2 张，URL 变化又让 Coil 重新解码——返回首页先灰白再出图。
     */
    @Test
    fun backfillKeepsRealListThumbnailOnMultiImageWork() {
        assertNull(ThumbnailResolver.backfillThumbnailUrl(listThumb, listOf(appendThumb)))
    }

    /**
     * 点击卡片预热详情页首图：卡片渲染的是列表缩略图的 _360 版本，详情页首图是同一个
     * 文件的 _640——URL 不同，Coil 缓存互不相通，所以要按列表给的原始 URL（_640）预热。
     */
    @Test
    fun detailPrefetchUrlKeepsRealListThumbnail() {
        assertEquals(listThumb, ThumbnailResolver.detailPrefetchUrl(listThumb))
    }

    /**
     * 空图/占位图（登录墙/关注墙/密码/R-18/警告）不预热：详情页要么走门卡，要么由
     * append 出真实图，预热一张用不上的图只是白下载。
     */
    @Test
    fun detailPrefetchUrlSkipsBlankAndPlaceholder() {
        assertNull(ThumbnailResolver.detailPrefetchUrl(""))
        listOf(
            "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_follower.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_pass.png_640.jpg",
            "https://cdn.poipiku.com/img/R-18.png_640.jpg",
            "https://cdn.poipiku.com/img/warning.png_640.jpg",
        ).forEach { placeholder ->
            assertNull("$placeholder 是占位图，不该预热", ThumbnailResolver.detailPrefetchUrl(placeholder))
        }
    }

    /**
     * 详情页首图的低清打底：来源缩略图确实是首图本身时，垫列表卡片渲染的那张 _360
     * （卡片刚解码过、缓存必中；详情页要的 _640 是另一个 URL，缓存不通）。
     */
    @Test
    fun detailUnderlayUsesListThumbnailWhenItIsTheFirstImage() {
        assertEquals(
            listThumb.replace("_640.jpg", "_360.jpg"),
            ThumbnailResolver.detailUnderlayUrl(listThumb, listThumb),
        )
    }

    /**
     * 占位图不垫：关注墙/密码墙作品的卡片显示的就是那张"墙"，关注后/解锁后图区是真实图，
     * 垫在下面只会让人短暂看到一张墙图。
     */
    @Test
    fun detailUnderlaySkipsPlaceholderAndBlankSource() {
        val placeholder = "https://cdn.poipiku.com/img/publish_follower.png_640.jpg"
        assertNull(ThumbnailResolver.detailUnderlayUrl(placeholder, placeholder))
        assertNull(ThumbnailResolver.detailUnderlayUrl("", listThumb))
    }

    /**
     * 来源缩略图与首图不是同一张时不垫：作品被改过、或首图本来来自 append（多图作品的
     * 追加载入）——垫一张别的图比空着更误导。
     */
    @Test
    fun detailUnderlaySkipsWhenFirstImageDiffers() {
        assertNull(ThumbnailResolver.detailUnderlayUrl(listThumb, appendThumb))
        assertNull(ThumbnailResolver.detailUnderlayUrl(listThumb, null))
    }

    /**
     * 历史/收藏进来时来源缩略图可能已经是 _360（回填过的），详情页首图仍是同一个文件的
     * _640：归一化到 _360 后比得出来，照常打底。
     */
    @Test
    fun detailUnderlayMatchesAcrossThumbnailSizes() {
        assertEquals(
            listThumb.replace("_640.jpg", "_360.jpg"),
            ThumbnailResolver.detailUnderlayUrl(
                listThumb.replace("_640.jpg", "_360.jpg"),
                listThumb,
            ),
        )
    }

    /** 消费端（列表回填）同一条判定：只有占位图/空图卡片接受替换 */
    @Test
    fun needsThumbnailBackfillCoversPlaceholderAndBlankOnly() {
        assertTrue(ThumbnailResolver.needsThumbnailBackfill(""))
        assertTrue(
            ThumbnailResolver.needsThumbnailBackfill(
                "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
            ),
        )
        assertTrue(
            ThumbnailResolver.needsThumbnailBackfill(
                "https://cdn.poipiku.com/img/publish_follower.png_640.jpg",
            ),
        )
        assertTrue(
            ThumbnailResolver.needsThumbnailBackfill("https://cdn.poipiku.com/img/warning_640.jpg"),
        )
        assertFalse(ThumbnailResolver.needsThumbnailBackfill(listThumb))
        assertFalse(ThumbnailResolver.needsThumbnailBackfill(appendThumb.replace("_640", "_360")))
    }

    /** 关注墙作品：关注后 append 拿到真实图，占位图卡片照旧被回填 */
    @Test
    fun backfillReplacesFollowerGatePlaceholder() {
        assertEquals(
            appendThumb,
            ThumbnailResolver.backfillThumbnailUrl(
                "https://cdn.poipiku.com/img/publish_follower.png_640.jpg",
                listOf(appendThumb),
            ),
        )
    }

    /** 列表缩略图未知（深链/正文链接进来）时回填作品首图，而不是随手一张追加图 */
    @Test
    fun backfillPrefersFirstRealImageInDisplayOrder() {
        assertEquals(
            listThumb,
            ThumbnailResolver.backfillThumbnailUrl("", listOf(listThumb, appendThumb)),
        )
        assertEquals(
            listThumb,
            ThumbnailResolver.backfillThumbnailUrl(
                "https://cdn.poipiku.com/img/publish_pass.png_640.jpg",
                listOf("https://cdn.poipiku.com/img/warning_640.jpg", listThumb, appendThumb),
            ),
        )
    }

    @Test
    fun backfillReplacesPlaceholderListThumbnail() {
        assertEquals(
            appendThumb,
            ThumbnailResolver.backfillThumbnailUrl(
                "https://cdn.poipiku.com/img/publish_pass.png_640.jpg",
                listOf(appendThumb),
            ),
        )
        assertEquals(
            appendThumb,
            ThumbnailResolver.backfillThumbnailUrl(
                "https://cdn.poipiku.com/img/warning_640.jpg",
                listOf(appendThumb),
            ),
        )
    }

    @Test
    fun backfillReplacesBlankListThumbnail() {
        assertEquals(appendThumb, ThumbnailResolver.backfillThumbnailUrl("", listOf(appendThumb)))
    }

    @Test
    fun backfillSkipsPlaceholderAppendImages() {
        val placeholderTails = listOf(
            "https://cdn.poipiku.com/img/warning.png_640.jpg",
            "https://cdn.poipiku.com/img/publish_login.png_640.jpg",
        )

        assertEquals(
            appendThumb,
            ThumbnailResolver.backfillThumbnailUrl(
                "https://cdn.poipiku.com/img/publish_pass.png_640.jpg",
                placeholderTails + appendThumb,
            ),
        )
    }

    @Test
    fun backfillReturnsNullWhenAppendHasNoRealImage() {
        val placeholderList = "https://cdn.poipiku.com/img/publish_pass.png_640.jpg"

        assertNull(ThumbnailResolver.backfillThumbnailUrl(placeholderList, emptyList()))
        assertNull(
            ThumbnailResolver.backfillThumbnailUrl(
                placeholderList,
                listOf("https://cdn.poipiku.com/img/warning.png_640.jpg"),
            ),
        )
    }

    /** 占位图作品（密码/警告）仍照旧回填真实图，且缓存下来的 _360 图可供历史/收藏记录使用 */
    @Test
    fun rememberThumbStillBackfillsPlaceholderWork() {
        val resolver = ThumbnailResolver(InMemorySharedPreferences())
        val placeholderWork = work("https://cdn.poipiku.com/img/publish_pass.png_640.jpg")

        val url = ThumbnailResolver.backfillThumbnailUrl(placeholderWork.thumbnailUrl, listOf(appendThumb))
        assertNotNull("占位图作品必须回填真实图", url)

        val expected = appendThumb.replace("_640.jpg", "_360.jpg")
        assertEquals(expected, resolver.rememberThumb(placeholderWork, requireNotNull(url)))
        assertEquals(expected, resolver.thumbFor(placeholderWork))
    }
}