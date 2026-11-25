package com.campussync.app.core.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campussync.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Stores this device's FCM token once the student is in the main app, so a
 * trusted sender can target it even before the student opens Messages.
 *
 * Registration is best-effort: chat works in real time without it.
 */
@HiltViewModel
class PushRegistrationViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private var requested = false

    fun register() {
        if (requested) return
        requested = true
        viewModelScope.launch {
            runCatching { chatRepository.registerPushToken() }
        }
    }
}

@Composable
fun RegisterForPushNotifications(
    viewModel: PushRegistrationViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.register()
    }
}
