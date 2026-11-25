package com.campussync.app.core.util

object EmailDomainValidator {
    fun normalizeDomain(value: String): String =
        value.trim().lowercase().removePrefix("@")

    fun hasValidDomain(email: String, allowedDomains: List<String>): Boolean {
        val domain = email.trim().substringAfterLast("@", "").lowercase()
        if (domain.isBlank()) return false

        return allowedDomains
            .map(::normalizeDomain)
            .filter { it.isNotBlank() }
            .any { it == domain }
    }
}
