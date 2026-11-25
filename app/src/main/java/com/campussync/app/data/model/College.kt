package com.campussync.app.data.model

/**
 * A college that students can sign up with.
 *
 * Firestore path: /colleges/{collegeId}
 */
data class College(
    val id: String = "",
    val name: String = "",
    val emailDomains: List<String> = emptyList(),
    val createdAt: Long? = null
)
