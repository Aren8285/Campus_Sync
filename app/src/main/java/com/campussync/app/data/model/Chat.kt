package com.campussync.app.data.model

/**
 * A one-to-one conversation or a group chat.
 */
enum class ChatType(val wireValue: String) {
    Direct("direct"),
    Group("group");

    companion object {
        fun fromWire(value: String?): ChatType =
            entries.firstOrNull { it.wireValue == value } ?: Direct
    }
}

/**
 * A direct chat starts as a request when the two students are not connected, so
 * the recipient can accept or decline it before the conversation opens.
 */
enum class ChatStatus(val wireValue: String) {
    Accepted("accepted"),
    Request("request");

    companion object {
        fun fromWire(value: String?): ChatStatus =
            entries.firstOrNull { it.wireValue == value } ?: Accepted
    }
}

/**
 * The preview stored on the chat document so the chat list is one bounded query.
 */
data class LastMessage(
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.Text,
    val text: String = "",
    val at: Long? = null
)

/**
 * A conversation.
 *
 * Firestore path: /chats/{chatId}
 *
 * [members] is the whole access model: only those UIDs can read or write the
 * chat. Names are denormalized into [memberNames] so the chat list does not need
 * a profile read per member. [readAt] and [mutedBy] are per-member state kept on
 * the same document so a member can update only their own entry.
 */
data class Chat(
    val id: String = "",
    val type: ChatType = ChatType.Direct,
    val title: String = "",
    val members: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val admins: List<String> = emptyList(),
    val createdBy: String = "",
    val status: ChatStatus = ChatStatus.Accepted,
    val requestedBy: String = "",
    val lastMessage: LastMessage? = null,
    val lastMessageAt: Long? = null,
    val readAt: Map<String, Long> = emptyMap(),
    val mutedBy: List<String> = emptyList(),
    val createdAt: Long? = null
)

fun Chat.isMember(uid: String): Boolean = uid.isNotBlank() && uid in members

fun Chat.isAdmin(uid: String): Boolean = uid.isNotBlank() && uid in admins

fun Chat.isMutedBy(uid: String): Boolean = uid.isNotBlank() && uid in mutedBy

fun Chat.otherMemberId(uid: String): String? = members.firstOrNull { it != uid }

/** The title to show: the group name, or the other student's name for a direct chat. */
fun Chat.titleFor(uid: String): String = when (type) {
    ChatType.Group -> title.ifBlank { "Group chat" }
    ChatType.Direct -> {
        val other = otherMemberId(uid)
        memberNames[other.orEmpty()].orEmpty().ifBlank {
            if (other.isNullOrBlank()) "Direct chat" else "Student"
        }
    }
}

/** The one-line preview for the chat list. */
fun Chat.previewText(): String {
    val last = lastMessage ?: return if (type == ChatType.Group) {
        "Group created"
    } else {
        "Say hello"
    }

    return if (last.type == MessageType.Image) {
        "Photo"
    } else {
        last.text
    }
}

/**
 * True when the newest message came from someone else and arrived after this
 * member's last read marker. The chat list uses it for the unread dot.
 */
fun Chat.hasUnreadFor(uid: String): Boolean {
    val last = lastMessage ?: return false
    if (last.senderId == uid) return false
    val at = lastMessageAt ?: return false
    return at > (readAt[uid] ?: 0L)
}

/** True when the signed-in student has to accept or decline this chat. */
fun Chat.needsMyResponse(uid: String): Boolean =
    status == ChatStatus.Request && requestedBy != uid && isMember(uid)
