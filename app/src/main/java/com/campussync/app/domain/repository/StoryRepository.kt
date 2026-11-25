package com.campussync.app.domain.repository

import com.campussync.app.data.model.Story
import com.campussync.app.data.model.StoryType
import com.campussync.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface StoryRepository {
    /** Active stories for one college, newest first, capped at a bounded number. */
    fun observeActiveStories(collegeId: String): Flow<List<Story>>

    /** Active stories by one author, oldest first, for the viewer. */
    suspend fun getAuthorStories(authorId: String, collegeId: String): List<Story>

    suspend fun createStory(author: UserProfile, type: StoryType, text: String, imageUrl: String)

    suspend fun markSeen(storyId: String)
}
