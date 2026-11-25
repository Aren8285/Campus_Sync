package com.campussync.app.feature.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Comment
import com.campussync.app.data.model.Post
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.FeedRepository
import com.campussync.app.domain.repository.SafetyRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailContent(
    val post: Post,
    val comments: List<Comment>,
    val currentProfile: UserProfile? = null
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    private val postId: String =
        savedStateHandle.get<String>(Routes.PostIdArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<PostDetailContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<PostDetailContent>> = _uiState.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _replyTarget = MutableStateFlow<Comment?>(null)
    val replyTarget: StateFlow<Comment?> = _replyTarget.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var profile: UserProfile? = null
    private var blockedUids: Set<String> = emptySet()
    private var observeJob: Job? = null

    init {
        load()
    }

    fun load() {
        observeJob?.cancel()

        if (postId.isBlank()) {
            _uiState.value = UiState.Error("This post link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        observeJob = viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                profile = userRepository.getUser(account.uid)
                blockedUids = safetyRepository.getBlockedUids().toSet()

                combine(
                    feedRepository.observePost(postId),
                    feedRepository.observeComments(postId)
                ) { post, comments -> post to comments }
                    .collect { (post, comments) ->
                        if (post == null) {
                            _uiState.value = UiState.Empty
                        } else {
                            _uiState.value = UiState.Success(
                                PostDetailContent(
                                    post = post,
                                    comments = comments.filter {
                                        it.authorId !in blockedUids
                                    },
                                    currentProfile = profile
                                )
                            )
                        }
                    }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load this post."
                )
            }
        }
    }

    fun onCommentTextChange(value: String) {
        if (value.length > MAX_COMMENT_LENGTH) return
        _commentText.value = value
    }

    fun replyTo(comment: Comment) {
        _replyTarget.value = comment
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    fun sendComment() {
        val text = _commentText.value.trim()
        val author = profile
        if (text.isBlank() || author == null || _isSending.value) return

        val parentId = when {
            _replyTarget.value == null -> ""
            _replyTarget.value?.parentId.isNullOrBlank() -> _replyTarget.value?.id.orEmpty()
            else -> _replyTarget.value?.parentId.orEmpty()
        }

        _isSending.value = true

        viewModelScope.launch {
            try {
                feedRepository.addComment(
                    author = author,
                    postId = postId,
                    text = text,
                    parentId = parentId
                )
                _commentText.value = ""
                _replyTarget.value = null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't add your comment."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun toggleLike(post: Post) {
        val uid = profile?.uid ?: return
        val liked = uid in post.likedBy

        viewModelScope.launch {
            try {
                feedRepository.setLiked(post.id, liked = !liked)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't update your like."
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                feedRepository.deletePost(postId)
                _message.value = "Your post was deleted."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't delete this post."
            }
        }
    }

    fun blockUser(targetUid: String, name: String) {
        viewModelScope.launch {
            try {
                safetyRepository.setBlocked(targetUid, blocked = true)
                blockedUids = blockedUids + targetUid

                val current = _uiState.value
                if (current is UiState.Success) {
                    _uiState.value = UiState.Success(
                        current.data.copy(
                            comments = current.data.comments.filter {
                                it.authorId != targetUid
                            }
                        )
                    )
                }

                _message.value = "You blocked ${name.ifBlank { "this student" }}."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't block this student."
            }
        }
    }

    fun report(target: ReportTarget, targetId: String, reportedUserId: String, reason: ReportReason, details: String) {
        viewModelScope.launch {
            try {
                safetyRepository.report(target, targetId, reportedUserId, reason, details)
                _message.value = "Thanks. Your report was sent for review."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't send your report."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val MAX_COMMENT_LENGTH = 500
    }
}
