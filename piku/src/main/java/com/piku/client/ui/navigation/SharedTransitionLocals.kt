package com.piku.client.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProvideNavSharedScope(
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides sharedScope,
        LocalNavAnimatedScope provides animatedScope,
        content = content,
    )
}

fun workSharedKey(authorId: Long, workId: Long) = "work-$authorId-$workId"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedWorkBounds(key: String): Modifier {
    if (key.isBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedScope.current ?: return this
    return with(sharedScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }.then(this)
}
