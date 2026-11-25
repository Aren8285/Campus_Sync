package com.campussync.app.data.model

/**
 * A message board for one college.
 *
 * Firestore path: /boards/{boardId}
 *
 * Boards are created by the owner in the Firebase console. The app only reads
 * them, so the board list is small and the document ID is chosen by hand.
 */
data class Board(
    val id: String = "",
    val collegeId: String = "",
    val name: String = "",
    val description: String = "",
    val moderatorIds: List<String> = emptyList(),
    val createdAt: Long? = null
) {
    fun isModerator(uid: String): Boolean =
        uid.isNotBlank() && uid in moderatorIds
}

/**
 * A discussion thread inside a board.
 *
 * Firestore path: /boards/{boardId}/threads/{threadId}
 *
 * Votes are stored as UID lists, so a student can appear in at most one list and
 * the score is always upvotedBy.size - downvotedBy.size.
 */
data class BoardThread(
    val id: String = "",
    val boardId: String = "",
    val collegeId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val body: String = "",
    val score: Long = 0L,
    val upvotedBy: List<String> = emptyList(),
    val downvotedBy: List<String> = emptyList(),
    val commentCount: Long = 0L,
    val createdAt: Long? = null,
    val pinned: Boolean = false,
    val isAnonymous: Boolean = false,
    val anonAlias: String = "",
    val reportCount: Long = 0L
) {
    /** 1 for an upvote, -1 for a downvote, 0 when this student has not voted. */
    fun voteOf(uid: String): Int = voteOf(uid, upvotedBy, downvotedBy)

    /** Alias-first name used everywhere a thread is shown. */
    val displayName: String
        get() = if (isAnonymous) anonAlias else authorName

    /** Anonymous items disappear from normal users once enough reports arrive. */
    val isHidden: Boolean
        get() = isAnonymous && reportCount >= GHOST_HIDE_REPORT_COUNT
}

/**
 * A comment on a board thread.
 *
 * Firestore path: /boards/{boardId}/threads/{threadId}/comments/{commentId}
 *
 * Replies nest up to three levels. A top-level comment has a null [parentId] and
 * depth 0; a reply's depth is always its parent's depth plus one.
 */
data class BoardComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val body: String = "",
    val parentId: String? = null,
    val depth: Int = 0,
    val score: Long = 0L,
    val upvotedBy: List<String> = emptyList(),
    val downvotedBy: List<String> = emptyList(),
    val createdAt: Long? = null,
    val isAnonymous: Boolean = false,
    val anonAlias: String = "",
    val reportCount: Long = 0L
) {
    /** 1 for an upvote, -1 for a downvote, 0 when this student has not voted. */
    fun voteOf(uid: String): Int = voteOf(uid, upvotedBy, downvotedBy)

    val displayName: String
        get() = if (isAnonymous) anonAlias else authorName

    val isHidden: Boolean
        get() = isAnonymous && reportCount >= GHOST_HIDE_REPORT_COUNT
}

/** The real author shown to a moderator after a reason is recorded. */
data class RevealedAuthor(
    val name: String,
    val username: String
)

const val GHOST_HIDE_REPORT_COUNT = 3L

private fun voteOf(uid: String, upvotedBy: List<String>, downvotedBy: List<String>): Int =
    when {
        uid.isBlank() -> 0
        uid in upvotedBy -> 1
        uid in downvotedBy -> -1
        else -> 0
    }
