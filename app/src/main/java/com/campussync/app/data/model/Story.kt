package com.campussync.app.data.model

/** How a story is shown in the viewer. */
enum class StoryType(val wireValue: String) {
    Text("text"),
    Image("image");

    companion object {
        fun fromWire(value: String): StoryType =
            entries.firstOrNull { it.wireValue == value } ?: Text
    }
}

/**
 * A 24-hour story.
 *
 * Firestore path: /stories/{storyId}
 *
 * Text and images are referenced by external link; the app never uploads files.
 * [expiresAt] is set by the client to now + 24 hours and is enforced by client
 * filtering plus a Firestore TTL policy on this field (see docs/SETUP.md). No
 * Cloud Function is needed.
 */
data class Story(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val collegeId: String = "",
    val type: StoryType = StoryType.Text,
    val text: String = "",
    val imageUrl: String = "",
    val seenBy: List<String> = emptyList(),
    val expiresAt: Long? = null,
    val createdAt: Long? = null
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        (expiresAt ?: 0L) > now
}
