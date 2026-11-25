package com.campussync.app.feature.boards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateThreadContent(
    val board: Board,
    val currentProfile: UserProfile
)

@HiltViewModel
class CreateThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val boardRepository: BoardRepository
) : ViewModel() {

    private val boardId: String =
        savedStateHandle.get<String>(Routes.BoardIdArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<CreateThreadContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<CreateThreadContent>> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _anonymous = MutableStateFlow(false)
    val anonymous: StateFlow<Boolean> = _anonymous.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _createdThreadId = MutableStateFlow<String?>(null)
    val createdThreadId: StateFlow<String?> = _createdThreadId.asStateFlow()

    private var profile: UserProfile? = null

    init {
        load()
    }

    fun load() {
        if (boardId.isBlank()) {
            _uiState.value = UiState.Error("This board link is invalid.")
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                val loadedProfile = userRepository.getUser(account.uid)
                    ?: throw AppException("Finish your profile before posting.")
                val board = boardRepository.getBoard(boardId)
                    ?: throw AppException("This board is no longer available.")

                profile = loadedProfile
                _uiState.value = UiState.Success(
                    CreateThreadContent(
                        board = board,
                        currentProfile = loadedProfile
                    )
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load this board."
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        if (value.length > MAX_TITLE_LENGTH) return
        _title.value = value
    }

    fun onBodyChange(value: String) {
        if (value.length > MAX_BODY_LENGTH) return
        _body.value = value
    }

    fun setAnonymous(value: Boolean) {
        _anonymous.value = value
    }

    fun submit() {
        if (_uiState.value !is UiState.Success) return
        if (_isSubmitting.value) return

        val currentProfile = profile ?: return
        val cleanTitle = _title.value.trim()
        val cleanBody = _body.value.trim()

        if (cleanTitle.isBlank()) {
            _message.value = "Give your thread a title."
            return
        }
        if (cleanBody.isBlank()) {
            _message.value = "Write something in your thread."
            return
        }

        _isSubmitting.value = true

        viewModelScope.launch {
            try {
                val threadId = boardRepository.createThread(
                    boardId = boardId,
                    author = currentProfile,
                    title = cleanTitle,
                    body = cleanBody,
                    anonymous = _anonymous.value
                )
                _createdThreadId.value = threadId
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't publish your thread."
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_BODY_LENGTH = 2000
    }
}

@Composable
fun CreateThreadScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateThreadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val anonymous by viewModel.anonymous.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val createdThreadId by viewModel.createdThreadId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(createdThreadId) {
        createdThreadId?.let(onCreated)
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
            ScreenHeader(title = "New thread", onBack = onBack)

            when (val formState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Preparing the composer..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = formState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> Unit

                is UiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formState.data.board.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text(text = "Title") },
                            supportingText = {
                                Text(text = "${title.length}/120")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = body,
                            onValueChange = viewModel::onBodyChange,
                            label = { Text(text = "What do you want to discuss?") },
                            supportingText = {
                                Text(text = "${body.length}/2000")
                            },
                            minLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AnonymityToggle(
                            anonymous = anonymous,
                            onToggle = viewModel::setAnonymous
                        )

                        Button(
                            onClick = viewModel::submit,
                            enabled = title.isNotBlank() && body.isNotBlank() && !isSubmitting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = "Post thread")
                            }
                        }
                    }
                }
            }
        }
    }
}
