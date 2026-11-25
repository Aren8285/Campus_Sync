package com.campussync.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<UserProfile>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<UserProfile>>> = _uiState.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _createdChatId = MutableStateFlow<String?>(null)
    val createdChatId: StateFlow<String?> = _createdChatId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var students: List<UserProfile> = emptyList()

    init {
        load()
    }

    fun load() {
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                students = chatRepository.getStudents()
                _uiState.value = if (students.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(students)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = UiState.Error(
                    error.message ?: "We couldn't load your college's students."
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        _title.value = value
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun toggleSelected(uid: String) {
        _selected.value = if (uid in _selected.value) {
            _selected.value - uid
        } else {
            _selected.value + uid
        }
    }

    fun create() {
        if (_isCreating.value) return

        val chosen = students.filter { it.uid in _selected.value }
        if (chosen.isEmpty()) {
            _message.value = "Pick a classmate first."
            return
        }
        if (chosen.size > 1 && _title.value.isBlank()) {
            _message.value = "Give the group a name first."
            return
        }

        _isCreating.value = true

        viewModelScope.launch {
            try {
                val chatId = if (chosen.size == 1 && _title.value.isBlank()) {
                    chatRepository.openDirectChat(chosen.first())
                } else {
                    chatRepository.createGroup(_title.value, chosen)
                }
                _createdChatId.value = chatId
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "We couldn't start this chat."
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun clearCreated() {
        _createdChatId.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
