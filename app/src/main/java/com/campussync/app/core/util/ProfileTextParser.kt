package com.campussync.app.core.util

object ProfileTextParser {
    fun parseSkills(value: String): List<String> = value
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(20)
}
