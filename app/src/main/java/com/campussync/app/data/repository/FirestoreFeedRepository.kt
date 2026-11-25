package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Comment
import com.campussync.app.data.model.Post
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.FeedPage
import com.campussync.app.domain.repository.FeedRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreFeedRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : FeedRepository {

    private val posts = firestore.collection(POSTS)
    private val users = firestore.collection(USERS)

    private var cachedCollege: Pair<String, String>? = null

    override suspend fun getFeedPage(cursor: Long?, pageSize: Int): FeedPage {
        val uid = requireUid()
        val collegeId = collegeIdOf(uid)

        try {
            var query = posts
                .whereEqualTo("collegeId", collegeId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            if (cursor != null) {
                query = query.startAfter(Timestamp(Date(cursor)))
            }

            val snapshot = query.get().awaitResult()
            val mapped = snapshot.documents.mapNotNull { it.toPost() }
            val nextCursor = if (mapped.size >= pageSize) mapped.lastOrNull()?.createdAt else null
            return FeedPage(posts = mapped, nextCursor = nextCursor)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load the feed. Please try again.")
        }
    }

    override suspend fun createPost(
        author: UserProfile,
        text: String,
        imageUrls: List<String>
    ) {
        val uid = requireUid()
        if (author.uid != uid) {
            throw AppException("Your profile doesn't match this account. Please log in again.")
        }

        val cleanImages = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanImages.size > MAX_IMAGES) {
            throw AppException("A post can have at most $MAX_IMAGES images.")
        }
        if (cleanImages.any { !it.startsWith("https://") }) {
            throw AppException("Image links must start with https://.")
        }

        val trimmedText = text.trim()
        if (trimmedText.isBlank() && cleanImages.isEmpty()) {
            throw AppException("Write something or add an image before posting.")
        }
        if (trimmedText.length > MAX_TEXT_LENGTH) {
            throw AppException("Posts can be up to $MAX_TEXT_LENGTH characters.")
        }

        try {
            posts.add(
                mapOf(
                    "authorId" to uid,
                    "authorName" to author.name,
                    "authorUsername" to author.username,
                    "collegeId" to author.collegeId,
                    "text" to trimmedText,
                    "imageUrls" to cleanImages,
                    "likedBy" to emptyList<String>(),
                    "commentCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't publish your post. Please try again.")
        }
    }

    override suspend fun deletePost(postId: String) {
        requireUid()
        try {
            posts.document(postId).delete().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't delete this post.")
        }
    }

    override suspend fun setLiked(postId: String, liked: Boolean) {
        val uid = requireUid()
        try {
            posts.document(postId).update(
                "likedBy",
                if (liked) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            ).awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't update your like. Please try again.")
        }
    }

    override fun observePost(postId: String): Flow<Post?> = callbackFlow {
        val registration = posts.document(postId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error.toFirestoreException("We couldn't load this post."))
                return@addSnapshotListener
            }

            trySend(snapshot?.takeIf { it.exists() }?.toPost())
        }

        awaitClose { registration.remove() }
    }

    override fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val registration = posts.document(postId)
            .collection(COMMENTS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(COMMENT_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't load the comments."))
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toComment() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    override suspend fun addComment(
        author: UserProfile,
        postId: String,
        text: String,
        parentId: String
    ) {
        val uid = requireUid()
        if (author.uid != uid) {
            throw AppException("Your profile doesn't match this account. Please log in again.")
        }

        val trimmed = text.trim()
        if (trimmed.isBlank()) throw AppException("Write a comment first.")
        if (trimmed.length > MAX_COMMENT_LENGTH) {
            throw AppException("Comments can be up to $MAX_COMMENT_LENGTH characters.")
        }

        try {
            val batch = firestore.batch()
            batch.set(
                posts.document(postId).collection(COMMENTS).document(),
                mapOf(
                    "authorId" to uid,
                    "authorName" to author.name,
                    "authorUsername" to author.username,
                    "text" to trimmed,
                    "parentId" to parentId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(posts.document(postId), "commentCount", FieldValue.increment(1))
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't add your comment. Please try again.")
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private suspend fun collegeIdOf(uid: String): String {
        cachedCollege?.let { (cachedUid, collegeId) ->
            if (cachedUid == uid) return collegeId
        }

        val snapshot = try {
            users.document(uid).get().awaitResult()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load your college.")
        }

        val collegeId = snapshot.getString("collegeId").orEmpty()
        if (collegeId.isBlank()) {
            throw AppException("Finish your profile before opening the feed.")
        }

        cachedCollege = uid to collegeId
        return collegeId
    }

    private fun DocumentSnapshot.toPost(): Post? {
        if (!exists()) return null
        return Post(
            id = id,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            authorUsername = getString("authorUsername").orEmpty(),
            collegeId = getString("collegeId").orEmpty(),
            text = getString("text").orEmpty(),
            imageUrls = (get("imageUrls") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            likedBy = (get("likedBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            commentCount = getLong("commentCount") ?: 0L,
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private fun DocumentSnapshot.toComment(): Comment? {
        if (!exists()) return null
        return Comment(
            id = id,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            authorUsername = getString("authorUsername").orEmpty(),
            text = getString("text").orEmpty(),
            parentId = getString("parentId").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private companion object {
        const val POSTS = "posts"
        const val USERS = "users"
        const val COMMENTS = "comments"
        const val MAX_IMAGES = 4
        const val MAX_TEXT_LENGTH = 1000
        const val MAX_COMMENT_LENGTH = 500
        const val COMMENT_LIMIT = 200
    }
}
