package com.piku.client.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.ErrorRedDark
import com.piku.client.ui.theme.ErrorRedLight
import com.piku.client.ui.theme.FollowDark
import com.piku.client.ui.theme.FollowLight
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.PikuColors

@Composable
fun FollowPillButton(
    followed: Boolean,
    refollow: Boolean,
    sending: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !sending) 0.95f else 1f,
        label = "followPillPress",
    )

    val labelRes = when {
        followed -> R.string.follow_user_unfollow
        refollow -> R.string.follow_user_refollow
        else -> R.string.follow_user_follow
    }
    val targetBg = when {
        followed -> if (dark) ErrorRedDark.copy(alpha = 0.08f) else ErrorRedLight.copy(alpha = 0.06f)
        refollow -> if (dark) FollowDark.copy(alpha = 0.08f) else FollowLight.copy(alpha = 0.06f)
        else -> if (dark) FollowDark else FollowLight
    }
    val targetBorder = when {
        followed -> if (dark) ErrorRedDark.copy(alpha = 0.20f) else ErrorRedLight.copy(alpha = 0.15f)
        refollow -> if (dark) FollowDark.copy(alpha = 0.20f) else FollowLight.copy(alpha = 0.15f)
        else -> if (dark) FollowDark else FollowLight
    }
    val targetContent = when {
        followed -> PikuColors.error
        refollow -> if (dark) FollowDark else FollowLight
        else -> if (dark) LoginBackgroundDark else Color.White
    }
    val bgColor by animateColorAsState(targetBg, label = "followPillBg")
    val borderColor by animateColorAsState(targetBorder, label = "followPillBorder")
    val contentColor by animateColorAsState(targetContent, label = "followPillContent")

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (sending) 0.7f else 1f
            }
            .defaultMinSize(minWidth = 96.dp)
            .animateContentSize()
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(0.5.dp, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.18f)),
                enabled = !sending,
                onClick = onClick,
            )
            .padding(horizontal = 15.dp, vertical = 7.5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = contentColor,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(6.dp))
        }
        Crossfade(targetState = labelRes, label = "followPillState") { res ->
            Text(
                text = stringResource(res),
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
