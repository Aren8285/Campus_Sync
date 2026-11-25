package com.campussync.app.data.model

/**
 * A small local draft saved after account creation so onboarding knows which
 * college and full name the student chose before their profile existed.
 */
data class SignupDraft(
    val uid: String = "",
    val fullName: String = "",
    val collegeId: String = ""
)
