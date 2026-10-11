package com.piku.client.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * key 变化时触发 action，初进组合跳过。
 * 裸 LaunchedEffect(key) 初进组合也会跑，会冲掉 rememberSaveable 恢复出的状态——滚动重置类逻辑一律走这里
 */
@Composable
fun <T> EffectOnChange(keyOf: () -> T, action: suspend () -> Unit) {
    val currentKeyOf by rememberUpdatedState(keyOf)
    LaunchedEffect(Unit) {
        snapshotFlow { currentKeyOf() }
            .drop(1)
            .collect { action() }
    }
}
