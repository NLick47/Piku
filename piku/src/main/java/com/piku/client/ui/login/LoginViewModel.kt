package com.piku.client.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.domain.model.LoginError
import com.piku.client.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val errorRes: Int) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var passwordVisible by mutableStateOf(false)
        private set

    var loggedIn by mutableStateOf(false)
        private set

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            loginUseCase(email.trim(), password)
                .onSuccess {
                    _uiState.value = LoginUiState.Idle
                    loggedIn = true
                }
                .onFailure { error ->
                    _uiState.value = LoginUiState.Error(error.toErrorRes())
                }
        }
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is LoginError.InvalidCredentials -> R.string.login_error_invalid
        is LoginError.Locked -> R.string.login_error_locked
        is LoginError.Network -> R.string.login_error_network
        else -> R.string.login_error_unknown
    }
}
