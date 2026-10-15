package com.piku.client.core.ui.feature.scopes

import androidx.lifecycle.SavedStateHandle
import com.piku.client.R
import com.piku.client.core.model.WorkSource
import com.piku.client.core.source.MAX_SCOPES
import com.piku.client.core.source.SourceScope
import com.piku.client.core.source.SourceScopeKind
import com.piku.client.core.source.SourceScopeRegistry
import com.piku.client.core.source.SourceScopeSuggestion
import com.piku.client.core.source.SourceScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScopesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeScopes(
        override val sourceId: WorkSource = WorkSource.AO3,
        private val results: List<SourceScopeSuggestion> = emptyList(),
        private val failing: Boolean = false,
        /** 各维度的热门榜；没配的维度给空 */
        private val popularByKind: Map<String, List<SourceScopeSuggestion>> = emptyMap(),
    ) : SourceScopes {
        val popularKinds = mutableListOf<String>()
        override val kinds = listOf(
            SourceScopeKind("fandom", R.string.ao3_scope_fandom),
            SourceScopeKind("relationship", R.string.ao3_scope_relationship),
        )

        private val _scopes = MutableStateFlow<List<SourceScope>>(emptyList())
        private val _active = MutableStateFlow<SourceScope?>(null)
        override val scopes: StateFlow<List<SourceScope>> = _scopes
        override val active: StateFlow<SourceScope?> = _active

        override suspend fun suggest(kind: String, term: String): Result<List<SourceScopeSuggestion>> =
            if (failing) {
                Result.failure(IllegalStateException("boom"))
            } else {
                Result.success(results.filter { it.name.contains(term, ignoreCase = true) })
            }

        override suspend fun popular(kind: String): Result<List<SourceScopeSuggestion>> {
            popularKinds += kind
            return Result.success(popularByKind[kind].orEmpty())
        }

        override fun setScopes(scopes: List<SourceScope>) {
            _scopes.value = scopes.distinctBy { it.id }
            // 与 Ao3ScopeStore 同款回落：激活项被删就落到第一个
            if (_active.value?.id !in _scopes.value.map { it.id }) {
                _active.value = _scopes.value.firstOrNull()
            }
        }

        override fun setActive(scope: SourceScope) {
            _active.value = scope
        }
    }

    private fun build(scopes: FakeScopes): ScopesViewModel = ScopesViewModel(
        SourceScopeRegistry(setOf(scopes)),
        SavedStateHandle(mapOf(ARG_SCOPE_SOURCE to WorkSource.AO3.name)),
    )

    private val hp = SourceScope("fandom", "Harry Potter - J. K. Rowling")

    @Test
    fun subscribedScopesAreNotOfferedAgain() = runTest(dispatcher) {
        val fake = FakeScopes(
            results = listOf(
                SourceScopeSuggestion(hp.name, count = 100),
                SourceScopeSuggestion("Harry Potter (Movies)"),
            ),
        )
        fake.setScopes(listOf(hp))
        val vm = build(fake)

        vm.setQuery("Harry")
        advanceUntilIdle()

        assertEquals(listOf("Harry Potter (Movies)"), vm.uiState.value.candidates.map { it.name })
    }

    @Test
    fun firstSubscribeAlsoBecomesActive() = runTest(dispatcher) {
        val fake = FakeScopes()
        val vm = build(fake)

        vm.add(hp)
        advanceUntilIdle()

        assertEquals(listOf(hp), vm.uiState.value.scopes)
        assertEquals(hp, vm.uiState.value.active)
    }

    @Test
    fun removingTheActiveScopeFallsBackToTheFirstLeft() = runTest(dispatcher) {
        val second = SourceScope("fandom", "Stranger Things (TV 2016)")
        val fake = FakeScopes()
        val vm = build(fake)
        vm.add(hp)
        vm.add(second)
        advanceUntilIdle()
        vm.setActive(second)

        vm.remove(second)
        advanceUntilIdle()

        assertEquals(listOf(hp), vm.uiState.value.scopes)
        assertEquals(hp, vm.uiState.value.active)
    }

    @Test
    fun atTheLimitNothingMoreIsAdded() = runTest(dispatcher) {
        val fake = FakeScopes()
        val vm = build(fake)
        repeat(MAX_SCOPES) { index -> vm.add(SourceScope("fandom", "Fandom $index")) }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canAdd)
        vm.add(SourceScope("fandom", "One Too Many"))
        advanceUntilIdle()

        assertEquals(MAX_SCOPES, vm.uiState.value.scopes.size)
    }

    @Test
    fun failureIsNotReportedAsNothingFound() = runTest(dispatcher) {
        val vm = build(FakeScopes(failing = true))

        vm.setQuery("Harry")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.failed)
        // 失败时不能同时说"没找到"：两者在页面上是两套文案
        assertFalse(vm.uiState.value.searched)
        assertTrue(vm.uiState.value.candidates.isEmpty())
    }

    @Test
    fun clearingTheQueryGoesBackToManageMode() = runTest(dispatcher) {
        val fake = FakeScopes(results = listOf(SourceScopeSuggestion(hp.name)))
        val vm = build(fake)

        vm.setQuery("Harry")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.searchMode)

        vm.clearQuery()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.searchMode)
        assertTrue(vm.uiState.value.results.isEmpty())
        assertFalse(vm.uiState.value.failed)
    }

    @Test
    fun unknownSourceGivesAnEmptyPageInsteadOfCrashing() {
        val vm = ScopesViewModel(
            SourceScopeRegistry(emptySet()),
            SavedStateHandle(mapOf(ARG_SCOPE_SOURCE to WorkSource.AO3.name)),
        )

        assertTrue(vm.uiState.value.kinds.isEmpty())
        assertTrue(vm.uiState.value.scopes.isEmpty())
        assertNull(vm.uiState.value.active)
    }

    /** 热门榜是空查询时的发现入口，进页面就该有；维度是它的入参 */
    @Test
    fun popularListLoadsForTheCurrentKindOnOpen() = runTest(dispatcher) {
        val fake = FakeScopes(
            popularByKind = mapOf(
                "fandom" to listOf(SourceScopeSuggestion(hp.name, count = 100)),
                "relationship" to listOf(SourceScopeSuggestion("Draco Malfoy/Harry Potter", count = 50)),
            ),
        )
        val vm = build(fake)
        advanceUntilIdle()

        assertEquals(listOf(hp.name), vm.uiState.value.popular.map { it.name })
        assertEquals(listOf("fandom"), fake.popularKinds)
    }

    /** 换维度换的是热门榜——chips 点了要看得见效果 */
    @Test
    fun switchingKindReloadsThePopularList() = runTest(dispatcher) {
        val fake = FakeScopes(
            popularByKind = mapOf(
                "fandom" to listOf(SourceScopeSuggestion(hp.name, count = 100)),
                "relationship" to listOf(SourceScopeSuggestion("Draco Malfoy/Harry Potter", count = 50)),
            ),
        )
        val vm = build(fake)
        advanceUntilIdle()

        vm.selectKind("relationship")
        advanceUntilIdle()

        assertEquals(listOf("Draco Malfoy/Harry Potter"), vm.uiState.value.popular.map { it.name })
        assertEquals(listOf("fandom", "relationship"), fake.popularKinds)
    }

    /** 订过的从热门榜上消失，退订又回来（与联想同一套实时过滤） */
    @Test
    fun popularHidesSubscribedScopes() = runTest(dispatcher) {
        val fake = FakeScopes(
            popularByKind = mapOf(
                "fandom" to listOf(SourceScopeSuggestion(hp.name, count = 100)),
            ),
        )
        val vm = build(fake)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.popularCandidates.size)

        vm.add(hp)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.popularCandidates.isEmpty())

        vm.remove(hp)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.popularCandidates.size)
    }

    /** 站点不给热门榜的维度：这一块整段不出现，不留一个空标题 */
    @Test
    fun kindWithoutPopularListStaysEmpty() = runTest(dispatcher) {
        val vm = build(FakeScopes())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.popularCandidates.isEmpty())
        assertFalse(vm.uiState.value.popularLoading)
    }
}
