package com.piku.client.source.pixiv.ui.detail

import com.piku.client.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class PixivDetailContentTest {

    @Test
    fun compactCountKeepsSmallNumbersAsIs() {
        assertEquals("0", compactCount(0, AppLanguage.ZH))
        assertEquals("691", compactCount(691, AppLanguage.EN))
        assertEquals("9999", compactCount(9999, AppLanguage.JA))
    }

    /** 中日按万，英文按 K/M；小数位固定 US locale，不受系统语言影响 */
    @Test
    fun compactCountFollowsLanguage() {
        assertEquals("2.2万", compactCount(22079, AppLanguage.ZH))
        assertEquals("21.8万", compactCount(217599, AppLanguage.JA))
        assertEquals("1.2亿", compactCount(120_000_000, AppLanguage.ZH))
        assertEquals("217.6K", compactCount(217599, AppLanguage.EN))
        assertEquals("1.2M", compactCount(1_200_000, AppLanguage.EN))
    }
}
