package com.campussync.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.needsMyResponse
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatListContent(
    val chats: List<Chat> = emptyList(),
    val requests: List<Chat> = emptyList(),
    val currentUid: String = "",
    val isWorking: Boolean = false
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChatListContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChatListContent>> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var currentUid = ""
    private var chats: List<Chat> = emptyList()
    private var isWorking = false
    private var chatJob: Job? = null

    init {
        load()
    }

    fun load() {
        chatJob?.cancel()
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                currentUid = account.uid


                chatJob = viewModelScope.launch {
                    chatRepository.observeChats()
                        .catch { error ->
                            if (error !is CancellationException) {
                                if (chats.isEmpty()) {
                                    _uiState.value = UiState.Error(
                                        error.message ?: "We couldn't load your chats."
                                    )
                                } else {
                                    _message.value = error.message
                                }
                            }
                        }
                        .collect { loaded ->
                            chats = loaded
                            publish()
                        }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load your chats."
                )
            }
        }
    }

    fun accept(chatId: String) {
        work(chatId, "We couldn't accept this request.") {
            chatRepository.acceptRequest(chatId)
            "Request accepted."
        }
    }

    fun decline(chatId: String) {
        work(chatId, "We couldn't decline this request.") {
            chatRepository.declineRequest(chatId)
            "Request declined."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun work(chatId: String, fallback: String, block: suspend () -> String) {
        if (isWorking) return
        isWorking = true
        publish()

        viewModelScope.launch {
            try {
                _message.value = block()
                // A declined chat disappears from the listener; an accepted one
                // arrives with the new status.
                chats = chats.filterNot { it.id == chatId && it.needsMyResponse(currentUid) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: fallback
            } finally {
                isWorking = false
                publish()
            }
        }
    }

    private fun publish() {
        val requests = chats
            .filter { it.needsMyResponse(currentUid) }
            .sortedByDescending { it.lastMessageAt ?: 0L }
        val conversations = chats
            .filterNot { it.needsMyResponse(currentUid) }
            .sortedByDescending { it.lastMessageAt ?: 0L }

        _uiState.value = when {
            requests.isEmpty() && conversations.isEmpty() -> UiState.Empty
            else -> UiState.Success(
                ChatListContent(
                    chats = conversations,
                    requests = requests,
                    currentUid = currentUid,
                    isWorking = isWorking
                )
            )
        }
    }
}
