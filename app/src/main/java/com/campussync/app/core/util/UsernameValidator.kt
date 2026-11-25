package com.campussync.app.core.util

object UsernameValidator {
    private val allowed = Regex("^[a-z0-9_]{3,20}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    fun validationError(raw: String): String? {
        val username = normalize(raw)
        return when {
            username.isBlank() -> "Choose a username."
            !allowed.matches(username) ->
                "Use 3-20 lowercase letters, numbers, or underscores."
            else -> null
        }
    }
}
