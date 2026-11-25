package com.campussync.app.domain.repository

import com.campussync.app.data.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** Emits whenever the Firebase user changes, including after reload(). */
    val authState: StateFlow<AuthUser?>

    fun currentUser(): AuthUser?

    suspend fun signUp(fullName: String, email: String, password: String): AuthUser

    suspend fun login(email: String, password: String): AuthUser

    fun logout()

    suspend fun sendVerification()

    suspend fun reloadUser(): AuthUser?

    suspend fun resetPassword(email: String)
}
