package com.campussync.app.feature.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.TagChip
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Board
import com.campussync.app.data.model.BoardThread
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.BoardRepository
import com.campussync.app.domain.repository.BoardSort
import com.campussync.app.domain.repository.SafetyRepository
import com.campussync.app.domain.repository.ThreadCursor
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BoardThreadsContent(
    val board: Board,
    val threads: List<BoardThread>,
    val currentProfile: UserProfile,
    val sort: BoardSort,
    val hasMore: Boolean,
    val isLoadingMore: Boolean,
    val isModerator: Boolean
)

@HiltViewModel
class BoardThreadsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val boardRepository: BoardRepository,
    private val safetyRepository: SafetyRepository
) : ViewModel() {

    private val boardId: String =
        savedStateHandle.get<String>(Routes.BoardIdArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<BoardThreadsContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<BoardThreadsContent>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var profile: UserProfile? = null
    private var board: Board? = null
    private var threads: List<BoardThread> = emptyList()
    private var blockedUids: Set<String> = emptySet()
    private var nextCursor: ThreadCursor? = null
    private var hasMore = false
    private var isLoadingMore = false
    private var sort = BoardSort.New
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()

        if (boardId.isBlank()) {
            _uiState.value = UiState.Error("This board link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        loadJob = viewModelScope.launch {
            try {
                val (loadedProfile, loadedBoard, blocked) = loadContext()
                val page = boardRepository.getThreads(boardId, sort, cursor = null)

                profile = loadedProfile
                board = loadedBoard
                blockedUids = blocked
                threads = page.threads
                nextCursor = page.nextCursor
                hasMore = page.nextCursor != null
                isLoadingMore = false
                publish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load this board."
                )
            }
        }
    }

    fun refresh() {
        if (boardId.isBlank() || _uiState.value !is UiState.Success) {
            if (_uiState.value !is UiState.Success) load()
            return
        }

        viewModelScope.launch {
            try {
                val page = boardRepository.getThreads(boardId, sort, cursor = null)
                threads = page.threads
                nextCursor = page.nextCursor
                hasMore = page.nextCursor != null
                publish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't refresh this board."
            }
        }
    }

    fun setSort(newSort: BoardSort) {
        if (newSort == sort) return
        sort = newSort
        load()
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (isLoadingMore) return

        isLoadingMore = true
        publish()

        viewModelScope.launch {
            try {
                val page = boardRepository.getThreads(boardId, sort, cursor)
                threads = threads + page.threads
                nextCursor = page.nextCursor
                hasMore = page.nextCursor != null
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't load more threads."
            } finally {
                isLoadingMore = false
                publish()
            }
        }
    }

    fun restoreThread(threadId: String) {
        viewModelScope.launch {
            try {
                boardRepository.restoreThread(boardId, threadId)
                _message.value = "The thread is visible again."
                refresh()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't restore this thread."
            }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            try {
                boardRepository.deleteThread(boardId, threadId)
                threads = threads.filterNot { it.id == threadId }
                publish()
                _message.value = "The thread was deleted."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't delete this thread."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun loadContext(): Triple<UserProfile, Board, Set<String>> {
        val account = authRepository.currentUser()
            ?: throw AppException("You're signed out. Please log in again.")
        val loadedProfile = userRepository.getUser(account.uid)
            ?: throw AppException("Finish your profile before opening the boards.")
        val loadedBoard = boardRepository.getBoard(boardId)
            ?: throw AppException("This board is no longer available.")
        val blocked = safetyRepository.getBlockedUids().toSet()

        return Triple(loadedProfile, loadedBoard, blocked)
    }

    private fun publish() {
        val currentProfile = profile
        val currentBoard = board

        if (currentProfile == null || currentBoard == null) {
            _uiState.value = UiState.Error("This board is no longer available.")
            return
        }

        val isModerator = currentBoard.isModerator(currentProfile.uid)

        _uiState.value = UiState.Success(
            BoardThreadsContent(
                board = currentBoard,
                threads = threads.filter {
                    it.authorId !in blockedUids && (!it.isHidden || isModerator)
                },
                currentProfile = currentProfile,
                sort = sort,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                isModerator = isModerator
            )
        )
    }
}

@Composable
fun BoardThreadsScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onNewThread: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoardThreadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        if (hasLoadedOnce) {
            viewModel.refresh()
        } else {
            hasLoadedOnce = true
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
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
                title = (state as? UiState.Success)?.data?.board?.name.orEmpty()
                    .ifBlank { "Board" },
                onBack = onBack
            )

            when (val boardState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading threads..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = boardState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "This board has no threads yet."
                )

                is UiState.Success -> {
                    val content = boardState.data

                    Column(modifier = Modifier.weight(1f)) {
                        if (content.board.description.isNotBlank()) {
                            Text(
                                text = content.board.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        Button(
                            onClick = onNewThread,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text = "New thread")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = content.sort == BoardSort.New,
                                onClick = { viewModel.setSort(BoardSort.New) },
                                label = { Text(text = "New") }
                            )
                            FilterChip(
                                selected = content.sort == BoardSort.Top,
                                onClick = { viewModel.setSort(BoardSort.Top) },
                                label = { Text(text = "Top") }
                            )
                        }

                        if (content.threads.isEmpty()) {
                            EmptyView(
                                modifier = Modifier.weight(1f),
                                message = "No threads here yet. Start the first discussion."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(content.threads, key = { it.id }) { thread ->
                                    ThreadRow(
                                        thread = thread,
                                        isModerator = content.isModerator,
                                        onClick = { onOpenThread(thread.id) },
                                        onRestore = { viewModel.restoreThread(thread.id) },
                                        onDelete = { viewModel.deleteThread(thread.id) }
                                    )
                                }

                                if (content.hasMore) {
                                    item(key = "load-more") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (content.isLoadingMore) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                OutlinedButton(onClick = viewModel::loadMore) {
                                                    Text(text = "Load more")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: BoardThread,
    isModerator: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thread.pinned) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = thread.title.ifBlank { "Untitled thread" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (thread.body.isNotBlank()) {
                Text(
                    text = thread.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (thread.isHidden && isModerator) {
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
                if (thread.isAnonymous) {
                    GhostAuthorText(
                        alias = thread.anonAlias,
                        isOp = true
                    )
                } else {
                    Text(
                        text = thread.authorName.ifBlank { "Student" },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = " · ${formatRelativeTime(thread.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${thread.score} pts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = " · ${thread.commentCount} comments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
