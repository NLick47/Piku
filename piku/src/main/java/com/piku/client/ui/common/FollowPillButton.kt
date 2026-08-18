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
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight

/**
 * 玻璃质感胶囊关注按钮：关注列表与作者搜索页共用
 * - 已关注（[unfollowed] = false）：柔和红玻璃，点击取消关注
 * - 已取消（[unfollowed] = true）：中性玻璃，点击重新关注
 * - 操作中（[sending] = true）：转圈 + 禁用
 */
@Composable
fun FollowPillButton(
    unfollowed: Boolean,
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

    val bgColor = when {
        sending -> if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
        unfollowed -> if (dark) Color(0x14FFFFFF) else Color(0x0D2C2C2C)
        else -> if (dark) Color(0x14E08A8A) else Color(0x0FC24B4B)
    }
    val borderColor = when {
        sending -> if (dark) Color(0x26FFFFFF) else Color(0x1A2C2C2C)
        unfollowed -> if (dark) Color(0x33FFFFFF) else Color(0x242C2C2C)
        else -> if (dark) Color(0x33E08A8A) else Color(0x26C24B4B)
    }
    val contentColor = when {
        sending -> if (dark) LoginTextFaintDark else LoginTextFaintLight
        unfollowed -> if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight
        else -> if (dark) Color(0xFFE08A8A) else Color(0xFFC24B4B)
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
                text = stringResource(R.string.follow_user_unfollow),
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Crossfade(targetState = unfollowed, label = "followPillState") { alreadyUnfollowed ->
                Text(
                    text = stringResource(
                        if (alreadyUnfollowed) R.string.follow_user_refollow else R.string.follow_user_unfollow
                    ),
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}