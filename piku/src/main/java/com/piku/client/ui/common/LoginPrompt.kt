package com.piku.client.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.AccentDark
import com.piku.client.ui.theme.LoginCardBorderDark
import com.piku.client.ui.theme.LoginCardBorderLight
import com.piku.client.ui.theme.LoginCardDark
import com.piku.client.ui.theme.LoginCardLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight

/** 整屏居中的登录引导：提示文案 + 「点击登录」按钮（未登录态页面共用） */
@Composable
fun LoginPrompt(
    message: String,
    onLogin: () -> Unit,
    dark: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (dark) LoginCardDark else LoginCardLight)
                .border(
                    BorderStroke(0.5.dp, if (dark) LoginCardBorderDark else LoginCardBorderLight),
                    shape,
                )
                .clickable(onClick = onLogin)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.drawer_login_hint),
                color = if (dark) LoginTextPrimaryDark else AccentDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}