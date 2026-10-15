package com.piku.client.source.ao3.data

import com.piku.client.core.source.MAX_SCOPES
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.canAddScope
import com.piku.client.core.source.scopeChipLabels
import com.piku.client.core.storage.prefs.InMemorySharedPreferences
import com.piku.client.source.ao3.data.local.Ao3ScopeStore
import com.piku.client.source.ao3.data.remote.Ao3FacetParams
import com.piku.client.source.ao3.data.remote.Ao3ScopeParams
import com.piku.client.source.ao3.data.source.Ao3ContentSource
import com.piku.client.source.ao3.data.source.Ao3Scopes
import com.piku.client.source.ao3.data.source.ao3ChipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 圈层：参数映射、联想解析、订阅落盘 */
class Ao3ScopeTest {

    // ---- 圈层 → 搜索参数：维度决定字段名，写错等于没收窄（安静地返回全站结果） ----

    @Test
    fun fandomScopeMapsToFandomField() {
        val params = Ao3ScopeParams.of(SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling"))
        assertEquals(mapOf("work_search[fandom_names]" to "Harry Potter - J. K. Rowling"), params)
    }

    @Test
    fun relationshipAndFreeformMapToTheirFields() {
        assertEquals(
            mapOf("work_search[relationship_names]" to "Katsuki Yuuri/Victor Nikiforov"),
            Ao3ScopeParams.of(SourceScope(Ao3Scopes.KIND_RELATIONSHIP, "Katsuki Yuuri/Victor Nikiforov")),
        )
        assertEquals(
            mapOf("work_search[freeform_names]" to "Fluff"),
            Ao3ScopeParams.of(SourceScope(Ao3Scopes.KIND_FREEFORM, "Fluff")),
        )
    }

    @Test
    fun unknownKindAndNullScopeCarryNoFilter() {
        assertTrue(Ao3ScopeParams.of(null).isEmpty())
        assertTrue(Ao3ScopeParams.of(SourceScope("mystery", "x")).isEmpty())
    }

    // ---- 联想用的标签类型：传错会回一堆别的维度的同名标签 ----

    @Test
    fun scopeKindMapsToTagSearchType() {
        assertEquals("Fandom", Ao3ScopeParams.tagTypeOf(Ao3Scopes.KIND_FANDOM))
        assertEquals("Relationship", Ao3ScopeParams.tagTypeOf(Ao3Scopes.KIND_RELATIONSHIP))
        assertEquals("Freeform", Ao3ScopeParams.tagTypeOf(Ao3Scopes.KIND_FREEFORM))
        assertEquals("", Ao3ScopeParams.tagTypeOf("mystery"))
    }

    // ---- 订阅落盘 ----

    @Test
    fun scopesRoundTripAndActiveFallsBackWhenRemoved() {
        val prefs = InMemorySharedPreferences()
        val store = Ao3ScopeStore(prefs)
        val hp = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling")
        val ship = SourceScope(Ao3Scopes.KIND_RELATIONSHIP, "Katsuki Yuuri/Victor Nikiforov")

        assertTrue("初始为空", store.scopes.value.isEmpty())
        assertNull("初始无激活", store.active.value)

        store.setScopes(listOf(hp, ship))
        store.setActive(ship)
        assertEquals(listOf(hp, ship), store.scopes.value)
        assertEquals(ship, store.active.value)

        // 重新构造 = 冷启动：从盘上读回
        val reopened = Ao3ScopeStore(prefs)
        assertEquals(listOf(hp, ship), reopened.scopes.value)
        assertEquals(ship, reopened.active.value)

        // 删掉当前激活项：落在剩下的第一个，不留悬空激活
        reopened.setScopes(listOf(hp))
        assertEquals(hp, reopened.active.value)

        // 全删光：激活清空
        reopened.setScopes(emptyList())
        assertNull(reopened.active.value)
        assertTrue(Ao3ScopeStore(prefs).scopes.value.isEmpty())
    }

    @Test
    fun duplicateScopesAreDeduped() {
        val store = Ao3ScopeStore(InMemorySharedPreferences())
        val hp = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling")
        store.setScopes(listOf(hp, hp))
        assertEquals(listOf(hp), store.scopes.value)
    }

    /** 圈层 id 必须稳定：去重、落盘与 loader 键都靠它（同名不同维度不能撞） */
    @Test
    fun scopeIdIncludesKind() {
        assertEquals("fandom:Fluff", SourceScope(Ao3Scopes.KIND_FANDOM, "Fluff").id)
        assertEquals("freeform:Fluff", SourceScope(Ao3Scopes.KIND_FREEFORM, "Fluff").id)
    }

    // ---- 圈层内筛选：收窄参数必须与圈层参数合并后一起发出 ----

    private fun facetsOf(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @Test
    fun completeFilterMapsToSiteParam() {
        assertEquals(
            mapOf("work_search[complete]" to "T"),
            Ao3FacetParams.of(facetsOf(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_YES)),
        )
        assertEquals(
            mapOf("work_search[complete]" to "F"),
            Ao3FacetParams.of(facetsOf(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_NO)),
        )
    }

    /** 默认项不得发参数：站点的"不过滤"是字段缺省，传空串同样等于收窄条件 */
    @Test
    fun defaultFacetsCarryNoParam() {
        assertTrue(Ao3FacetParams.of(facetsOf(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_ALL)).isEmpty())
        assertTrue(Ao3FacetParams.of(emptyMap()).isEmpty())
    }

    /** 只有完结状态一组：认不出的组 id 不产生任何参数（字数筛选已砍，参数能力不留悬空） */
    @Test
    fun unknownFacetGroupCarriesNoParam() {
        assertTrue(Ao3FacetParams.of(facetsOf("words" to "10k")).isEmpty())
        assertTrue(Ao3FacetParams.of(facetsOf("mystery" to "x")).isEmpty())
    }

    /** 圈层与筛选落进同一组 work_search 参数：合并后不得互相覆盖 */
    @Test
    fun scopeAndFacetsCoexistInOneParamSet() {
        val merged = Ao3ScopeParams.of(SourceScope(Ao3Scopes.KIND_FANDOM, "Fluff")) +
            Ao3FacetParams.of(facetsOf(Ao3ContentSource.FACET_COMPLETE to Ao3ContentSource.COMPLETE_YES))

        assertEquals(
            mapOf(
                "work_search[fandom_names]" to "Fluff",
                "work_search[complete]" to "T",
            ),
            merged,
        )
    }

    // ---- 订阅数软上限：满了之后选择器只读 ----

    @Test
    fun scopeLimitAllowsBelowAndBlocksAtCap() {
        val one = listOf(SourceScope(Ao3Scopes.KIND_FANDOM, "a"))
        assertTrue("未满时应可添加", canAddScope(one))
        val full = List(MAX_SCOPES) { i -> SourceScope(Ao3Scopes.KIND_FANDOM, "s$i") }
        assertTrue("恰好满员时不可再添加", !canAddScope(full))
        assertTrue("超限（如上限下调后的旧数据）同样不可添加", !canAddScope(full + one))
    }

    /** 咬人：上限值本身变了，选择器提示文案里的数字也会跟着变（两者共用同一常量） */
    @Test
    fun scopeLimitIsStableConstant() {
        assertEquals(8, MAX_SCOPES)
    }

    // ---- chips 显示名：同人收短，撞名回退全名 ----

    @Test
    fun fandomChipDropsTheDisambiguationSuffix() {
        assertEquals(
            "Harry Potter",
            ao3ChipLabel(SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling")),
        )
        assertEquals(
            "Haikyuu!!",
            ao3ChipLabel(SourceScope(Ao3Scopes.KIND_FANDOM, "Haikyuu!!")),
        )
    }

    /** 咬人：配对与自由标签的连字符是名字内容，按同人规则切会改义 */
    @Test
    fun nonFandomChipsKeepTheirFullName() {
        val ship = "Katsuki Yuuri/Victor Nikiforov"
        assertEquals(ship, ao3ChipLabel(SourceScope(Ao3Scopes.KIND_RELATIONSHIP, ship)))
        assertEquals("Hurt/Comfort", ao3ChipLabel(SourceScope(Ao3Scopes.KIND_FREEFORM, "Hurt/Comfort")))
    }

    @Test
    fun chipsCollidingAfterShorteningFallBackToFullName() {
        val rowling = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - J. K. Rowling")
        val rpf = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter RPF")
        val other = SourceScope(Ao3Scopes.KIND_FREEFORM, "Fluff")

        assertEquals(
            listOf("Harry Potter", "Harry Potter RPF", "Fluff"),
            scopeChipLabels(listOf(rowling, rpf, other), ::ao3ChipLabel),
        )

        // 两颗都叫 Harry Potter：两颗都回退全名，绝不留下两颗看着一样的 chip
        val ambiguous = SourceScope(Ao3Scopes.KIND_FANDOM, "Harry Potter - Fandom")
        assertEquals(
            listOf("Harry Potter - J. K. Rowling", "Harry Potter - Fandom"),
            scopeChipLabels(listOf(rowling, ambiguous), ::ao3ChipLabel),
        )
    }
}
