package com.campussync.app.data.model

/**
 * The signed-in user as the rest of the app sees them.
 *
 * This keeps Firebase Auth types out of ViewModels and repositories' public API.
 */
data class AuthUser(
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false
)
