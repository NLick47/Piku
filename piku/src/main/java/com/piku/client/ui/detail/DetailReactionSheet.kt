package com.piku.client.ui.detail

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.piku.client.domain.model.WorkDetail
import com.piku.client.ui.theme.LoginBackgroundDark
import com.piku.client.ui.theme.LoginBackgroundLight
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.delay

private val SEND_EMOJIS = listOf("💖", "❤", "👍", "👏", "💯", "🥰", "😍", "💞", "🫶", "🎉")

/** 收到反应面板最多展示的表情种类数，防止表情过多撑满面板 */
private const val MAX_VISIBLE_REACTION_TYPES = 8

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReactionSheet(
    detail: WorkDetail,
    dark: Boolean,
    loggedIn: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (dark) LoginBackgroundDark else LoginBackgroundLight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_reaction_title),
                    color = PikuColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (detail.reactionCount > 0) {
                    Text(
                        text = stringResource(R.string.detail_reaction_total, detail.reactionCount),
                        color = PikuColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            if (detail.reactionCount > 0) {
                Spacer(Modifier.height(16.dp))
                val entries = detail.reactionCounts.entries.sortedByDescending { it.value }
                val visible = entries.take(MAX_VISIBLE_REACTION_TYPES)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visible.forEach { (emoji, count) ->
                        ReceivedReactionPill(emoji = emoji, count = count, dark = dark)
                    }
                }
                if (entries.size > visible.size) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.detail_reaction_more, entries.size - visible.size),
                        color = PikuColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.detail_reaction_empty),
                    color = PikuColors.textSecondary,
                    fontSize = 13.sp,
                )
            }
            if (loggedIn) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.detail_reaction_send_title),
                    color = PikuColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                ReactionSendRow(
                    sending = sending,
                    received = detail.reactionCounts,
                    dark = dark,
                    onSend = onSend,
                )
            } else {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = PikuColors.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.detail_reaction_login_hint),
                        color = PikuColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedReactionPill(emoji: String, count: Int?, dark: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(PikuColors.surfaceSoft)
            .border(
                BorderStroke(0.5.dp, PikuColors.border),
                shape,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = emoji, fontSize = 14.sp)
        if (count != null) {
            Text(
                text = count.toString(),
                color = PikuColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionSendRow(
    sending: Boolean,
    received: Map<String, Int>,
    dark: Boolean,
    onSend: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.graphicsLayer { alpha = if (sending) 0.45f else 1f },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SEND_EMOJIS.forEach { emoji ->
            ReactionSendButton(
                emoji = emoji,
                enabled = !sending,
                highlighted = received.containsKey(emoji),
                dark = dark,
                onClick = { onSend(emoji) },
            )
        }
    }
}

@Composable
private fun ReactionSendButton(
    emoji: String,
    enabled: Boolean,
    highlighted: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val accent = PikuColors.controlAccent
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else if (popped) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "reactionSendScale",
    )
    LaunchedEffect(popped) {
        if (popped) {
            delay(400)
            popped = false
        }
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = if (dark) 0.20f else 0.12f))
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (highlighted) accent.copy(alpha = 0.55f) else Color.Transparent,
                ),
                CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) {
                popped = true
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 17.sp)
    }
}
