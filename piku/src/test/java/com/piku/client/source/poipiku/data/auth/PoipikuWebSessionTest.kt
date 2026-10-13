package com.piku.client.source.poipiku.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoipikuWebSessionTest {

    @Test
    fun nonEmptySessionCookieIsTreatedAsLoggedIn() {
        assertEquals(TOKEN, PoipikuWebSession.sessionToken("POIPIKU_LK=$TOKEN"))
        assertTrue(PoipikuWebSession.isLoggedIn("POIPIKU_LK=$TOKEN"))
    }

    @Test
    fun blankSessionCookieIsNotLoggedIn() {
        assertNull(PoipikuWebSession.sessionToken("POIPIKU_LK=; LANG=ja"))
        assertFalse(PoipikuWebSession.isLoggedIn("POIPIKU_LK=; LANG=ja"))
    }

    @Test
    fun missingSessionCookieIsNotLoggedIn() {
        assertNull(PoipikuWebSession.sessionToken("JSESSIONID=abc; LANG=ja"))
        assertFalse(PoipikuWebSession.isLoggedIn("JSESSIONID=abc; LANG=ja"))
    }

    @Test
    fun nullAndBlankHeadersAreNotLoggedIn() {
        assertNull(PoipikuWebSession.sessionToken(null))
        assertNull(PoipikuWebSession.sessionToken(""))
        assertNull(PoipikuWebSession.sessionToken("   "))
        assertFalse(PoipikuWebSession.isLoggedIn(null))
    }

    @Test
    fun sessionCookieIsFoundAmongOtherCookies() {
        val header = "JSESSIONID=16189FC796; LANG=ja; POIPIKU_LK=$TOKEN; POIPIKU_CONTENTS_VIEW_MODE=1"

        assertEquals(TOKEN, PoipikuWebSession.sessionToken(header))
    }

    @Test
    fun cookiesWithSessionNameAsSubstringAreIgnored() {
        assertNull(PoipikuWebSession.sessionToken("X_POIPIKU_LK=nope"))
        assertNull(PoipikuWebSession.sessionToken("POIPIKU_LK_OLD=nope"))
    }

    @Test
    fun loginPageUrlIsTheStandaloneLoginPage() {
        assertEquals("https://poipiku.com/LoginFormEmailPcV.jsp", PoipikuWebSession.LOGIN_PAGE_URL)
    }

    private companion object {
        const val TOKEN = "66c3af0de94693f0019be93cffab20b3"
    }
}