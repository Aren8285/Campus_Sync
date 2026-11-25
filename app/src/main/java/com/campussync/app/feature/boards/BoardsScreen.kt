package com.campussync.app.feature.boards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.TagChip
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Board
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.BoardRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BoardsContent(
    val boards: List<Board>,
    val currentProfile: UserProfile
)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val boardRepository: BoardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<BoardsContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<BoardsContent>> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        load()
    }

    fun load() {
        observeJob?.cancel()
        _uiState.value = UiState.Loading

        observeJob = viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val profile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before opening the boards.")

                boardRepository.observeBoards(profile.collegeId).collect { boards ->
                    _uiState.value = if (boards.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(
                            BoardsContent(
                                boards = boards,
                                currentProfile = profile
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load the message boards."
                )
            }
        }
    }
}

@Composable
fun BoardsScreen(
    onBack: () -> Unit,
    onOpenBoard: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoardsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(title = "Message boards", onBack = onBack)

            when (val boardState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading your college boards..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = boardState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "No boards yet. Your college admin adds them from the Firebase console."
                )

                is UiState.Success -> {
                    val content = boardState.data

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(content.boards, key = { it.id }) { board ->
                            BoardRow(
                                board = board,
                                isModerator = board.isModerator(content.currentProfile.uid),
                                onClick = { onOpenBoard(board.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    isModerator: Boolean,
    onClick: () -> Unit,
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
            Text(
                text = board.name.ifBlank { "Untitled board" },
                style = MaterialTheme.typography.titleMedium
            )

            if (board.description.isNotBlank()) {
                Text(
                    text = board.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isModerator) {
                TagChip(text = "Moderator")
            }
        }
    }
}
