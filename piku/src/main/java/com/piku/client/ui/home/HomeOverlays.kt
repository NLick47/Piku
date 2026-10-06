package com.piku.client.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.piku.client.ui.home.drawer.AccountsScreen
import com.piku.client.ui.home.drawer.SourceAccountRow

@Composable
internal fun HomeOverlays(
    showAccountsPage: Boolean,
    onAccountsBack: () -> Unit,
    onAccountsLogin: (SourceAccountRow) -> Unit,
    dark: Boolean,
) {
    if (showAccountsPage) {
        Dialog(
            onDismissRequest = onAccountsBack,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
        ) {
            AccountsScreen(onBack = onAccountsBack, onLogin = onAccountsLogin, dark = dark)
        }
    }
}
