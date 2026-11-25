package com.campussync.app.data.model

/**
 * The kind of content a message carries. Images are external links, never uploads.
 */
enum class MessageType(val wireValue: String) {
    Text("text"),
    Image("image");

    companion object {
        fun fromWire(value: String?): MessageType =
            entries.firstOrNull { it.wireValue == value } ?: Text
    }
}

/**
 * One message in a chat.
 *
 * Firestore path: /chats/{chatId}/messages/{messageId}
 *
 * The sender's name is copied onto the message so the thread needs one query and
 * no per-message profile reads.
 */
data class Message(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.Text,
    val text: String = "",
    val imageUrl: String = "",
    val createdAt: Long? = null
)
