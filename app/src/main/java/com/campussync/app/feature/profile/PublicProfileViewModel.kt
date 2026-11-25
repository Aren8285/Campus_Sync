package com.campussync.app.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.ChatRepository
import com.campussync.app.domain.repository.SafetyRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileSafetyState(
    val isOwnProfile: Boolean = false,
    val isBlocked: Boolean = false,
    val isFollowing: Boolean = false,
    val isWorking: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val safetyRepository: SafetyRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val uid: String = savedStateHandle.get<String>(Routes.PublicProfileArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<UserProfile>>(UiState.Loading)
    val uiState: StateFlow<UiState<UserProfile>> = _uiState.asStateFlow()

    private val _safetyState = MutableStateFlow(ProfileSafetyState())
    val safetyState: StateFlow<ProfileSafetyState> = _safetyState.asStateFlow()

    private val _openChatId = MutableStateFlow<String?>(null)
    val openChatId: StateFlow<String?> = _openChatId.asStateFlow()

    init {
        load()
        loadSafetyState()
    }

    fun load() {
        if (uid.isBlank()) {
            _uiState.value = UiState.Error("This profile link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val profile = userRepository.getUser(uid)
                _uiState.value = if (profile == null) {
                    UiState.Empty
                } else {
                    UiState.Success(profile)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message
                        ?: "This profile is private or belongs to another college."
                )
            }
        }
    }

    fun toggleFollow() {
        val state = _safetyState.value
        if (state.isOwnProfile || state.isWorking || uid.isBlank()) return

        val follow = !state.isFollowing
        _safetyState.update { it.copy(isWorking = true, message = null) }

        viewModelScope.launch {
            try {
                userRepository.setFollowing(uid, follow)
                _safetyState.update {
                    it.copy(
                        isFollowing = follow,
                        isWorking = false,
                        message = if (follow) {
                            "You follow this student."
                        } else {
                            "You unfollowed this student."
                        }
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _safetyState.update {
                    it.copy(
                        isWorking = false,
                        message = error.message ?: "We couldn't update this follow."
                    )
                }
            }
        }
    }

    fun messageStudent() {
        val state = _safetyState.value
        if (state.isOwnProfile || state.isWorking || uid.isBlank()) return

        _safetyState.update { it.copy(isWorking = true, message = null) }

        viewModelScope.launch {
            try {
                // The profile is normally already on screen; reuse it instead of a
                // second read of the same document.
                val other = (_uiState.value as? UiState.Success)?.data
                    ?: userRepository.getUser(uid)
                    ?: throw AppException("This student could not be found.")
                val chatId = chatRepository.openDirectChat(other)
                _safetyState.update { it.copy(isWorking = false) }
                _openChatId.value = chatId
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _safetyState.update {
                    it.copy(
                        isWorking = false,
                        message = error.message ?: "We couldn't start this chat."
                    )
                }
            }
        }
    }

    fun clearOpenChat() {
        _openChatId.value = null
    }

    fun toggleBlock() {
        val state = _safetyState.value
        if (state.isOwnProfile || state.isWorking || uid.isBlank()) return

        val block = !state.isBlocked
        _safetyState.update { it.copy(isWorking = true, message = null) }

        viewModelScope.launch {
            try {
                safetyRepository.setBlocked(uid, block)
                _safetyState.update {
                    it.copy(
                        isBlocked = block,
                        isWorking = false,
                        message = if (block) {
                            "You blocked this student."
                        } else {
                            "You unblocked this student."
                        }
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _safetyState.update {
                    it.copy(
                        isWorking = false,
                        message = error.message ?: "We couldn't update the block."
                    )
                }
            }
        }
    }

    fun report(reason: ReportReason, details: String) {
        val state = _safetyState.value
        if (state.isOwnProfile || state.isWorking || uid.isBlank()) return

        _safetyState.update { it.copy(isWorking = true, message = null) }

        viewModelScope.launch {
            try {
                safetyRepository.report(
                    target = ReportTarget.User,
                    targetId = uid,
                    reportedUserId = uid,
                    reason = reason,
                    details = details
                )
                _safetyState.update {
                    it.copy(
                        isWorking = false,
                        message = "Thanks. Your report was sent for review."
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _safetyState.update {
                    it.copy(
                        isWorking = false,
                        message = error.message ?: "We couldn't send your report."
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _safetyState.update { it.copy(message = null) }
    }

    private fun loadSafetyState() {
        val myUid = authRepository.currentUser()?.uid.orEmpty()
        if (myUid.isBlank() || uid.isBlank()) return

        if (myUid == uid) {
            _safetyState.update { it.copy(isOwnProfile = true) }
            return
        }

        viewModelScope.launch {
            try {
                // One targeted block lookup plus one follow lookup, in parallel.
                val (blocked, following) = coroutineScope {
                    val blockCheck = async { safetyRepository.isBlocked(uid) }
                    val followCheck = async { userRepository.isFollowing(uid) }
                    blockCheck.await() to followCheck.await()
                }
                _safetyState.update {
                    it.copy(isBlocked = blocked, isFollowing = following)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _safetyState.update {
                    it.copy(
                        message = error.message
                            ?: "We couldn't check this student's profile settings."
                    )
                }
            }
        }
    }
}
