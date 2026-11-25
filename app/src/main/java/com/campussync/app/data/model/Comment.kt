package com.campussync.app.data.model

/**
 * A comment on a post.
 *
 * Firestore path: /posts/{postId}/comments/{commentId}
 *
 * Threading is one level deep: a top-level comment has a blank [parentId], and a
 * reply points at a top-level comment. Replies to replies are attached to the
 * same top-level comment.
 */
data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val text: String = "",
    val parentId: String = "",
    val createdAt: Long? = null
) {
    val isReply: Boolean
        get() = parentId.isNotBlank()
}
