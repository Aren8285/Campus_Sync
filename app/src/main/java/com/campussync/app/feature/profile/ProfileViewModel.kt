package com.campussync.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LogoutUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val _logoutState = MutableStateFlow(LogoutUiState())
    val logoutState: StateFlow<LogoutUiState> = _logoutState.asStateFlow()

    val uiState: StateFlow<UiState<UserProfile>> = retryTrigger
        .flatMapLatest {
            val uid = authRepository.currentUser()?.uid
            if (uid == null) {
                flowOf(UiState.Error("You're signed out. Please log in again."))
            } else {
                userRepository.observeUser(uid)
                    .map<UserProfile?, UiState<UserProfile>> { profile ->
                        if (profile == null) {
                            UiState.Empty
                        } else {
                            UiState.Success(profile)
                        }
                    }
                    .onStart { emit(UiState.Loading) }
                    .catch { error ->
                        emit(
                            UiState.Error(
                                error.message ?: "We couldn't load your profile."
                            )
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading
        )

    fun retry() {
        retryTrigger.value += 1
    }

    fun logout() {
        if (_logoutState.value.isLoading) return

        _logoutState.update { LogoutUiState(isLoading = true) }

        try {
            authRepository.logout()
            _logoutState.update { LogoutUiState() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _logoutState.update {
                LogoutUiState(
                    error = error.message ?: "We couldn't log you out. Please try again."
                )
            }
        }
    }
}
