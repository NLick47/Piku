package com.piku.client.ui.search

/** 输入联想层：压暗浮层 + 标签联想面板（标签 + 简中译名） */


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.domain.source.SourceSuggestion
import com.piku.client.ui.theme.MenuPopupBgDark
import com.piku.client.ui.theme.MenuPopupBgLight
import com.piku.client.ui.theme.PikuColors

// ---------------- 输入联想层 ----------------

@Composable
internal fun SuggestionScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onDismiss),
    )
}

/** 标签联想面板：标签 + 简中译名，点按直接检索；底部保留"搜索原文"兜底 */
@Composable
internal fun SuggestionPanel(
    query: String,
    suggestions: List<SourceSuggestion>,
    onSelect: (String) -> Unit,
    onDirectSearch: () -> Unit,
    dark: Boolean,
) {
    val accent = PikuColors.accent
    val nameColor = PikuColors.textPrimary
    val faint = PikuColors.textFaint
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) MenuPopupBgDark else MenuPopupBgLight),
    ) {
        suggestions.take(6).forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onSelect(suggestion.name) })
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (dark) 0.16f else 0.09f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "#", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(11.dp))
                Text(
                    text = suggestionLabel(suggestion.name, query, accent),
                    color = nameColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                suggestion.translatedName?.takeIf { it.isNotBlank() }?.let { translated ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = translated,
                        color = faint,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(if (dark) Color.White.copy(alpha = 0.08f) else Color(0x14000000)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDirectSearch)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = stringResource(R.string.search_suggest_direct, query),
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 命中输入前缀的部分加粗着色，其余原样 */
private fun suggestionLabel(
    text: String,
    query: String,
    accent: Color,
): AnnotatedString {
    if (query.isEmpty() || !text.startsWith(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(text.take(query.length))
        }
        append(text.substring(query.length))
    }
}
