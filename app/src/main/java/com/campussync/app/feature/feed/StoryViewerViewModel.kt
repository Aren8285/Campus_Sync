package com.campussync.app.feature.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Story
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.StoryRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoryViewerContent(
    val stories: List<Story>,
    val currentUid: String,
    val authorId: String
)

@HiltViewModel
class StoryViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storyRepository: StoryRepository
) : ViewModel() {

    private val authorId: String =
        savedStateHandle.get<String>(Routes.StoryUidArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<StoryViewerContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<StoryViewerContent>> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (authorId.isBlank()) {
            _uiState.value = UiState.Error("This story link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val profile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before viewing stories.")

                val stories = storyRepository.getAuthorStories(
                    authorId = authorId,
                    collegeId = profile.collegeId
                )

                _uiState.value = if (stories.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(
                        StoryViewerContent(
                            stories = stories,
                            currentUid = account.uid,
                            authorId = authorId
                        )
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load this story."
                )
            }
        }
    }

    fun markSeen(storyId: String) {
        viewModelScope.launch {
            try {
                storyRepository.markSeen(storyId)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Seen state is best effort; the viewer keeps working.
            }
        }
    }
}
