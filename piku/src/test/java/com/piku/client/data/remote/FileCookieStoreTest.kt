package com.piku.client.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.HttpCookie
import java.net.URI

class FileCookieStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun corruptedFileStartsEmptyInsteadOfCrashingConstructor() {
        val file = tmp.newFile("cookies.txt")
        file.writeText("POIPIKU_LK\u0000.poipiku.com\u0000/=token\\u12zz")

        val store = FileCookieStore(file)

        assertTrue(store.cookies.isEmpty())
        assertFalse("corrupt file must be removed for self-heal", file.exists())
    }

    @Test
    fun cookiesRoundTripAcrossInstances() {
        val file = tmp.newFile("cookies.txt")
        val first = FileCookieStore(file)
        val cookie = HttpCookie("POIPIKU_LK", "token123").apply {
            domain = ".poipiku.com"
            path = "/"
        }
        first.add(URI("https://poipiku.com/"), cookie)

        val reloaded = FileCookieStore(file)
        val got = reloaded.get(URI("https://poipiku.com/IllustListPcV.jsp"))

        assertEquals(1, got.size)
        assertEquals("POIPIKU_LK", got[0].name)
        assertEquals("token123", got[0].value)
    }
}
