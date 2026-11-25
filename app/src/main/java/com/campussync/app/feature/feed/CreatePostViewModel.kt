package com.campussync.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.data.model.AppException
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.FeedRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val text: String = "",
    val imageUrls: List<String> = listOf(""),
    val isPosting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false
)

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun onTextChange(value: String) {
        if (value.length > MAX_TEXT_LENGTH) return
        _uiState.update { it.copy(text = value, error = null) }
    }

    fun onImageUrlChange(index: Int, value: String) {
        if (value.length > MAX_URL_LENGTH) return
        _uiState.update { state ->
            val updated = state.imageUrls.toMutableList()
            if (index !in updated.indices) return@update state
            updated[index] = value
            state.copy(imageUrls = updated, error = null)
        }
    }

    fun addImageUrl() {
        _uiState.update { state ->
            if (state.imageUrls.size >= MAX_IMAGES) {
                state
            } else {
                state.copy(imageUrls = state.imageUrls + "", error = null)
            }
        }
    }

    fun removeImageUrl(index: Int) {
        _uiState.update { state ->
            val updated = state.imageUrls.toMutableList()
            if (index !in updated.indices) return@update state
            updated.removeAt(index)
            state.copy(
                imageUrls = updated.ifEmpty { listOf("") },
                error = null
            )
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isPosting) return

        val text = state.text.trim()
        val images = state.imageUrls.map { it.trim() }.filter { it.isNotBlank() }

        val invalidImage = images.firstOrNull { !it.isHttpsUrl() }
        val error = when {
            text.isBlank() && images.isEmpty() ->
                "Write something or add an image link before posting."

            invalidImage != null ->
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
                    ?: throw AppException("Finish your profile before posting.")

                feedRepository.createPost(
                    author = profile,
                    text = text,
                    imageUrls = images
                )

                _uiState.update { it.copy(isPosting = false, posted = true) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isPosting = false,
                        error = error.message ?: "We couldn't publish your post."
                    )
                }
            }
        }
    }

    private fun String.isHttpsUrl(): Boolean = startsWith("https://")

    private companion object {
        const val MAX_IMAGES = 4
        const val MAX_TEXT_LENGTH = 1000
        const val MAX_URL_LENGTH = 500
    }
}
