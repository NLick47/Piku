package com.piku.client.ui.home.drawer

import com.piku.client.ui.home.sheets.ConfirmDestructiveDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.ui.common.PikuBackButton
import com.piku.client.ui.common.UserAvatar
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.PikuColors

@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    onLogin: (SourceAccountRow) -> Unit,
    dark: Boolean,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var pendingRevoke by remember { mutableStateOf<SourceAccountRow?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            )
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PikuBackButton(
                onClick = onBack,
                dark = dark,
                contentDescription = stringResource(R.string.back),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.account_title),
                    color = PikuColors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.account_hint),
                    color = PikuColors.textFaint,
                    fontSize = 11.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            rows.forEach { row ->
                AccountRowCard(
                    row = row,
                    dark = dark,
                    onLogin = { onLogin(row) },
                    onRevoke = { pendingRevoke = row },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }

    pendingRevoke?.let { row ->
        val dismiss = { pendingRevoke = null }
        val confirm = {
            viewModel.logout(row.source)
            pendingRevoke = null
        }
        val message = row.logoutMessageRes
        if (message != null) {
            ConfirmDestructiveDialog(
                title = stringResource(R.string.logout),
                message = stringResource(message, stringResource(row.labelRes)),
                confirmLabel = stringResource(R.string.logout_confirm),
                onConfirm = confirm,
                onDismiss = dismiss,
                dark = dark,
            )
        }
    }
}

@Composable
private fun AccountRowCard(
    row: SourceAccountRow,
    dark: Boolean,
    onLogin: () -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PikuColors.surface)
            .border(0.5.dp, PikuColors.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像只是展示，不参与点击（动作都在右侧那一个词上）
        UserAvatar(
            avatarUrl = row.account?.avatarUrl,
            onClick = {},
            dark = dark,
            size = 40.dp,
            enabled = false,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(row.labelRes),
                color = PikuColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.subtitle(),
                color = if (row.loggedIn) PikuColors.textSecondary else PikuColors.textFaint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            !row.loggedIn && row.loginRoute != null -> ActionText(
                text = stringResource(R.string.login_button),
                color = PikuColors.accent,
                onClick = onLogin,
            )

            row.loggedIn && row.logoutMessageRes != null -> ActionText(
                text = stringResource(R.string.logout),
                color = PikuColors.error,
                onClick = onRevoke,
            )
        }
    }
}

/** 副标题：站内标识优先，标识缺失时退回昵称；资料未到就只说"已登录" */
@Composable
private fun SourceAccountRow.subtitle(): String = when {
    !loggedIn -> stringResource(R.string.account_unauthorized)
    pending -> stringResource(R.string.account_logged_in)
    else -> account?.account.orEmpty().ifBlank { account?.displayName.orEmpty() }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
