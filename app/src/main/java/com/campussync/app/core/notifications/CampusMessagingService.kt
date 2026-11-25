package com.campussync.app.core.notifications

import com.campussync.app.domain.repository.ChatRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives chat pushes and shows a notification for them.
 *
 * The Spark plan has no Cloud Functions, so no client can send a push. This
 * service covers the receive side: a trusted sender (a later approved server, or
 * a manual test from the Firebase console) targets the token stored under
 * `/users/{uid}/devices`. Muted chats never notify.
 */
@AndroidEntryPoint
class CampusMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var chatRepository: ChatRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Firebase 25.1 deprecated onNewToken in favour of Firebase Installation IDs.
    // Registration tokens are still co-supported and are the ID a console test
    // sender can target, so the app keeps storing them until push sending lands.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { chatRepository.savePushToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val chatId = message.data[KEY_CHAT_ID].orEmpty()
        if (chatId.isBlank()) return

        val title = message.data[KEY_SENDER_NAME]
            ?.takeIf { it.isNotBlank() }
            ?: message.notification?.title.orEmpty().ifBlank { "New message" }
        val body = message.data[KEY_BODY]
            ?.takeIf { it.isNotBlank() }
            ?: message.notification?.body.orEmpty().ifBlank { "You have a new message." }

        scope.launch {
            // Skips muted chats, chats the signed-in student is not a member of,
            // and tokens left behind by a previous account on this device.
            val shouldNotify = runCatching {
                chatRepository.shouldNotify(chatId)
            }.getOrDefault(false)
            if (!shouldNotify) return@launch
            MessageNotifier.show(applicationContext, chatId, title, body)
        }
    }

    private companion object {
        const val KEY_CHAT_ID = "chatId"
        const val KEY_SENDER_NAME = "senderName"
        const val KEY_BODY = "body"
    }
}
