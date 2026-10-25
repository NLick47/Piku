package com.piku.client.source.ao3.ui.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piku.client.R
import com.piku.client.core.ui.common.PikuBackButton
import com.piku.client.core.ui.theme.HomeBgBottomDark
import com.piku.client.core.ui.theme.HomeBgBottomLight
import com.piku.client.core.ui.theme.HomeBgTopDark
import com.piku.client.core.ui.theme.HomeBgTopLight
import com.piku.client.core.ui.theme.LocalDarkTheme
import com.piku.client.core.ui.theme.PikuColors
import com.piku.client.source.poipiku.ui.login.LoginBlobs
import com.piku.client.source.poipiku.ui.login.LoginErrorBanner
import com.piku.client.source.poipiku.ui.login.LoginGlassButton
import com.piku.client.source.poipiku.ui.login.glassFieldColors
import kotlinx.coroutines.launch

@Composable
fun Ao3LoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val viewModel: Ao3LoginViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = LocalDarkTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(viewModel.loggedIn) {
        if (viewModel.loggedIn) onSuccess()
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
            Ao3LoginCard(
                uiState = uiState,
                login = viewModel.login,
                onLoginChange = viewModel::onLoginChange,
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
                onSubmit = {
                    keyboardController?.hide()
                    viewModel.submit()
                },
                dark = dark,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        alpha = cardAlpha.value
                        translationY = cardTranslate.value.dp.toPx()
                    },
            )
        }

        PikuBackButton(
            onClick = onBack,
            dark = dark,
            glass = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(14.dp),
        )
    }
}

@Composable
private fun Ao3LoginCard(
    uiState: Ao3LoginUiState,
    login: String,
    onLoginChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    passwordFocusRequester: FocusRequester,
    onFocusPassword: () -> Unit,
    onSubmit: () -> Unit,
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
        // AO3 的站标：红底白字的方块
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x26000000), spotColor = Color(0x33000000))
                .background(Color(0xFF990000), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "AO3",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ao3_login_title),
            color = PikuColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ao3_login_subtitle),
            color = PikuColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        OutlinedTextField(
            value = login,
            onValueChange = onLoginChange,
            placeholder = { Text(stringResource(R.string.ao3_login_account_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier
                .focusRequester(passwordFocusRequester)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        )

        if (uiState is Ao3LoginUiState.Error) {
            Spacer(Modifier.height(16.dp))
            LoginErrorBanner(stringResource(uiState.errorRes), dark)
        }

        Spacer(Modifier.height(30.dp))

        LoginGlassButton(
            text = stringResource(R.string.login_button),
            enabled = uiState !is Ao3LoginUiState.Loading &&
                login.isNotBlank() && password.isNotBlank(),
            loading = uiState is Ao3LoginUiState.Loading,
            onClick = onSubmit,
            dark = dark,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
