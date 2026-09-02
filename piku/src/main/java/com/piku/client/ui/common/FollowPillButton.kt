package com.piku.client.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.PikuColors

/**
 *   关注列表与作者搜索页共用
 * - 已关注（[followed] = true）：柔和红玻璃，点击取消关注
 * - 未关注：中性玻璃，点击关注；[refollow] = true 时文案为"重新关注"（关注列表内取消过的用户）
 * - 操作中（[sending] = true）：转圈 + 禁用
 */
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
        targetValue = if (pressed) 0.95f else 1f,
        label = "followPillPress",
    )

    val labelRes = when {
        followed -> R.string.follow_user_unfollow
        refollow -> R.string.follow_user_refollow
        else -> R.string.follow_user_follow
    }
    val bgColor = when {
        followed && !sending -> if (dark) Color(0x14E08A8A) else Color(0x0FC24B4B)
        else -> if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
    }
    val borderColor = when {
        followed && !sending -> if (dark) Color(0x33E08A8A) else Color(0x26C24B4B)
        else -> if (dark) Color(0x33FFFFFF) else Color(0x242C2C2C)
    }
    val contentColor = when {
        sending -> PikuColors.textFaint
        followed -> PikuColors.error
        else -> PikuColors.textPrimary
    }

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = contentColor,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(labelRes),
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Crossfade(targetState = labelRes, label = "followPillState") { res ->
                Text(
                    text = stringResource(res),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}