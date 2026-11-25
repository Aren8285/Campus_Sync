package com.campussync.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.navigation.Routes
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.Message
import com.campussync.app.data.model.UserProfile
import com.campussync.app.data.model.isMutedBy
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatThreadContent(
    val chatId: String = "",
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val currentUid: String = "",
    val typingNames: List<String> = emptyList(),
    val students: List<UserProfile> = emptyList(),
    val isLoadingStudents: Boolean = false
)

@HiltViewModel
class ChatThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val chatId: String = savedStateHandle.get<String>(Routes.ChatThreadArg).orEmpty()

    private val _uiState = MutableStateFlow<UiState<ChatThreadContent>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChatThreadContent>> = _uiState.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _leftChat = MutableStateFlow(false)
    val leftChat: StateFlow<Boolean> = _leftChat.asStateFlow()

    private var content = ChatThreadContent(chatId = chatId)
    private var typingUpdates: Map<String, Long> = emptyMap()
    private var typingTicker: Job? = null
    private var chatJob: Job? = null
    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private var readRequestInFlight = false
    private var lastTypingWrite = 0L
    private var typingSent = false

    init {
        start()
    }

    fun retry() {
        start()
    }

    fun onInputChange(value: String) {
        _input.value = value

        val now = System.currentTimeMillis()
        if (value.isBlank()) {
            if (typingSent) {
                typingSent = false
                viewModelScope.launch {
                    runCatching { chatRepository.setTyping(chatId, false) }
                }
            }
            return
        }

        if (!typingSent || now - lastTypingWrite > TYPING_WRITE_INTERVAL_MS) {
            typingSent = true
            lastTypingWrite = now
            viewModelScope.launch {
                runCatching { chatRepository.setTyping(chatId, true) }
            }
        }
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _isSending.value) return
        sendMessage(text = text, imageUrl = "")
    }

    fun sendImage(imageUrl: String, caption: String) {
        val url = imageUrl.trim()
        if (url.isEmpty()) {
            _message.value = "Add an image link first."
            return
        }
        sendMessage(text = caption.trim(), imageUrl = url)
    }

    fun acceptRequest() {
        work("We couldn't accept this request.") {
            chatRepository.acceptRequest(chatId)
            "Request accepted."
        }
    }

    fun declineRequest() {
        viewModelScope.launch {
            try {
                chatRepository.declineRequest(chatId)
                _leftChat.value = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't decline this request."
            }
        }
    }

    fun toggleMute() {
        val chat = content.chat ?: return
        val uid = content.currentUid
        val muted = !chat.isMutedBy(uid)

        work("We couldn't update your notification setting.") {
            chatRepository.setMuted(chatId, muted)
            if (muted) "Notifications muted for this chat." else "Notifications unmuted."
        }
    }

    fun renameGroup(title: String) {
        work("We couldn't rename this group.") {
            chatRepository.renameGroup(chatId, title)
            "Group renamed."
        }
    }

    fun loadStudents() {
        val chat = content.chat ?: return
        // The picker filters out current members itself, so a loaded list stays
        // correct after adds and removals; do not spend 50 reads reopening it.
        if (content.isLoadingStudents || content.students.isNotEmpty()) return

        content = content.copy(isLoadingStudents = true)
        publish()

        viewModelScope.launch {
            try {
                val students = chatRepository
                    .getStudents()
                    .filter { it.uid !in chat.members }
                content = content.copy(students = students, isLoadingStudents = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                content = content.copy(isLoadingStudents = false)
                _message.value = error.message ?: "We couldn't load your college's students."
            } finally {
                publish()
            }
        }
    }

    fun addMember(student: UserProfile) {
        work("We couldn't add this student.") {
            chatRepository.addMembers(chatId, listOf(student))
            "${student.name.ifBlank { "The student" }} was added to the group."
        }
    }

    fun removeMember(uid: String) {
        work("We couldn't remove this student.") {
            chatRepository.removeMember(chatId, uid)
            "The student was removed from the group."
        }
    }

    fun setAdmin(uid: String, admin: Boolean) {
        work("We couldn't update this admin.") {
            chatRepository.setAdmin(chatId, uid, admin)
            if (admin) "The student is now a group admin." else "The student is no longer an admin."
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            try {
                chatRepository.leaveGroup(chatId)
                _leftChat.value = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't remove you from this group."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun start() {
        // chatId can arrive from a notification intent, so never build a Firestore
        // path from an unvalidated string.
        if (!CHAT_ID_PATTERN.matches(chatId)) {
            _uiState.value = UiState.Error("This chat link is invalid.")
            return
        }

        chatJob?.cancel()
        messagesJob?.cancel()
        typingJob?.cancel()
        typingTicker?.cancel()
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val account = authRepository.currentUser()
                    ?: throw AppException("You're signed out. Please log in again.")
                content = content.copy(currentUid = account.uid)
                observeChat()
                observeMessages()
                observeTyping()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't open this chat."
                )
            }
        }
    }

    private fun observeChat() {
        chatJob = viewModelScope.launch {
            chatRepository.observeChat(chatId)
                .catch { error ->
                    if (error !is CancellationException) {
                        fail(error.message ?: "We couldn't open this chat.")
                    }
                }
                .collect { chat ->
                    if (chat == null) {
                        if (content.chat == null) {
                            _uiState.value = UiState.Empty
                        } else {
                            _leftChat.value = true
                        }
                        return@collect
                    }

                    if (content.currentUid.isNotBlank() &&
                        content.currentUid !in chat.members
                    ) {
                        _uiState.value = UiState.Error("You're no longer in this chat.")
                        return@collect
                    }

                    content = content.copy(chat = chat)
                    publish()
                    markReadIfNeeded()
                }
        }
    }

    private fun observeMessages() {
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(chatId)
                .catch { error ->
                    if (error !is CancellationException) {
                        fail(error.message ?: "We couldn't load these messages.")
                    }
                }
                .collect { messages ->
                    content = content.copy(messages = messages)
                    publish()
                    markReadIfNeeded()
                }
        }
    }

    private fun observeTyping() {
        typingJob = viewModelScope.launch {
            chatRepository.observeTyping(chatId)
                .catch { error ->
                    if (error !is CancellationException) {
                        _message.value = error.message
                    }
                }
                .collect { updates ->
                    typingUpdates = updates
                    startTypingTicker()
                }
        }
    }

    private fun startTypingTicker() {
        if (typingTicker?.isActive == true) return

        typingTicker = viewModelScope.launch {
            while (true) {
                publish()
                val now = System.currentTimeMillis()
                val stillTyping = typingUpdates.any { (uid, at) ->
                    uid != content.currentUid && now - at <= ChatRepository.TYPING_WINDOW_MS
                }
                if (!stillTyping) break
                delay(TYPING_TICK_MS)
            }
        }
    }

    private fun markReadIfNeeded() {
        val chat = content.chat ?: return
        val last = chat.lastMessage ?: return
        if (last.senderId == content.currentUid) return
        if (readRequestInFlight) return

        val marker = chat.readAt[content.currentUid] ?: 0L
        val messageAt = last.at ?: chat.lastMessageAt ?: return
        if (messageAt <= marker) return

        readRequestInFlight = true
        viewModelScope.launch {
            runCatching { chatRepository.markRead(chatId) }
            readRequestInFlight = false
        }
    }

    private fun sendMessage(text: String, imageUrl: String) {
        if (_isSending.value) return
        _isSending.value = true
        typingSent = false

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(chatId, text, imageUrl)
                if (imageUrl.isBlank()) _input.value = ""
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't send your message."
            } finally {
                _isSending.value = false
            }
        }
    }

    private fun work(fallback: String, block: suspend () -> String) {
        viewModelScope.launch {
            try {
                _message.value = block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: fallback
            }
        }
    }

    private fun fail(message: String) {
        if (content.chat == null) {
            _uiState.value = UiState.Error(message)
        } else {
            _message.value = message
        }
    }

    private fun publish() {
        val chat = content.chat ?: return
        val now = System.currentTimeMillis()

        val typingNames = typingUpdates.entries
            .filter { (uid, at) ->
                uid != content.currentUid && now - at <= ChatRepository.TYPING_WINDOW_MS
            }
            .map { (uid, _) -> chat.memberNames[uid].orEmpty().ifBlank { "Someone" } }
            .distinct()

        content = content.copy(typingNames = typingNames)
        _uiState.value = UiState.Success(content)
    }

    private companion object {
        const val TYPING_TICK_MS = 2_000L
        const val TYPING_WRITE_INTERVAL_MS = 3_000L
        val CHAT_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,120}")
    }
}
