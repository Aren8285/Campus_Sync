package com.campussync.app.core.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val startupComplete = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                // Refresh the cached verification flag before choosing a screen.
                // If the network is slow, fall back to the cached state instead of blocking.
                withTimeoutOrNull(5_000) {
                    authRepository.reloadUser()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline or transient: fall back to the cached auth state.
                Log.w(TAG, "Could not refresh the auth user at startup", error)
            } finally {
                startupComplete.value = true
            }
        }
    }

    val state: StateFlow<SessionState> = retryTrigger
        .flatMapLatest {
            startupComplete.flatMapLatest { isComplete ->
                if (!isComplete) {
                    flowOf(SessionState.Loading)
                } else {
                    authRepository.authState.flatMapLatest { user ->
                        when {
                            user == null -> flowOf(SessionState.SignedOut)

                            !user.isEmailVerified -> flowOf(
                                SessionState.Unverified(user.email.orEmpty())
                            )

                            else -> userRepository.observeUser(user.uid)
                                .map { profile ->
                                    if (profile == null) {
                                        SessionState.NeedsOnboarding(user.uid)
                                    } else {
                                        SessionState.Ready(user.uid)
                                    }
                                }
                                .onStart { emit(SessionState.Loading) }
                                .catch { error ->
                                    emit(
                                        SessionState.Error(
                                            error.message
                                                ?: "We couldn't load your profile. Please try again."
                                        )
                                    )
                                }
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading
        )

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val TAG = "SessionViewModel"
    }
}
