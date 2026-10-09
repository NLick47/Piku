package com.piku.client.ui.search

import com.piku.client.domain.model.PopularTag
import org.junit.Assert.assertEquals
import org.junit.Test

/** 热门标签 → 待机态图墙条目：标签一个不筛，缺封面就交空格子（渲染成中性占位） */
class PopularTagTrendingTest {

    @Test
    fun tagWithCoverBecomesImageTile() {
        val tile = PopularTag(
            name = "類司R18",
            thumbnailUrl = "https://cdn.poipiku.com/013533041_UDBPiLAkV.jpeg_360.jpg",
        ).toTrendingTag()

        assertEquals("類司R18", tile.name)
        assertEquals("https://cdn.poipiku.com/013533041_UDBPiLAkV.jpeg_360.jpg", tile.thumbnailUrl)
        // 站点不给宽高：先按方图排，图加载完由实测比例接管（与 pixiv 缺尺寸时同一条路）
        assertEquals(0, tile.width)
        assertEquals(0, tile.height)
    }

    @Test
    fun tagWithoutCoverStaysInTheWall() {
        val tile = PopularTag(name = "没有作品").toTrendingTag()

        assertEquals("没有作品", tile.name)
        assertEquals("", tile.thumbnailUrl)
    }

    @Test
    fun blankCoverStaysInTheWall() {
        val tile = PopularTag(name = "图挂了", thumbnailUrl = " ").toTrendingTag()

        assertEquals("图挂了", tile.name)
        assertEquals(" ", tile.thumbnailUrl)
    }
}
