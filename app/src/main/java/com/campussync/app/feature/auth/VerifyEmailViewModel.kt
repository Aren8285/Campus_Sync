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

data class VerifyEmailUiState(
    val email: String = "",
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VerifyEmailUiState(email = authRepository.currentUser()?.email.orEmpty())
    )
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun checkVerified() {
        val state = _uiState.value
        if (state.isChecking) return

        _uiState.update {
            it.copy(isChecking = true, message = null, error = null)
        }

        viewModelScope.launch {
            try {
                val user = authRepository.reloadUser()
                when {
                    user == null -> _uiState.update {
                        it.copy(
                            isChecking = false,
                            error = "You're signed out. Please log in again."
                        )
                    }

                    !user.isEmailVerified -> _uiState.update {
                        it.copy(
                            isChecking = false,
                            message = "Not verified yet. Open the link in your email, then try again."
                        )
                    }

                    else -> _uiState.update { it.copy(isChecking = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        error = error.message
                            ?: "We couldn't check your verification status. Try again."
                    )
                }
            }
        }
    }

    fun resendEmail() {
        val state = _uiState.value
        if (state.isResending) return

        _uiState.update {
            it.copy(isResending = true, message = null, error = null)
        }

        viewModelScope.launch {
            try {
                authRepository.sendVerification()
                _uiState.update {
                    it.copy(
                        isResending = false,
                        message = "Verification email sent to ${it.email}."
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isResending = false,
                        error = error.message
                            ?: "We couldn't send the verification email. Try again."
                    )
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
