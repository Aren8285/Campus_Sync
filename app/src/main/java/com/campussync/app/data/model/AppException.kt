package com.campussync.app.data.model

/**
 * An error with a message that is safe and useful to show to the user.
 */
class AppException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
