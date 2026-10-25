package com.piku.client.source.ao3.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3AuthHtmlTest {

    private val page = """
        <html>
          <head>
            <meta name="csrf-token" content="csrf-token-from-meta">
          </head>
        </html>
    """.trimIndent()

    @Test
    fun csrfTokenFromLoginPage() {
        assertEquals("csrf-token-from-meta", Ao3AuthHtml.csrfToken(page))
    }

    @Test
    fun csrfTokenMissingOnNonLoginPage() {
        assertNull(Ao3AuthHtml.csrfToken("<html><body>Just a moment...</body></html>"))
    }

    @Test
    fun usernameFromLoginRedirect() {
        assertEquals("uu6kk", Ao3AuthHtml.username("https://archiveofourown.org/users/uu6kk"))
        assertEquals("uu6kk", Ao3AuthHtml.username("/users/uu6kk?view_adult=true"))
    }

    @Test
    fun usernameOnlyFromUserPage() {
        assertNull(Ao3AuthHtml.username("https://archiveofourown.org/auth_error"))
        assertNull(Ao3AuthHtml.username("https://archiveofourown.org/"))
    }

    @Test
    fun loginRedirectIntoUserPageIsSuccess() {
        assertEquals(
            Ao3LoginOutcome.Success("uu6kk"),
            classifyLogin(302, "https://archiveofourown.org/users/uu6kk"),
        )
    }

    @Test
    fun loginRedirectIntoAuthErrorIsRejectedCredentials() {
        assertEquals(
            Ao3LoginOutcome.InvalidCredentials,
            classifyLogin(302, "https://archiveofourown.org/auth_error"),
        )
    }

    /** 525/超时由调用方按瞬时失败重试；这里只断言"没登录成功" */
    @Test
    fun nonRedirectIsNotSuccess() {
        assertEquals(Ao3LoginOutcome.Rejected, classifyLogin(200, null))
        assertEquals(Ao3LoginOutcome.Rejected, classifyLogin(525, null))
    }
}
