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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
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
import com.piku.client.ui.theme.LoginTextPrimaryDark
import com.piku.client.ui.theme.LoginTextPrimaryLight
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    canGoBack: Boolean = true,
    onSuccess: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel.registered) {
        if (viewModel.registered) onSuccess()
    }

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
        LoginBlobs(dark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RegisterGlassCard(
                uiState = uiState,
                nickname = viewModel.nickname,
                onNicknameChange = viewModel::onNicknameChange,
                email = viewModel.email,
                onEmailChange = viewModel::onEmailChange,
                password = viewModel.password,
                onPasswordChange = viewModel::onPasswordChange,
                passwordVisible = viewModel.passwordVisible,
                onTogglePassword = viewModel::togglePasswordVisibility,
                emailFocusRequester = emailFocusRequester,
                passwordFocusRequester = passwordFocusRequester,
                onFocusEmail = {
                    if (!emailFocusRequester.requestFocus()) {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                },
                onFocusPassword = {
                    if (!passwordFocusRequester.requestFocus()) {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                },
                onRegister = {
                    keyboardController?.hide()
                    viewModel.register()
                },
                onLoginClick = onLoginClick,
                dark = dark,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        alpha = cardAlpha.value
                        translationY = cardTranslate.value.dp.toPx()
                    },
            )
        }

        if (canGoBack) {
            GlassBackButton(
                onClick = {
                    android.util.Log.d("PikuDiag", "register glass back button clicked")
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
private fun RegisterGlassCard(
    uiState: RegisterUiState,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    emailFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    onFocusEmail: () -> Unit,
    onFocusPassword: () -> Unit,
    onRegister: () -> Unit,
    onLoginClick: () -> Unit,
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
            text = stringResource(R.string.register_title),
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            placeholder = { Text(stringResource(R.string.register_nickname_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            singleLine = true,
            keyboardOptions = NicknameKeyboardOptions,
            keyboardActions = KeyboardActions(onNext = { onFocusEmail() }),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        Spacer(Modifier.height(14.dp))

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
                .focusRequester(emailFocusRequester)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text(stringResource(R.string.register_password_label)) },
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
            keyboardActions = KeyboardActions(onDone = { onRegister() }),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier
                .focusRequester(passwordFocusRequester)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        if (uiState is RegisterUiState.Error) {
            Spacer(Modifier.height(16.dp))
            LoginErrorBanner(stringResource((uiState as RegisterUiState.Error).errorRes), dark)
        }

        Spacer(Modifier.height(30.dp))

        LoginGlassButton(
            text = stringResource(R.string.register_button),
            enabled = uiState !is RegisterUiState.Loading &&
                nickname.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            loading = uiState is RegisterUiState.Loading,
            onClick = onRegister,
            dark = dark,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.register_has_account),
            color = if (dark) LoginTextPrimaryDark else LoginTextPrimaryLight,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onLoginClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}