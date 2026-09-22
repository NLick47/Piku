package com.piku.client.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.model.RestrictionReason
import com.piku.client.ui.theme.PikuColors

/**
 * 受限门卡：需登录 / 需开启 R-18 显示时替代图区内容的引导卡片。
 * 与 PasswordBox 同级渲染在图区 Box 内，完成对应动作后页面自动重载恢复；
 * 详情未解析出（detail == null）时由 DetailScreen 直接渲染在错误位上。
 */
@Composable
internal fun LockGateCard(
    reason: RestrictionReason,
    loading: Boolean = false,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 登录门卡给锁图标；R-18/关注门卡不给图标（用户反馈）
        if (reason == RestrictionReason.LOGIN) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = PikuColors.textSecondary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = stringResource(reason.titleRes),
            color = PikuColors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(reason.bodyRes),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPrimaryAction,
            enabled = !loading,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PikuColors.accent,
                contentColor = PikuColors.surfaceSoft,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PikuColors.surfaceSoft,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(reason.actionRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val RestrictionReason.titleRes: Int
    get() = when (this) {
        RestrictionReason.LOGIN -> R.string.detail_gate_login_title
        RestrictionReason.ADULT -> R.string.detail_gate_adult_title
        // poipiku 内关注限定：App 内一键关注可解
        RestrictionReason.FOLLOW -> R.string.detail_gate_follower_title
        // Twitter 关注限定：解锁在 Twitter 侧，只能去网页端
        RestrictionReason.FOLLOW_TWITTER -> R.string.detail_gate_follow_twitter_title
        RestrictionReason.RETWEET -> R.string.detail_gate_retweet_title
    }

private val RestrictionReason.bodyRes: Int
    get() = when (this) {
        RestrictionReason.LOGIN -> R.string.detail_gate_login_body
        RestrictionReason.ADULT -> R.string.detail_gate_adult_body
        RestrictionReason.FOLLOW -> R.string.detail_gate_follower_body
        RestrictionReason.FOLLOW_TWITTER -> R.string.detail_gate_follow_twitter_body
        RestrictionReason.RETWEET -> R.string.detail_gate_retweet_body
    }

private val RestrictionReason.actionRes: Int
    get() = when (this) {
        RestrictionReason.LOGIN -> R.string.detail_gate_login_action
        RestrictionReason.ADULT -> R.string.detail_gate_adult_action
        RestrictionReason.FOLLOW -> R.string.detail_gate_follower_action
        RestrictionReason.FOLLOW_TWITTER -> R.string.detail_gate_follow_twitter_action
        RestrictionReason.RETWEET -> R.string.detail_gate_retweet_action
    }
