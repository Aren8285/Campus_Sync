package com.campussync.app.data.repository

import com.campussync.app.data.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot

/** Maps a `/users/{uid}` document to the app's profile model. */
internal fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    uid = id,
    name = getString("name").orEmpty(),
    username = getString("username").orEmpty(),
    collegeId = getString("collegeId").orEmpty(),
    department = getString("department").orEmpty(),
    year = getString("year").orEmpty(),
    graduationYear = getLong("graduationYear")?.toInt() ?: 0,
    bio = getString("bio").orEmpty(),
    avatarUrl = getString("avatarUrl").orEmpty(),
    skills = (get("skills") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    interests = (get("interests") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    role = getString("role") ?: "student",
    badges = (get("badges") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    karma = getLong("karma") ?: 0L,
    createdAt = getTimestamp("createdAt")?.toDate()?.time
)
