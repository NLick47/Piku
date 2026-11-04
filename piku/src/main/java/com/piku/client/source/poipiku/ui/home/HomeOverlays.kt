package com.piku.client.source.poipiku.ui.home

import androidx.compose.runtime.Composable
import com.piku.client.core.ui.common.PikuFullScreenDialog
import com.piku.client.core.ui.drawer.AccountsScreen
import com.piku.client.core.ui.drawer.SourceAccountRow

@Composable
internal fun HomeOverlays(
    showAccountsPage: Boolean,
    onAccountsBack: () -> Unit,
    onAccountsLogin: (SourceAccountRow) -> Unit,
    dark: Boolean,
) {
    if (showAccountsPage) {
        PikuFullScreenDialog(onDismissRequest = onAccountsBack) {
            AccountsScreen(onBack = onAccountsBack, onLogin = onAccountsLogin, dark = dark)
        }
    }
}
