package com.piku.client.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.ui.theme.HomeBgBottomDark
import com.piku.client.ui.theme.HomeBgBottomLight
import com.piku.client.ui.theme.HomeBgTopDark
import com.piku.client.ui.theme.HomeBgTopLight
import com.piku.client.ui.theme.LocalDarkTheme
import com.piku.client.ui.theme.PikuColors
import kotlinx.coroutines.launch

@Composable
fun EmailLoginScreen(
    onBack: () -> Unit,
    canGoBack: Boolean = true,
    onSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel.loggedIn) {
        if (viewModel.loggedIn) onSuccess()
    }

    // 入场动画：淡入 + 轻微上移
    val cardAlpha = remember { Animatable(0f) }
    val cardTranslate = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        launch { cardAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { cardTranslate.animateTo(0f, tween(420, easing = FastOutSlowInEasing)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(HomeBgTopDark, HomeBgBottomDark)
                    else listOf(HomeBgTopLight, HomeBgBottomLight),
                ),
            ),
    ) {
        // 装饰光斑
        LoginBlobs(dark)

        // 居中玻璃卡片（键盘弹出时自动让位，内容超高时可滚动）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginGlassCard(
                uiState = uiState,
                email = viewModel.email,
                onEmailChange = viewModel::onEmailChange,
                password = viewModel.password,
                onPasswordChange = viewModel::onPasswordChange,
                passwordVisible = viewModel.passwordVisible,
                onTogglePassword = viewModel::togglePasswordVisibility,
                passwordFocusRequester = passwordFocusRequester,
                onFocusPassword = {
                    if (!passwordFocusRequester.requestFocus()) {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                },
                onLogin = {
                    keyboardController?.hide()
                    viewModel.login()
                },
                onRegisterClick = onRegisterClick,
                dark = dark,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        alpha = cardAlpha.value
                        translationY = cardTranslate.value.dp.toPx()
                    },
            )
        }

        // 悬浮玻璃返回按钮：必须放在滚动 Column 之后（上层）——
        // 全屏 verticalScroll 的指针输入层会挡住其下方兄弟节点的点击
        // （历史 bug：登录页返回按钮点了无效）
        if (canGoBack) {
            GlassBackButton(
                onClick = {
                    android.util.Log.d("PikuDiag", "login glass back button clicked")
                    onBack()
                },
                dark = dark,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun LoginGlassCard(
    uiState: LoginUiState,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    passwordFocusRequester: FocusRequester,
    onFocusPassword: () -> Unit,
    onLogin: () -> Unit,
    onRegisterClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val glassBorder = if (dark) Color(0x3DFFFFFF) else Color(0x59C8C2B8)
    val fieldShape = RoundedCornerShape(18.dp)
    val fieldColors = glassFieldColors(dark)

    Column(
        modifier = modifier
            .shadow(24.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .background(if (dark) Color(0xF2262421) else Color(0xF2FFFFFF))
            .clip(shape)
            .border(BorderStroke(0.5.dp, glassBorder), shape)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 品牌标
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(6.dp, CircleShape, ambientColor = Color(0x26000000), spotColor = Color(0x33000000))
                .background(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFFF5F5F5), Color(0xFFC7C7C7))
                        else listOf(Color(0xFF4A4A4A), Color(0xFF141414)),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                color = if (dark) Color(0xFF1C1A18) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.email_login_title),
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = { Text(stringResource(R.string.email_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            singleLine = true,
            keyboardOptions = EmailKeyboardOptions,
            keyboardActions = KeyboardActions(onNext = { onFocusPassword() }),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text(stringResource(R.string.password_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.password_hide else R.string.password_show,
                        ),
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = PasswordKeyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onLogin() }),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier
                .focusRequester(passwordFocusRequester)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        if (uiState is LoginUiState.Error) {
            Spacer(Modifier.height(16.dp))
            LoginErrorBanner(stringResource((uiState as LoginUiState.Error).errorRes), dark)
        }

        Spacer(Modifier.height(30.dp))

        LoginGlassButton(
            text = stringResource(R.string.login_button),
            enabled = uiState !is LoginUiState.Loading &&
                email.isNotBlank() && password.isNotBlank(),
            loading = uiState is LoginUiState.Loading,
            onClick = onLogin,
            dark = dark,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.register_link_login),
            color = PikuColors.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRegisterClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun LoginErrorBanner(message: String, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LoginErrorRed.copy(alpha = if (dark) 0.16f else 0.10f))
            .border(
                BorderStroke(0.5.dp, LoginErrorRed.copy(alpha = 0.35f)),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = LoginErrorRed,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            color = if (dark) Color(0xFFE8A0A0) else LoginErrorRed,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}