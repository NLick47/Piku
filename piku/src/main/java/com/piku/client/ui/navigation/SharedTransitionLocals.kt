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

/**
 * 本页的卡片要不要参与共享元素形变。默认要；列表会被"打开作品"这类动作从外部改动的页面
 * 置 false——浏览记录就是这样：打开作品即重记一条，列表按 visitedAt 重排，卡片在转场期间
 * 会挪位置，形变没有稳定落点。
 */
val LocalWorkMorphEnabled = compositionLocalOf { true }

/** 路由转场时长，同时也是共享元素过渡的动画窗口 */
const val SHARED_TRANSITION_MS = 220

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedWorkBounds(key: String, skipEnterMorph: Boolean = false): Modifier {
    // skipEnterMorph = 本次组合不注册共享元素（进场退化成普通 fade）。由调用方按源声明：
    // 图区不在页首、要等加载的详情壳（poipiku）在进场窗口内置 true，窗口收口后恢复
    // false——恢复后的注册是常驻的，返回缩回卡片不依赖转场中途的任何重组时机。
    // 退出侧内容在转场中不会重组，「返回时再补注册」不可行，必须靠常驻注册。
    if (key.isBlank() || skipEnterMorph || !LocalWorkMorphEnabled.current) return this
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
