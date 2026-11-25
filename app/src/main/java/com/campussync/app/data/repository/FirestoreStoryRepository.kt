package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Story
import com.campussync.app.data.model.StoryType
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.StoryRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreStoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : StoryRepository {

    private val stories = firestore.collection(STORIES)

    override fun observeActiveStories(collegeId: String): Flow<List<Story>> = callbackFlow {
        val registration = stories
            .whereEqualTo("collegeId", collegeId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_STORY_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't load stories."))
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                trySend(
                    snapshot?.documents
                        ?.mapNotNull { it.toStory() }
                        ?.filter { it.isActive(now) }
                        .orEmpty()
                )
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getAuthorStories(authorId: String, collegeId: String): List<Story> {
        try {
            val snapshot = stories
                .whereEqualTo("collegeId", collegeId)
                .whereEqualTo("authorId", authorId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(VIEWER_STORY_LIMIT.toLong())
                .get()
                .awaitResult()

            val now = System.currentTimeMillis()
            return snapshot.documents
                .mapNotNull { it.toStory() }
                .filter { it.isActive(now) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load this story.")
        }
    }

    override suspend fun createStory(
        author: UserProfile,
        type: StoryType,
        text: String,
        imageUrl: String
    ) {
        val uid = requireUid()
        if (author.uid != uid) {
            throw AppException("Your profile doesn't match this account. Please log in again.")
        }

        val trimmedText = text.trim()
        val trimmedImage = imageUrl.trim()

        if (type == StoryType.Image && trimmedImage.isBlank()) {
            throw AppException("Add an image link for an image story.")
        }
        if (type == StoryType.Text && trimmedText.isBlank()) {
            throw AppException("Write something for your story.")
        }
        if (type == StoryType.Image && !trimmedImage.startsWith("https://")) {
            throw AppException("Image links must start with https://.")
        }
        if (trimmedText.length > MAX_TEXT_LENGTH) {
            throw AppException("Stories can be up to $MAX_TEXT_LENGTH characters.")
        }

        // Text stories carry no link, which the rules also enforce.
        val storedImage = if (type == StoryType.Image) trimmedImage else ""

        val now = System.currentTimeMillis()
        val expiresAt = Timestamp(Date(now + TimeUnit.HOURS.toMillis(EXPIRY_HOURS)))

        try {
            stories.add(
                mapOf(
                    "authorId" to uid,
                    "authorName" to author.name,
                    "authorUsername" to author.username,
                    "collegeId" to author.collegeId,
                    "type" to type.wireValue,
                    "text" to trimmedText,
                    "imageUrl" to storedImage,
                    "seenBy" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to expiresAt
                )
            ).awaitResult()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't publish your story. Please try again.")
        }
    }

    override suspend fun markSeen(storyId: String) {
        val uid = requireUid()
        try {
            stories.document(storyId)
                .update("seenBy", FieldValue.arrayUnion(uid))
                .awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Marking a story as seen is best effort; never block the viewer on it.
            throw error.toFirestoreException("We couldn't update this story.")
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private fun DocumentSnapshot.toStory(): Story? {
        if (!exists()) return null
        return Story(
            id = id,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            authorUsername = getString("authorUsername").orEmpty(),
            collegeId = getString("collegeId").orEmpty(),
            type = StoryType.fromWire(getString("type").orEmpty()),
            text = getString("text").orEmpty(),
            imageUrl = getString("imageUrl").orEmpty(),
            seenBy = (get("seenBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            expiresAt = getTimestamp("expiresAt")?.toDate()?.time,
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private companion object {
        const val STORIES = "stories"
        const val EXPIRY_HOURS = 24L
        const val MAX_TEXT_LENGTH = 200
        const val FEED_STORY_LIMIT = 50
        const val VIEWER_STORY_LIMIT = 30
    }
}
