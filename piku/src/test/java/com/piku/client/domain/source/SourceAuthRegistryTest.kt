package com.piku.client.domain.source

import com.piku.client.domain.model.AuthStatus
import com.piku.client.domain.model.WorkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAuthRegistryTest {

    private class FakeAuth(
        override val source: WorkSource,
        loggedIn: Boolean,
        override val loginRoute: String? = null,
    ) : SourceAuth {

        override val logoutMessageRes = 0
        private val _status = MutableStateFlow(
            if (loggedIn) AuthStatus.LOGGED_IN else AuthStatus.LOGGED_OUT,
        )
        override val status: StateFlow<AuthStatus> = _status.asStateFlow()

        var logoutCalls = 0
            private set

        override fun logout() {
            logoutCalls++
            _status.value = AuthStatus.LOGGED_OUT
        }
    }

    private val poipiku = FakeAuth(WorkSource.POIPIKU, loggedIn = true)
    private val pixiv = FakeAuth(WorkSource.PIXIV, loggedIn = false, loginRoute = "pixiv_login")
    private val registry = SourceAuthRegistry(setOf(poipiku, pixiv))

    @Test
    fun eachSourceAnswersForItselfOnly() {
        assertTrue(registry.isLoggedIn(WorkSource.POIPIKU))
        assertFalse(registry.isLoggedIn(WorkSource.PIXIV))
    }

    /** 没注册的源不能因为"查不到"被当成放行 */
    @Test
    fun unregisteredSourceIsNeverLoggedIn() {
        assertFalse(SourceAuthRegistry(emptySet()).isLoggedIn(WorkSource.PIXIV))
        assertNull(SourceAuthRegistry(emptySet()).byId(WorkSource.PIXIV))
    }

    @Test
    fun logoutHitsOnlyTheNamedSource() {
        registry.byId(WorkSource.PIXIV)?.logout()

        assertEquals(1, pixiv.logoutCalls)
        assertEquals(0, poipiku.logoutCalls)
        assertTrue(registry.isLoggedIn(WorkSource.POIPIKU))
    }
}
