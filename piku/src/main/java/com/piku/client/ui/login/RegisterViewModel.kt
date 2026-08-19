package com.piku.client.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.domain.model.RegisterError
import com.piku.client.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Error(val errorRes: Int) : RegisterUiState
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    var nickname by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var passwordVisible by mutableStateOf(false)
        private set

    var registered by mutableStateOf(false)
        private set

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNicknameChange(value: String) {
        nickname = value
    }

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    fun register() {
        val nick = nickname.trim()
        val mail = email.trim()
        when {
            nick.length !in NICKNAME_LENGTH_RANGE -> {
                _uiState.value = RegisterUiState.Error(R.string.register_error_nickname)
                return
            }
            !isValidEmail(mail) -> {
                _uiState.value = RegisterUiState.Error(R.string.register_error_email)
                return
            }
            password.length !in PASSWORD_LENGTH_RANGE -> {
                _uiState.value = RegisterUiState.Error(R.string.register_error_password)
                return
            }
        }
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            registerUseCase(mail, password, nick)
                .onSuccess {
                    _uiState.value = RegisterUiState.Idle
                    registered = true
                }
                .onFailure { error ->
                    android.util.Log.d(
                        "PikuDiag",
                        "register fail error=${error::class.simpleName}: ${error.message}",
                        error,
                    )
                    _uiState.value = RegisterUiState.Error(error.toErrorRes())
                }
        }
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is RegisterError.EmailInUse -> R.string.register_error_email_used
        is RegisterError.InvalidNickname -> R.string.register_error_nickname
        is RegisterError.InvalidEmail -> R.string.register_error_email
        is RegisterError.Network -> R.string.register_error_network
        else -> R.string.register_error_unknown
    }

    private fun isValidEmail(value: String): Boolean =
        value.contains('@') && value.contains('.') && value.length <= MAX_EMAIL_LEN

    private companion object {
        const val MIN_NICKNAME = 3
        const val MAX_NICKNAME = 16
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 32
        const val MAX_EMAIL_LEN = 254
        val NICKNAME_LENGTH_RANGE = MIN_NICKNAME..MAX_NICKNAME
        val PASSWORD_LENGTH_RANGE = MIN_PASSWORD..MAX_PASSWORD
    }
}