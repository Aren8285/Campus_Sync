package com.campussync.app.data.model

/**
 * A student profile.
 *
 * Firestore path: /users/{uid}
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val username: String = "",
    val collegeId: String = "",
    val department: String = "",
    val year: String = "",
    val graduationYear: Int = 0,
    val bio: String = "",
    val avatarUrl: String = "",
    val skills: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val role: String = "student",
    val badges: List<String> = emptyList(),
    val karma: Long = 0L,
    val createdAt: Long? = null
)

/**
 * The fields the owner is allowed to change after onboarding.
 *
 * College, role, badges, and karma are intentionally absent.
 */
data class ProfileUpdate(
    val name: String,
    val username: String,
    val department: String,
    val year: String,
    val graduationYear: Int,
    val bio: String,
    val avatarUrl: String,
    val skills: List<String>,
    val interests: List<String>
)
