package com.piku.client.source.ao3.ui.login

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piku.client.R
import com.piku.client.core.model.LoginError
import com.piku.client.source.ao3.data.auth.Ao3Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Ao3LoginUiState {
    data object Idle : Ao3LoginUiState
    data object Loading : Ao3LoginUiState
    data class Error(@StringRes val errorRes: Int) : Ao3LoginUiState
}

@HiltViewModel
class Ao3LoginViewModel @Inject constructor(
    private val auth: Ao3Auth,
) : ViewModel() {

    var login by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var passwordVisible by mutableStateOf(false)
        private set

    var loggedIn by mutableStateOf(false)
        private set

    private val _uiState = MutableStateFlow<Ao3LoginUiState>(Ao3LoginUiState.Idle)
    val uiState: StateFlow<Ao3LoginUiState> = _uiState.asStateFlow()

    fun onLoginChange(value: String) {
        login = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
    }

    fun submit() {
        if (login.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            _uiState.value = Ao3LoginUiState.Loading
            auth.login(login.trim(), password)
                .onSuccess {
                    _uiState.value = Ao3LoginUiState.Idle
                    loggedIn = true
                }
                .onFailure { error ->
                    android.util.Log.d("PikuDiag", "ao3 login fail: ${error::class.simpleName}")
                    _uiState.value = Ao3LoginUiState.Error(error.toErrorRes())
                }
        }
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is LoginError.InvalidCredentials -> R.string.ao3_login_error_invalid
        is LoginError.Network -> R.string.login_error_network
        else -> R.string.login_error_unknown
    }
}
