package com.campussync.app.domain.repository

import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.Message
import com.campussync.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /** Chats the signed-in student belongs to, newest activity first. */
    fun observeChats(): Flow<List<Chat>>

    fun observeChat(chatId: String): Flow<Chat?>

    /** The newest bounded window of messages, oldest first. */
    fun observeMessages(chatId: String): Flow<List<Message>>

    /** UID to last-typing timestamp for the members currently typing. */
    fun observeTyping(chatId: String): Flow<Map<String, Long>>

    /** Same-college students who can be added to a chat. */
    suspend fun getStudents(limit: Int = STUDENT_LIMIT): List<UserProfile>

    /** Returns the direct chat ID, creating a request when the two are not connected. */
    suspend fun openDirectChat(other: UserProfile): String

    suspend fun createGroup(title: String, members: List<UserProfile>): String

    suspend fun sendMessage(chatId: String, text: String, imageUrl: String = "")

    suspend fun setTyping(chatId: String, typing: Boolean)

    suspend fun markRead(chatId: String)

    suspend fun setMuted(chatId: String, muted: Boolean)

    suspend fun renameGroup(chatId: String, title: String)

    suspend fun addMembers(chatId: String, members: List<UserProfile>)

    suspend fun removeMember(chatId: String, uid: String)

    suspend fun setAdmin(chatId: String, uid: String, admin: Boolean)

    suspend fun leaveGroup(chatId: String)

    suspend fun acceptRequest(chatId: String)

    suspend fun declineRequest(chatId: String)

    /**
     * True when the signed-in student is still a member of this chat and has not
     * muted it. Push handling uses it before showing a notification, so a token
     * left over from another account cannot surface someone else's messages.
     */
    suspend fun shouldNotify(chatId: String): Boolean

    /** Stores this device's FCM token so a trusted sender can target it. */
    suspend fun registerPushToken()

    suspend fun savePushToken(token: String)

    companion object {
        const val CHAT_LIMIT = 50
        const val MESSAGE_LIMIT = 100
        const val STUDENT_LIMIT = 50
        const val MAX_GROUP_MEMBERS = 50
        const val MAX_TEXT_LENGTH = 2_000
        const val MAX_TITLE_LENGTH = 60
        const val LAST_MESSAGE_PREVIEW = 140
        const val TYPING_WINDOW_MS = 8_000L
    }
}
