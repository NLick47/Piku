package com.piku.client.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 锁死 EffectOnChange 的契约：初进组合不触发、key 变化触发一次、同值不重复触发。
 * 初进组合那条就是 a74bbaa3 回归的病灶——退回裸 LaunchedEffect(key) 写法时它会红
 */
@RunWith(AndroidJUnit4::class)
class EffectOnChangeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun skipsInitialAndFiresOnChange() {
        var key by mutableStateOf("a")
        var actions by mutableStateOf(0)

        compose.setContent {
            EffectOnChange(keyOf = { key }) { actions++ }
            BasicText(text = key)
        }
        compose.waitForIdle()
        assertEquals("初进组合不得触发", 0, actions)

        key = "b"
        compose.waitForIdle()
        assertEquals("key 变化触发一次", 1, actions)

        key = "b"
        compose.waitForIdle()
        assertEquals("相同值去重", 1, actions)

        key = "c"
        compose.waitForIdle()
        assertEquals("再次变化再触发", 2, actions)
    }
}
