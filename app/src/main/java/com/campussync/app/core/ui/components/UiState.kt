package com.campussync.app.core.ui.components

/**
 * Represents the state of data displayed on a screen.
 *
 * Every screen should handle all four states so users always see something meaningful.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : UiState<Nothing>

    data object Empty : UiState<Nothing>
}
