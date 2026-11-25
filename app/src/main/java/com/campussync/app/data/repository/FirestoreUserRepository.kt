package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.ProfileUpdate
import com.campussync.app.data.model.UserProfile
import com.campussync.app.core.util.UsernameValidator
import com.campussync.app.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : UserRepository {

    private val users = firestore.collection(USERS)
    private val usernames = firestore.collection(USERNAMES)
    private val follows = firestore.collection(FOLLOWS)

    override fun observeUser(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(
                    error.toFirestoreException(
                        "We couldn't load this profile. Please try again."
                    )
                )
                return@addSnapshotListener
            }

            trySend(snapshot?.takeIf { it.exists() }?.toUserProfile())
        }

        awaitClose { registration.remove() }
    }

    override suspend fun getUser(uid: String): UserProfile? {
        try {
            val snapshot = users.document(uid).get().awaitResult()
            return if (snapshot.exists()) snapshot.toUserProfile() else null
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load this profile.")
        }
    }

    override suspend fun isUsernameAvailable(username: String): Boolean {
        val normalized = UsernameValidator.normalize(username)
        try {
            val snapshot = usernames.document(normalized).get().awaitResult()
            return !snapshot.exists()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException(
                "We couldn't check that username. Please try again."
            )
        }
    }

    override suspend fun createProfile(profile: UserProfile) {
        val username = UsernameValidator.normalize(profile.username)

        try {
            val usernameRef = usernames.document(username)
            if (usernameRef.get().awaitResult().exists()) {
                throw AppException("That username is already taken. Please choose another.")
            }

            val batch = firestore.batch()
            batch.set(
                users.document(profile.uid),
                mapOf(
                    "name" to profile.name.trim(),
                    "username" to username,
                    "collegeId" to profile.collegeId,
                    "department" to profile.department.trim(),
                    "year" to profile.year,
                    "graduationYear" to profile.graduationYear,
                    "bio" to profile.bio.trim(),
                    "avatarUrl" to "",
                    "skills" to profile.skills,
                    "interests" to profile.interests,
                    "role" to ROLE_STUDENT,
                    "badges" to emptyList<String>(),
                    "karma" to 0L,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(usernameRef, mapOf("uid" to profile.uid))
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException(
                "We couldn't save your profile. That username may have just been taken."
            )
        }
    }

    override suspend fun updateProfile(current: UserProfile, update: ProfileUpdate) {
        val newUsername = UsernameValidator.normalize(update.username)

        try {
            if (newUsername != current.username) {
                val existing = usernames.document(newUsername).get().awaitResult()
                if (existing.exists()) {
                    throw AppException("That username is already taken. Please choose another.")
                }
            }

            val batch = firestore.batch()
            batch.update(
                users.document(current.uid),
                mapOf(
                    "name" to update.name.trim(),
                    "username" to newUsername,
                    "department" to update.department.trim(),
                    "year" to update.year,
                    "graduationYear" to update.graduationYear,
                    "bio" to update.bio.trim(),
                    "avatarUrl" to update.avatarUrl.trim(),
                    "skills" to update.skills,
                    "interests" to update.interests
                )
            )

            if (newUsername != current.username) {
                batch.set(usernames.document(newUsername), mapOf("uid" to current.uid))
                if (current.username.isNotBlank()) {
                    batch.delete(usernames.document(current.username))
                }
            }

            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException(
                "We couldn't save your changes. That username may have just been taken."
            )
        }
    }

    override suspend fun setFollowing(uid: String, following: Boolean) {
        val myUid = requireUid()
        if (uid.isBlank()) throw AppException("This student could not be found.")
        if (uid == myUid) throw AppException("You can't follow yourself.")

        val reference = follows.document(followId(myUid, uid))
        try {
            if (following) {
                reference.set(
                    mapOf(
                        "followerId" to myUid,
                        "followeeId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).awaitCompletion()
            } else {
                reference.delete().awaitCompletion()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException(
                if (following) {
                    "We couldn't follow this student. Please try again."
                } else {
                    "We couldn't unfollow this student. Please try again."
                }
            )
        }
    }

    override suspend fun isFollowing(uid: String): Boolean {
        val myUid = requireUid()
        if (uid.isBlank() || uid == myUid) return false

        return try {
            follows.document(followId(myUid, uid)).get().awaitResult().exists()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't check this student.")
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private fun followId(followerId: String, followeeId: String): String =
        "${followerId}_$followeeId"

    private companion object {
        const val USERS = "users"
        const val USERNAMES = "usernames"
        const val FOLLOWS = "follows"
        const val ROLE_STUDENT = "student"
    }
}
