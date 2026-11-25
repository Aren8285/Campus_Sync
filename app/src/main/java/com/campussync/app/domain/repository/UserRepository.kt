package com.campussync.app.domain.repository

import com.campussync.app.data.model.ProfileUpdate
import com.campussync.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUser(uid: String): Flow<UserProfile?>

    suspend fun getUser(uid: String): UserProfile?

    suspend fun isUsernameAvailable(username: String): Boolean

    suspend fun createProfile(profile: UserProfile)

    suspend fun updateProfile(current: UserProfile, update: ProfileUpdate)

    /** True when the signed-in student follows [uid]. */
    suspend fun isFollowing(uid: String): Boolean

    suspend fun setFollowing(uid: String, following: Boolean)
}
