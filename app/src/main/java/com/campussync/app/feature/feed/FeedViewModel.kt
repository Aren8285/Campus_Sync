package com.campussync.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Post
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget
import com.campussync.app.data.model.Story
import com.campussync.app.data.model.UserProfile
import com.campussync.app.data.model.isLikedBy
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.FeedRepository
import com.campussync.app.domain.repository.SafetyRepository
import com.campussync.app.domain.repository.StoryRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** One student's active stories, shown as a ring in the story bar. */
data class StoryGroup(
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val stories: List<Story>,
    val hasUnseen: Boolean
)

data class FeedContent(
    val posts: List<Post> = emptyList(),
    val storyGroups: List<StoryGroup> = emptyList(),
    val currentProfile: UserProfile? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val feedRepository: FeedRepository,
    private val storyRepository: StoryRepository,
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<FeedContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<FeedContent>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var currentProfile: UserProfile? = null
    private var posts: List<Post> = emptyList()
    private var stories: List<Story> = emptyList()
    private var blockedUids: Set<String> = emptySet()
    private var nextCursor: Long? = null
    private var hasMore = false
    private var isLoadingMore = false
    private var isRefreshing = false
    private var storyJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return

        isRefreshing = true
        val existing = _uiState.value
        if (existing is UiState.Success) {
            _uiState.value = UiState.Success(existing.data.copy(isRefreshing = true))
        } else {
            _uiState.value = UiState.Loading
        }

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val profile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before opening the feed.")

                val blocked = safetyRepository.getBlockedUids().toSet()
                val page = feedRepository.getFeedPage(cursor = null)

                currentProfile = profile
                blockedUids = blocked
                posts = page.posts
                nextCursor = page.nextCursor
                hasMore = page.nextCursor != null
                isRefreshing = false

                listenForStories(profile.collegeId)
                publish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                isRefreshing = false
                if (posts.isEmpty()) {
                    _uiState.value = UiState.Error(
                        error.message ?: "We couldn't load the feed. Please try again."
                    )
                } else {
                    _message.value = error.message ?: "We couldn't refresh the feed."
                    publish()
                }
            }
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (isLoadingMore) return

        isLoadingMore = true
        publish()

        viewModelScope.launch {
            try {
                val page = feedRepository.getFeedPage(cursor = cursor)
                posts = posts + page.posts
                nextCursor = page.nextCursor
                hasMore = page.nextCursor != null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't load more posts."
            } finally {
                isLoadingMore = false
                publish()
            }
        }
    }

    fun toggleLike(post: Post) {
        val uid = currentProfile?.uid ?: return
        if (post.isLikedBy(uid)) {
            removeLike(post, uid)
        } else {
            addLike(post, uid)
        }
    }

    private fun addLike(post: Post, uid: String) {
        posts = posts.map { current ->
            if (current.id == post.id && uid !in current.likedBy) {
                current.copy(likedBy = current.likedBy + uid)
            } else {
                current
            }
        }
        publish()

        viewModelScope.launch {
            try {
                feedRepository.setLiked(post.id, liked = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                posts = posts.map { current ->
                    if (current.id == post.id) {
                        current.copy(likedBy = current.likedBy - uid)
                    } else {
                        current
                    }
                }
                publish()
                _message.value = error.message ?: "We couldn't save your like."
            }
        }
    }

    private fun removeLike(post: Post, uid: String) {
        posts = posts.map { current ->
            if (current.id == post.id) {
                current.copy(likedBy = current.likedBy - uid)
            } else {
                current
            }
        }
        publish()

        viewModelScope.launch {
            try {
                feedRepository.setLiked(post.id, liked = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                posts = posts.map { current ->
                    if (current.id == post.id && uid !in current.likedBy) {
                        current.copy(likedBy = current.likedBy + uid)
                    } else {
                        current
                    }
                }
                publish()
                _message.value = error.message ?: "We couldn't remove your like."
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                feedRepository.deletePost(postId)
                posts = posts.filterNot { it.id == postId }
                publish()
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
                publish()
                _message.value = "You blocked ${name.ifBlank { "this student" }}."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't block this student."
            }
        }
    }

    fun reportPost(post: Post, reason: ReportReason, details: String) {
        viewModelScope.launch {
            try {
                safetyRepository.report(
                    target = ReportTarget.Post,
                    targetId = post.id,
                    reportedUserId = post.authorId,
                    reason = reason,
                    details = details
                )
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

    private fun listenForStories(collegeId: String) {
        storyJob?.cancel()
        storyJob = viewModelScope.launch {
            storyRepository.observeActiveStories(collegeId)
                .catch { error ->
                    if (error !is CancellationException) {
                        _message.value = error.message ?: "We couldn't load stories."
                    }
                }
                .collect { activeStories ->
                    stories = activeStories
                    publish()
                }
        }
    }

    private fun publish() {
        val profile = currentProfile ?: return
        val now = System.currentTimeMillis()

        val visiblePosts = posts.filter { it.authorId !in blockedUids }

        val groups = stories
            .filter { it.authorId !in blockedUids && it.isActive(now) }
            .groupBy { it.authorId }
            .map { (authorId, authorStories) ->
                val ordered = authorStories.sortedBy { it.createdAt ?: 0L }
                val author = ordered.first()
                StoryGroup(
                    authorId = authorId,
                    authorName = author.authorName,
                    authorUsername = author.authorUsername,
                    stories = ordered,
                    hasUnseen = ordered.any { profile.uid !in it.seenBy }
                )
            }
            .sortedWith(
                compareByDescending<StoryGroup> { it.authorId == profile.uid }
                    .thenByDescending { it.stories.lastOrNull()?.createdAt ?: 0L }
            )

        _uiState.value = UiState.Success(
            FeedContent(
                posts = visiblePosts,
                storyGroups = groups,
                currentProfile = profile,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                isRefreshing = isRefreshing
            )
        )
    }
}
