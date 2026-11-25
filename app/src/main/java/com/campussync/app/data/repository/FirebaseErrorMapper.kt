package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

internal fun Throwable.toAuthException(
    fallback: String = "Something went wrong. Please try again."
): AppException {
    if (this is AppException) return this

    val message = when {
        this is FirebaseAuthException -> when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "That email address doesn't look valid."
            "ERROR_WRONG_PASSWORD" -> "That password is incorrect."
            "ERROR_INVALID_CREDENTIAL" -> "That email or password is incorrect."
            "ERROR_USER_NOT_FOUND" -> "No account was found with that email."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses that email. Try logging in."
            "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Check your internet connection and try again."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a moment and try again."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in is not enabled in Firebase yet."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Please log in again and retry."
            else -> fallback
        }

        this is IOException -> "Check your internet connection and try again."
        else -> fallback
    }

    return AppException(message, this)
}

internal fun Throwable.toFirestoreException(
    fallback: String = "Something went wrong. Please try again."
): AppException {
    if (this is AppException) return this

    val message = when {
        this is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "You don't have permission to do that."

            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Check your internet connection and try again."

            FirebaseFirestoreException.Code.NOT_FOUND ->
                "That item no longer exists."

            else -> fallback
        }

        this is IOException -> "Check your internet connection and try again."
        else -> fallback
    }

    return AppException(message, this)
}
