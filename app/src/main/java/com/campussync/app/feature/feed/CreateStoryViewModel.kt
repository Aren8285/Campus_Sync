package com.campussync.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.StoryType
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.StoryRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateStoryUiState(
    val type: StoryType = StoryType.Text,
    val text: String = "",
    val imageUrl: String = "",
    val isPosting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false
)

@HiltViewModel
class CreateStoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val storyRepository: StoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateStoryUiState())
    val uiState: StateFlow<CreateStoryUiState> = _uiState.asStateFlow()

    fun onTypeChange(type: StoryType) {
        _uiState.update { it.copy(type = type, error = null) }
    }

    fun onTextChange(value: String) {
        if (value.length > MAX_TEXT_LENGTH) return
        _uiState.update { it.copy(text = value, error = null) }
    }

    fun onImageUrlChange(value: String) {
        if (value.length > MAX_URL_LENGTH) return
        _uiState.update { it.copy(imageUrl = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isPosting) return

        val error = when {
            state.type == StoryType.Text && state.text.isBlank() ->
                "Write something for your story."

            state.type == StoryType.Image && state.imageUrl.isBlank() ->
                "Add an image link for an image story."

            state.type == StoryType.Image &&
                !state.imageUrl.trim().startsWith("https://") ->
                "Image links must start with https://."

            else -> null
        }

        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }

        _uiState.update { it.copy(isPosting = true, error = null) }

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val profile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before adding a story.")

                storyRepository.createStory(
                    author = profile,
                    type = state.type,
                    text = state.text,
                    imageUrl = state.imageUrl
                )

                _uiState.update { it.copy(isPosting = false, posted = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isPosting = false,
                        error = error.message ?: "We couldn't publish your story."
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 200
        const val MAX_URL_LENGTH = 500
    }
}
