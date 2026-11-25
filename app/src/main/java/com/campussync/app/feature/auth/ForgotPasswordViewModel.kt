package com.campussync.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, message = null, error = null) }
    }

    fun sendResetEmail() {
        val state = _uiState.value
        if (state.isLoading) return

        if (!state.email.trim().contains("@")) {
            _uiState.update { it.copy(error = "Enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, message = null, error = null) }

        viewModelScope.launch {
            try {
                authRepository.resetPassword(state.email.trim())
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "If an account exists for that email, a reset link is on its way."
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message
                            ?: "We couldn't send the reset email. Please try again."
                    )
                }
            }
        }
    }
}
