package com.piku.client.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.piku.client.ui.theme.LoginTextFaintDark
import com.piku.client.ui.theme.LoginTextFaintLight

@Composable
fun UserAvatar(
    avatarUrl: String?,
    onClick: () -> Unit,
    dark: Boolean,
    size: Dp = 32.dp,
) {
    val hasAvatar = !avatarUrl.isNullOrBlank() && !avatarUrl.contains("default_user")
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) LoginTextFaintDark else LoginTextFaintLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hasAvatar) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}