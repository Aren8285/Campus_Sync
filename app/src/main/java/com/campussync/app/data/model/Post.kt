package com.campussync.app.data.model

/**
 * A feed post.
 *
 * Firestore path: /posts/{postId}
 *
 * Author name and username are copied onto the post so the feed does not need an
 * extra read per author. Likes live in [likedBy]; the count is always its size.
 */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val collegeId: String = "",
    val text: String = "",
    val imageUrls: List<String> = emptyList(),
    val likedBy: List<String> = emptyList(),
    val commentCount: Long = 0L,
    val createdAt: Long? = null
)

fun Post.isLikedBy(uid: String): Boolean = uid.isNotBlank() && uid in likedBy
