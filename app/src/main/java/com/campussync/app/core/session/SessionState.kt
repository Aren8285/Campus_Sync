package com.campussync.app.core.session

/**
 * The app has one top-level state. It decides which part of the app is visible,
 * which is how the main tabs stay blocked until the email is verified and
 * onboarding is finished.
 */
sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class Unverified(val email: String) : SessionState

    data class NeedsOnboarding(val uid: String) : SessionState

    data class Ready(val uid: String) : SessionState

    data class Error(val message: String) : SessionState
}
