package com.piku.client.data.auth

import com.piku.client.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PoipikuAuthTest {

    @Test
    fun profileMapsToAccountProjection() {
        val account = UserProfile(
            uid = "42",
            avatarUrl = "https://cdn.poipiku.com/img/42.jpg",
            profileUrl = "https://poipiku.com/42/",
            name = "昵称",
        ).toSourceAccount()

        assertEquals("昵称", account.displayName)
        assertEquals("42", account.account)
        assertEquals("https://cdn.poipiku.com/img/42.jpg", account.avatarUrl)
    }

    /** 资料只到一半（缓存里只有 uid 或只有名字）也不能崩，缺的留空 */
    @Test
    fun partialProfileLeavesMissingFieldsEmpty() {
        val account = UserProfile(uid = null, avatarUrl = null, profileUrl = null, name = null)
            .toSourceAccount()

        assertEquals("", account.displayName)
        assertEquals("", account.account)
        assertEquals(null, account.avatarUrl)
    }
}
