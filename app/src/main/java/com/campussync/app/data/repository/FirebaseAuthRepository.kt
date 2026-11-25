package com.campussync.app.data.repository

import android.util.Log
import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.AuthUser
import com.campussync.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) : AuthRepository {

    private val _authState = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val authState: StateFlow<AuthUser?> = _authState.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser?.toAuthUser()
        }
    }

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signUp(
        fullName: String,
        email: String,
        password: String
    ): AuthUser {
        try {
            val result = auth
                .createUserWithEmailAndPassword(email.trim(), password)
                .awaitResult()

            val firebaseUser = result.user
                ?: throw AppException("We couldn't create your account. Please try again.")

            try {
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName.trim())
                    .build()
                firebaseUser.updateProfile(updates).awaitCompletion()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // The name is also stored in the local sign-up draft, so onboarding can recover.
                Log.w(TAG, "Could not set the display name during sign-up", error)
            }

            try {
                firebaseUser.sendEmailVerification().awaitCompletion()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // The verification screen lets the student resend the email.
                Log.w(TAG, "Could not send the verification email during sign-up", error)
            }

            val authUser = auth.currentUser?.toAuthUser() ?: firebaseUser.toAuthUser()
            _authState.value = authUser
            return authUser
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toAuthException("We couldn't create your account. Please try again.")
        }
    }

    override suspend fun login(email: String, password: String): AuthUser {
        try {
            val result = auth
                .signInWithEmailAndPassword(email.trim(), password)
                .awaitResult()

            val firebaseUser = result.user
                ?: throw AppException("We couldn't sign you in. Please try again.")

            val authUser = firebaseUser.toAuthUser()
            _authState.value = authUser
            return authUser
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toAuthException("We couldn't sign you in. Please try again.")
        }
    }

    override fun logout() {
        auth.signOut()
        _authState.value = null
    }

    override suspend fun sendVerification() {
        val firebaseUser = auth.currentUser
            ?: throw AppException("You're signed out. Please log in again.")

        try {
            firebaseUser.sendEmailVerification().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toAuthException(
                "We couldn't send the verification email. Please try again."
            )
        }
    }

    override suspend fun reloadUser(): AuthUser? {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            _authState.value = null
            return null
        }

        try {
            firebaseUser.reload().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toAuthException(
                "We couldn't check your verification status. Check your connection and try again."
            )
        }

        val authUser = auth.currentUser?.toAuthUser()

        // reload() updates the user but not the ID token, and Firestore rules
        // require the verified-email claim. Force one refresh so the next
        // request carries it instead of failing with permission denied.
        if (authUser?.isEmailVerified == true) {
            try {
                firebaseUser.getIdToken(true).awaitResult()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Offline or transient; the next token refresh will include the claim.
                Log.w(TAG, "Could not refresh the ID token after verification", error)
            }
        }

        _authState.value = authUser
        return authUser
    }

    override suspend fun resetPassword(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toAuthException("We couldn't send the password reset email.")
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified
    )

    private companion object {
        const val TAG = "FirebaseAuthRepository"
    }
}
