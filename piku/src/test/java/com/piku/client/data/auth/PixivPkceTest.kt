package com.piku.client.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixivPkceTest {

    @Test
    fun challengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PixivPkce.challenge(verifier))
    }

    @Test
    fun generatedVerifierIsUrlSafeAndLongEnough() {
        val verifier = PixivPkce.newVerifier()

        assertTrue("verifier 必须落在 43~128 位", verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertFalse("base64url 不允许填充", verifier.contains('='))
    }

    @Test
    fun codeIsParsedFromPixivSchemeRedirect() {
        assertEquals("AbC-123_x", parsePixivAuthCode("pixiv://account/login?code=AbC-123_x"))
    }

    @Test
    fun codeIsParsedFromHttpsCallbackOnPixivHost() {
        val url = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=xyz789&state=s"
        assertEquals("xyz789", parsePixivAuthCode(url))
    }

    /** 授权页自己那一堆跳转不能被当成"登录完成" */
    @Test
    fun nonCallbackUrlsGiveNoCode() {
        assertNull(parsePixivAuthCode("pixiv://account/login"))
        assertNull(parsePixivAuthCode("https://app-api.pixiv.net/web/v1/login?client=pixiv-android"))
        assertNull(parsePixivAuthCode("https://www.pixiv.net/"))
        assertNull(parsePixivAuthCode("pixiv://account/login?code="))
        assertNull(parsePixivAuthCode("not a url"))
    }

    /**
     * 取到的是编码态的值，这里必须解一次码：客户端随后按表单规则还会再编码一次，
     * 不解码就是双重编码，服务端只会回 invalid_grant（一个看不出原因的错误）。
     */
    @Test
    fun percentEncodedCodeIsDecodedExactlyOnce() {
        assertEquals("a+b/c", parsePixivAuthCode("pixiv://account/login?code=a%2Bb%2Fc"))
    }

    /** 字面 '+' 是授权码的一部分，不能按 form 规则当空格吃掉 */
    @Test
    fun literalPlusSurvives() {
        assertEquals("a+b", parsePixivAuthCode("pixiv://account/login?code=a+b"))
    }
}
