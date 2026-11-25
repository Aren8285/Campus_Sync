package com.campussync.app.feature.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ReportDialog
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.TagChip
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Board
import com.campussync.app.data.model.BoardComment
import com.campussync.app.data.model.BoardThread
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget
import com.campussync.app.data.model.RevealedAuthor
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.BoardRepository
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
import kotlinx.coroutines.launch

data class ThreadDetailContent(
    val board: Board,
    val thread: BoardThread,
    val comments: List<BoardComment>,
    val currentProfile: UserProfile,
    val isModerator: Boolean,
    val isAuthorBlocked: Boolean,
    val myAlias: String?
)

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val boardRepository: BoardRepository,
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    private val boardId: String =
        savedStateHandle.get<String>(Routes.BoardIdArg).orEmpty()
    private val threadId: String =
        savedStateHandle.get<String>(Routes.ThreadIdArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<ThreadDetailContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<ThreadDetailContent>> = _uiState.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _replyTarget = MutableStateFlow<BoardComment?>(null)
    val replyTarget: StateFlow<BoardComment?> = _replyTarget.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _postAnonymously = MutableStateFlow(false)
    val postAnonymously: StateFlow<Boolean> = _postAnonymously.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _threadDeleted = MutableStateFlow(false)
    val threadDeleted: StateFlow<Boolean> = _threadDeleted.asStateFlow()

    private val _revealedAuthor = MutableStateFlow<RevealedAuthor?>(null)
    val revealedAuthor: StateFlow<RevealedAuthor?> = _revealedAuthor.asStateFlow()

    private var profile: UserProfile? = null
    private var board: Board? = null
    private var blockedUids: Set<String> = emptySet()
    private var currentThread: BoardThread? = null
    private var currentComments: List<BoardComment> = emptyList()
    private var myAlias: String? = null
    private var observeJob: Job? = null

    init {
        load()
    }

    fun load() {
        observeJob?.cancel()

        if (boardId.isBlank() || threadId.isBlank()) {
            _uiState.value = UiState.Error("This thread link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        observeJob = viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val loadedProfile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before opening boards.")
                val loadedBoard = boardRepository.getBoard(boardId)
                    ?: throw AppException("This board is no longer available.")

                profile = loadedProfile
                board = loadedBoard
                blockedUids = safetyRepository.getBlockedUids().toSet()
                myAlias = boardRepository.getMyAlias(threadId)

                combine(
                    boardRepository.observeThread(boardId, threadId),
                    boardRepository.observeComments(boardId, threadId)
                ) { thread, comments -> thread to comments }
                    .collect { (thread, comments) ->
                        currentThread = thread
                        currentComments = comments
                        publish()
                    }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load this thread."
                )
            }
        }
    }

    fun setVote(upvote: Boolean) {
        viewModelScope.launch {
            try {
                boardRepository.setThreadVote(boardId, threadId, upvote)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't save your vote."
            }
        }
    }

    fun setCommentVote(comment: BoardComment, upvote: Boolean) {
        viewModelScope.launch {
            try {
                boardRepository.setCommentVote(boardId, threadId, comment.id, upvote)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't save your vote."
            }
        }
    }

    fun replyTo(comment: BoardComment) {
        if (comment.depth >= BoardRepository.MAX_DEPTH) return
        _replyTarget.value = comment
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    fun onCommentTextChange(value: String) {
        if (value.length > MAX_COMMENT_LENGTH) return
        _commentText.value = value
    }

    fun setAnonymous(value: Boolean) {
        _postAnonymously.value = value
    }

    fun sendComment() {
        val text = _commentText.value.trim()
        val author = profile ?: return
        if (text.isBlank() || _isSending.value) return

        val anonymous = _postAnonymously.value
        _isSending.value = true

        viewModelScope.launch {
            try {
                boardRepository.addComment(
                    author = author,
                    boardId = boardId,
                    threadId = threadId,
                    body = text,
                    parentId = _replyTarget.value?.id,
                    anonymous = anonymous
                )

                // The first anonymous comment creates the alias document, so
                // refresh it to keep the author's delete control available.
                if (anonymous) {
                    myAlias = boardRepository.getMyAlias(threadId)
                    publish()
                }

                _commentText.value = ""
                _replyTarget.value = null
                _postAnonymously.value = false
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't add your comment."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteThread() {
        viewModelScope.launch {
            try {
                boardRepository.deleteThread(boardId, threadId)
                _threadDeleted.value = true
                _message.value = "The thread was deleted."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't delete this thread."
            }
        }
    }

    fun deleteComment(comment: BoardComment) {
        viewModelScope.launch {
            try {
                boardRepository.deleteComment(boardId, threadId, comment.id)
                _message.value = "The comment was deleted."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't delete this comment."
            }
        }
    }

    fun togglePin() {
        val content = _uiState.value as? UiState.Success ?: return
        if (!content.data.isModerator) return

        viewModelScope.launch {
            try {
                boardRepository.setPinned(
                    boardId = boardId,
                    threadId = threadId,
                    pinned = !content.data.thread.pinned
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't update the pin."
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

    fun report(
        target: ReportTarget,
        targetId: String,
        reportedUserId: String,
        reason: ReportReason,
        details: String
    ) {
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

    fun reportAnonymousThread(reason: ReportReason, details: String) {
        viewModelScope.launch {
            try {
                boardRepository.reportAnonymousThread(boardId, threadId, reason, details)
                _message.value = "Thanks. Your report was sent for review."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't send your report."
            }
        }
    }

    fun reportAnonymousComment(
        comment: BoardComment,
        reason: ReportReason,
        details: String
    ) {
        viewModelScope.launch {
            try {
                boardRepository.reportAnonymousComment(
                    boardId = boardId,
                    threadId = threadId,
                    commentId = comment.id,
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

    fun restoreThread() {
        viewModelScope.launch {
            try {
                boardRepository.restoreThread(boardId, threadId)
                _message.value = "The thread is visible again."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't restore this thread."
            }
        }
    }

    fun restoreComment(comment: BoardComment) {
        viewModelScope.launch {
            try {
                boardRepository.restoreComment(boardId, threadId, comment.id)
                _message.value = "The comment is visible again."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't restore this comment."
            }
        }
    }

    fun revealAuthor(itemId: String, reason: String) {
        viewModelScope.launch {
            try {
                _revealedAuthor.value = boardRepository.revealAuthor(boardId, itemId, reason)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't reveal this author."
            }
        }
    }

    fun clearRevealedAuthor() {
        _revealedAuthor.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun publish() {
        val currentProfile = profile
        val currentBoard = board

        if (currentProfile == null || currentBoard == null) return

        val thread = currentThread
        if (thread == null) {
            _uiState.value = UiState.Empty
            return
        }

        val isModerator = currentBoard.isModerator(currentProfile.uid)

        _uiState.value = UiState.Success(
            ThreadDetailContent(
                board = currentBoard,
                thread = thread,
                comments = visibleComments(currentComments, blockedUids, isModerator),
                currentProfile = currentProfile,
                isModerator = isModerator,
                isAuthorBlocked = !thread.isAnonymous && thread.authorId in blockedUids,
                myAlias = myAlias
            )
        )
    }

    /**
     * Drops blocked authors, hidden comments for normal users, and anything under
     * a hidden parent, then orders survivors depth-first.
     */
    private fun visibleComments(
        comments: List<BoardComment>,
        blocked: Set<String>,
        includeHidden: Boolean
    ): List<BoardComment> {
        val visibleIds = mutableSetOf<String>()

        val kept = comments.filter { comment ->
            val parentVisible = comment.parentId?.let { it in visibleIds } ?: true
            val keep = comment.authorId !in blocked &&
                parentVisible &&
                (!comment.isHidden || includeHidden)
            if (keep) visibleIds += comment.id
            keep
        }

        val childrenByParent = kept.groupBy { it.parentId }
        val ordered = mutableListOf<BoardComment>()

        fun append(parentId: String?) {
            childrenByParent[parentId]
                .orEmpty()
                .sortedBy { it.createdAt ?: 0L }
                .forEach { child ->
                    ordered += child
                    append(child.id)
                }
        }

        append(null)
        return ordered
    }

    private companion object {
        const val MAX_COMMENT_LENGTH = 500
    }
}

@Composable
fun ThreadDetailScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThreadDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commentText by viewModel.commentText.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val anonymous by viewModel.postAnonymously.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val threadDeleted by viewModel.threadDeleted.collectAsStateWithLifecycle()
    val revealedAuthor by viewModel.revealedAuthor.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val content = (state as? UiState.Success)?.data
    var threadReportOpen by remember { mutableStateOf(false) }
    var commentReport by remember { mutableStateOf<BoardComment?>(null) }
    var revealItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(threadDeleted) {
        if (threadDeleted) onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(
                title = "Thread",
                onBack = onBack,
                actions = {
                    if (content != null && (!content.thread.isHidden || content.isModerator)) {
                        if (content.isModerator) {
                            IconButton(onClick = viewModel::togglePin) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = if (content.thread.pinned) {
                                        "Unpin thread"
                                    } else {
                                        "Pin thread"
                                    },
                                    tint = if (content.thread.pinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        val isOwn = if (content.thread.isAnonymous) {
                            content.thread.anonAlias == content.myAlias
                        } else {
                            content.thread.authorId == content.currentProfile.uid
                        }

                        ActionsMenu(
                            canDelete = content.isModerator || isOwn,
                            deleteLabel = "Delete thread",
                            reportLabel = "Report thread",
                            canBlock = !content.thread.isAnonymous,
                            onDelete = viewModel::deleteThread,
                            onReport = { threadReportOpen = true },
                            onBlock = {
                                if (!content.thread.isAnonymous) {
                                    viewModel.blockUser(
                                        targetUid = content.thread.authorId,
                                        name = content.thread.authorName
                                    )
                                }
                            }
                        )
                    }
                }
            )

            when (val detailState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading thread..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = detailState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "This thread is no longer available."
                )

                is UiState.Success -> {
                    val detail = detailState.data

                    if (detail.thread.isHidden && !detail.isModerator) {
                        EmptyView(
                            modifier = Modifier.weight(1f),
                            message = "This item is hidden pending review."
                        )
                    } else if (detail.isAuthorBlocked) {
                        EmptyView(
                            modifier = Modifier.weight(1f),
                            message = "This thread is hidden because you blocked its author."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item(key = "thread") {
                                ThreadBody(
                                    thread = detail.thread,
                                    currentUid = detail.currentProfile.uid,
                                    isModerator = detail.isModerator,
                                    onAuthorClick = { onOpenProfile(detail.thread.authorId) },
                                    onReveal = { revealItemId = detail.thread.id },
                                    onRestore = viewModel::restoreThread,
                                    onUpvote = { viewModel.setVote(true) },
                                    onDownvote = { viewModel.setVote(false) }
                                )
                            }

                            item(key = "comments-header") {
                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                Text(
                                    text = "Comments (${detail.comments.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            if (detail.comments.isEmpty()) {
                                item(key = "no-comments") {
                                    Text(
                                        text = "No comments yet. Start the discussion.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            items(detail.comments, key = { "comment-${it.id}" }) { comment ->
                                val isOwn = if (comment.isAnonymous) {
                                    comment.anonAlias == detail.myAlias
                                } else {
                                    comment.authorId == detail.currentProfile.uid
                                }

                                CommentRow(
                                    comment = comment,
                                    currentUid = detail.currentProfile.uid,
                                    opAlias = if (detail.thread.isAnonymous) {
                                        detail.thread.anonAlias
                                    } else {
                                        null
                                    },
                                    isModerator = detail.isModerator,
                                    canDelete = isOwn || detail.isModerator,
                                    canReply = comment.depth < BoardRepository.MAX_DEPTH,
                                    onUpvote = { viewModel.setCommentVote(comment, true) },
                                    onDownvote = { viewModel.setCommentVote(comment, false) },
                                    onReply = { viewModel.replyTo(comment) },
                                    onAuthorClick = { onOpenProfile(comment.authorId) },
                                    onReveal = { revealItemId = comment.id },
                                    onRestore = { viewModel.restoreComment(comment) },
                                    onDelete = { viewModel.deleteComment(comment) },
                                    onReport = { commentReport = comment },
                                    onBlock = {
                                        if (!comment.isAnonymous) {
                                            viewModel.blockUser(comment.authorId, comment.authorName)
                                        }
                                    }
                                )
                            }

                            item(key = "bottom-space") {
                                Box(modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }

                        Surface(tonalElevation = 2.dp) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AnonymityToggle(
                                    anonymous = anonymous,
                                    onToggle = viewModel::setAnonymous,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    showNote = anonymous
                                )

                                val target = replyTarget
                                if (target != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Replying to ${target.displayName.ifBlank { "this student" }}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = viewModel::cancelReply) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Cancel reply"
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = viewModel::onCommentTextChange,
                                        placeholder = { Text(text = "Add a comment...") },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 4
                                    )
                                    IconButton(
                                        onClick = viewModel::sendComment,
                                        enabled = commentText.isNotBlank() && !isSending,
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send comment"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (threadReportOpen && content != null) {
        ReportDialog(
            title = "Report thread",
            onDismiss = { threadReportOpen = false },
            onSubmit = { reason, details ->
                if (content.thread.isAnonymous) {
                    viewModel.reportAnonymousThread(reason, details)
                } else {
                    viewModel.report(
                        target = ReportTarget.Thread,
                        targetId = content.thread.id,
                        reportedUserId = content.thread.authorId,
                        reason = reason,
                        details = details
                    )
                }
                threadReportOpen = false
            }
        )
    }

    val commentTarget = commentReport
    if (commentTarget != null) {
        ReportDialog(
            title = "Report comment",
            onDismiss = { commentReport = null },
            onSubmit = { reason, details ->
                if (commentTarget.isAnonymous) {
                    viewModel.reportAnonymousComment(commentTarget, reason, details)
                } else {
                    viewModel.report(
                        target = ReportTarget.BoardComment,
                        targetId = commentTarget.id,
                        reportedUserId = commentTarget.authorId,
                        reason = reason,
                        details = details
                    )
                }
                commentReport = null
            }
        )
    }

    val itemToReveal = revealItemId
    if (itemToReveal != null) {
        RevealReasonDialog(
            onDismiss = { revealItemId = null },
            onSubmit = { reason ->
                viewModel.revealAuthor(itemToReveal, reason)
                revealItemId = null
            }
        )
    }

    val revealed = revealedAuthor
    if (revealed != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearRevealedAuthor,
            title = { Text(text = "Anonymous author") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = revealed.name.ifBlank { "Student" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (revealed.username.isNotBlank()) {
                        Text(
                            text = "@${revealed.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearRevealedAuthor) {
                    Text(text = "Close")
                }
            }
        )
    }
}

@Composable
private fun RevealReasonDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Reveal anonymous author") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A reason is required and will be written to the moderation audit log.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(text = "Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(reason.trim()) },
                enabled = reason.isNotBlank()
            ) {
                Text(text = "Reveal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
private fun ThreadBody(
    thread: BoardThread,
    currentUid: String,
    isModerator: Boolean,
    onAuthorClick: () -> Unit,
    onReveal: () -> Unit,
    onRestore: () -> Unit,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authorModifier = if (thread.isAnonymous) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onAuthorClick)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (thread.pinned) {
                TagChip(text = "Pinned")
            }

            if (thread.isHidden && isModerator) {
                TagChip(text = "Hidden pending review")
                TextButton(onClick = onRestore) {
                    Text(text = "Restore")
                }
            }

            Text(
                text = thread.title.ifBlank { "Untitled thread" },
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                modifier = authorModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InitialsAvatar(name = thread.displayName, size = 36.dp)

                Column(modifier = Modifier.weight(1f)) {
                    if (thread.isAnonymous) {
                        GhostAuthorText(
                            alias = thread.anonAlias,
                            isOp = true,
                            style = MaterialTheme.typography.titleSmall
                        )
                    } else {
                        Text(
                            text = thread.authorName.ifBlank { "Student" },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = formatRelativeTime(thread.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thread.isAnonymous && isModerator) {
                    TextButton(onClick = onReveal) {
                        Text(text = "Reveal author")
                    }
                }
            }

            if (thread.body.isNotBlank()) {
                Text(
                    text = thread.body,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                VoteRow(
                    score = thread.score,
                    myVote = thread.voteOf(currentUid),
                    onUpvote = onUpvote,
                    onDownvote = onDownvote
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${thread.commentCount} comments",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: BoardComment,
    currentUid: String,
    opAlias: String?,
    isModerator: Boolean,
    canDelete: Boolean,
    canReply: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onReply: () -> Unit,
    onAuthorClick: () -> Unit,
    onReveal: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp + (comment.depth * 14).dp,
                end = 16.dp,
                top = 6.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InitialsAvatar(name = comment.displayName, size = 28.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.isAnonymous) {
                    GhostAuthorText(
                        alias = comment.anonAlias,
                        isOp = opAlias != null && comment.anonAlias == opAlias,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    TextButton(
                        onClick = onAuthorClick,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = comment.authorName.ifBlank { "Student" },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Text(
                    text = " · ${formatRelativeTime(comment.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium
            )

            if (comment.isHidden && isModerator) {
                TagChip(text = "Hidden pending review")
                Row {
                    TextButton(onClick = onRestore) {
                        Text(text = "Restore")
                    }
                    TextButton(onClick = onDelete) {
                        Text(text = "Delete")
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                VoteRow(
                    score = comment.score,
                    myVote = comment.voteOf(currentUid),
                    onUpvote = onUpvote,
                    onDownvote = onDownvote,
                    compact = true
                )

                if (canReply) {
                    TextButton(onClick = onReply) {
                        Text(text = "Reply", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (comment.isAnonymous && isModerator) {
                    TextButton(onClick = onReveal) {
                        Text(text = "Reveal author", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        ActionsMenu(
            canDelete = canDelete,
            deleteLabel = "Delete comment",
            reportLabel = "Report comment",
            canBlock = !comment.isAnonymous,
            onDelete = onDelete,
            onReport = onReport,
            onBlock = onBlock
        )
    }
}

@Composable
private fun VoteRow(
    score: Long,
    myVote: Int,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val tint = if (myVote != 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onUpvote,
            modifier = Modifier.size(if (compact) 30.dp else 36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = if (myVote == 1) "Remove upvote" else "Upvote",
                tint = if (myVote == 1) tint else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (myVote != 0) tint else MaterialTheme.colorScheme.onSurface
        )

        IconButton(
            onClick = onDownvote,
            modifier = Modifier.size(if (compact) 30.dp else 36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (myVote == -1) "Remove downvote" else "Downvote",
                tint = if (myVote == -1) tint else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionsMenu(
    canDelete: Boolean,
    deleteLabel: String,
    reportLabel: String,
    canBlock: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More options"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(text = deleteLabel) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(text = reportLabel) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Flag, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onReport()
                    }
                )
                if (canBlock) {
                    DropdownMenuItem(
                        text = { Text(text = "Block author") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Block, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onBlock()
                        }
                    )
                }
            }
        }
    }
}
