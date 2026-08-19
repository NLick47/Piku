package com.piku.client.ui.login

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piku.client.R
import com.piku.client.ui.theme.ControlAccentDark
import com.piku.client.ui.theme.ControlAccentLight
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import com.piku.client.ui.theme.LoginTextSecondaryDark
import com.piku.client.ui.theme.LoginTextSecondaryLight
import com.piku.client.ui.theme.PillBorderDark
import com.piku.client.ui.theme.PillBorderLight

internal val LoginErrorRed = Color(0xFFD64545)

internal val EmailKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Email,
    imeAction = ImeAction.Next,
)

internal val PasswordKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    imeAction = ImeAction.Done,
)

internal val NicknameKeyboardOptions = KeyboardOptions(
    imeAction = ImeAction.Next,
)

@Composable
internal fun glassFieldColors(dark: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = if (dark) Color(0x14FFFFFF) else Color(0x8CFFFFFF),
    unfocusedContainerColor = if (dark) Color(0x0FFFFFFF) else Color(0x73FFFFFF),
    focusedBorderColor = if (dark) ControlAccentDark else ControlAccentLight,
    unfocusedBorderColor = if (dark) PillBorderDark else PillBorderLight,
    focusedLeadingIconColor = if (dark) ControlAccentDark else ControlAccentLight,
    unfocusedLeadingIconColor = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
    focusedTrailingIconColor = if (dark) ControlAccentDark else ControlAccentLight,
    unfocusedTrailingIconColor = if (dark) LoginTextSecondaryDark else LoginTextSecondaryLight,
    cursorColor = if (dark) ControlAccentDark else ControlAccentLight,
    focusedTextColor = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
    unfocusedTextColor = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
)

@Composable
internal fun LoginBlobs(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val blobPurple = if (dark) Color(0x409A7FC9) else Color(0x4D9A7FC9)
        val blobWarm = if (dark) Color(0x33C98A2D) else Color(0x4DC98A2D)
        val blobPink = if (dark) Color(0x33D8A8B8) else Color(0x4DD8A8B8)
        fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        blob(blobPurple, size.width - 40.dp.toPx(), 96.dp.toPx(), 120.dp.toPx())
        blob(blobWarm, 0f, 400.dp.toPx(), 100.dp.toPx())
        blob(blobPink, size.width, 620.dp.toPx(), 90.dp.toPx())
    }
}

@Composable
internal fun GlassBackButton(
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color(0x26000000), spotColor = Color(0x33000000))
            .background(if (dark) Color(0xF2262421) else Color(0xF2FFFFFF))
            .clip(CircleShape)
            .border(
                BorderStroke(0.5.dp, if (dark) Color(0x3DFFFFFF) else Color(0x59C8C2B8)),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun LoginGlassButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "glassButtonScale",
    )
    val shape = RoundedCornerShape(18.dp)
    val ink = if (dark) Color(0xFF1C1A18) else Color.White
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000),
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (dark) listOf(Color(0xFFF2F2F2), Color(0xFFC7C7C7))
                    else listOf(Color(0xFF3A3A3A), Color(0xFF141414)),
                ),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .height(54.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ink,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}