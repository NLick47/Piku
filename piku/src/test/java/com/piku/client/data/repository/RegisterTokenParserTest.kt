package com.piku.client.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterTokenParserTest {

    @Test
    fun `parses token from real form html`() {
        val html = """
            <html><body>
            <script>
            var data = {"TK":"5ilo0eZ5vdvHIa1UB09wwBNzpLO1rN93qs8ZtlMtrbiYLSMblnuzPKJcq1NEe9bY"};
            </script>
            </body></html>
        """.trimIndent()
        assertEquals(
            "5ilo0eZ5vdvHIa1UB09wwBNzpLO1rN93qs8ZtlMtrbiYLSMblnuzPKJcq1NEe9bY",
            parseRegisterToken(html),
        )
    }

    @Test
    fun `parses rotated token`() {
        val html = """{"TK":"312wZaiGiODQ6qq6iW5mJSsTK4iQotJD4qH7kwfafXb7KpQCJd1M3XeCtjwrW6xY"}"""
        assertEquals(
            "312wZaiGiODQ6qq6iW5mJSsTK4iQotJD4qH7kwfafXb7KpQCJd1M3XeCtjwrW6xY",
            parseRegisterToken(html),
        )
    }

    @Test
    fun `returns null when token missing`() {
        val html = "<html>no token here</html>"
        assertNull(parseRegisterToken(html))
    }

    @Test
    fun `returns null on empty html`() {
        assertNull(parseRegisterToken(""))
    }

    @Test
    fun `does not match token inside longer key`() {
        val html = """{"NOT_TK":"abcdef"}"""
        assertNull(parseRegisterToken(html))
    }
}