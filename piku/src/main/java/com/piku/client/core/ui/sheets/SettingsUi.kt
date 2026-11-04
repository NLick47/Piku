package com.piku.client.core.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.core.translation.AuthMode
import com.piku.client.core.translation.ManualProvider
import com.piku.client.core.translation.Role
import com.piku.client.core.ui.common.GlassCard
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.source.poipiku.ui.home.ProviderTestState

internal val SettingsCardShape = RoundedCornerShape(20.dp)

@Composable
internal fun SettingsCard(
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(dark = dark, shape = SettingsCardShape, modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun MiniPill(text: String, color: Color? = null) {
    Text(
        text = text,
        color = color ?: PikuColors.textSecondary,
        fontSize = 9.5.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PikuColors.surfaceMuted)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
internal fun TestStatusText(test: ProviderTestState?, provider: ManualProvider) {
    val secondary = PikuColors.textSecondary
    when (test) {
        ProviderTestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = PikuColors.controlAccent,
                strokeWidth = 1.4.dp,
            )
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.llm_manual_testing), color = secondary, fontSize = 10.5.sp)
        }
        is ProviderTestState.Success -> Text(
            text = stringResource(R.string.llm_manual_connected_latency, test.latencyMs),
            color = Color(0xFF5BAE61),
            fontSize = 10.5.sp,
        )
        is ProviderTestState.Failed -> Text(
            text = if (test.message.isNullOrBlank()) {
                stringResource(R.string.llm_manual_key_missing)
            } else {
                stringResource(R.string.llm_manual_failed_detail, test.message)
            },
            color = PikuColors.error,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            text = if (provider.authMode != AuthMode.NONE && !provider.hasKey) {
                stringResource(R.string.llm_manual_key_missing)
            } else {
                stringResource(R.string.llm_manual_untested)
            },
            color = secondary,
            fontSize = 10.5.sp,
        )
    }
}

@Composable
internal fun roleLabel(role: String): String = stringResource(
    when (role) {
        Role.TEXT -> R.string.llm_role_text
        Role.NOVEL -> R.string.llm_role_novel
        Role.IMAGE -> R.string.llm_role_image
        else -> R.string.llm_role_text
    },
)

@Composable
internal fun authLabel(provider: ManualProvider): String = stringResource(
    when (provider.authMode) {
        AuthMode.NONE -> R.string.llm_manual_no_auth
        AuthMode.BEARER -> R.string.llm_manual_bearer
        AuthMode.HEADER -> R.string.llm_manual_header
    },
)

@Composable
internal fun SettingsFooter(
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (dark) PikuColors.surface else PikuColors.surfaceSoft)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        color = PikuColors.textSecondary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
internal fun FieldError(text: String?) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        color = PikuColors.error,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(start = 2.dp, top = 5.dp),
    )
}
