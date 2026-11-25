package com.campussync.app.domain.repository

import com.campussync.app.data.model.Comment
import com.campussync.app.data.model.Post
import com.campussync.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** One page of the college feed. [nextCursor] is null when there are no more posts. */
data class FeedPage(
    val posts: List<Post>,
    val nextCursor: Long?
)

interface FeedRepository {
    suspend fun getFeedPage(cursor: Long?, pageSize: Int = PAGE_SIZE): FeedPage

    suspend fun createPost(author: UserProfile, text: String, imageUrls: List<String>)

    suspend fun deletePost(postId: String)

    suspend fun setLiked(postId: String, liked: Boolean)

    fun observePost(postId: String): Flow<Post?>

    fun observeComments(postId: String): Flow<List<Comment>>

    suspend fun addComment(author: UserProfile, postId: String, text: String, parentId: String)

    companion object {
        const val PAGE_SIZE = 10
    }
}
