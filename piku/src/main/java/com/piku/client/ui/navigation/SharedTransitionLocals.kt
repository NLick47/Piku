package com.piku.client.ui.navigation

import android.view.View
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val LocalSharedTransitionHostView = compositionLocalOf<View?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvideNavSharedScope(
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    val hostView = LocalView.current
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedScope,
        LocalNavAnimatedScope provides animatedScope,
        LocalSharedTransitionHostView provides hostView,
        content = content,
    )
}

fun workSharedKey(authorId: Long, workId: Long) = "work-$authorId-$workId"

/**
 * 给作品卡挂上共享元素转场（列表卡片 ↔ 详情页图区）
 *
 * 三个条件任一不满足就原样返回 Modifier，退化成没有任何共享元素的普通卡片：
 * 1. 不在导航共享作用域内（没套 [ProvideNavSharedScope]）；
 * 2. key 为空；
 * 3. **当前组合不在作用域的宿主窗口里**——即卡片位于 Dialog / Popup 这类独立窗口的浮层中。
 *
 * 第 3 条是安全阀，不是优化：跨窗口无法做形变过渡，若照常注册，一旦同一作品同时出现在
 * 浮层和主窗口列表里（例如首页列表刚好滚到收藏夹/浏览记录里的那一条），Compose 就会跨层级
 * 算坐标并崩溃。判断放在这里而不是让每个调用点自觉，是为了让"忘记加防护"的后果从
 * **崩溃**降级为**没有转场动画**，新页面天然安全。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedWorkBounds(key: String): Modifier {
    if (key.isBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    val hostView = LocalSharedTransitionHostView.current ?: return this
    if (LocalView.current !== hostView) return this
    return with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }.then(this)
}
