package com.campussync.app.core.di

import com.campussync.app.data.repository.FirebaseAuthRepository
import com.campussync.app.data.repository.FirestoreBoardRepository
import com.campussync.app.data.repository.FirestoreChatRepository
import com.campussync.app.data.repository.FirestoreCollegeRepository
import com.campussync.app.data.repository.FirestoreFeedRepository
import com.campussync.app.data.repository.FirestoreSafetyRepository
import com.campussync.app.data.repository.FirestoreStoryRepository
import com.campussync.app.data.repository.FirestoreUserRepository
import com.campussync.app.domain.repository.AuthRepository
import com.campussync.app.domain.repository.BoardRepository
import com.campussync.app.domain.repository.ChatRepository
import com.campussync.app.domain.repository.CollegeRepository
import com.campussync.app.domain.repository.FeedRepository
import com.campussync.app.domain.repository.SafetyRepository
import com.campussync.app.domain.repository.StoryRepository
import com.campussync.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(implementation: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindCollegeRepository(
        implementation: FirestoreCollegeRepository
    ): CollegeRepository

    @Binds
    abstract fun bindUserRepository(implementation: FirestoreUserRepository): UserRepository

    @Binds
    abstract fun bindFeedRepository(implementation: FirestoreFeedRepository): FeedRepository

    @Binds
    abstract fun bindStoryRepository(implementation: FirestoreStoryRepository): StoryRepository

    @Binds
    abstract fun bindSafetyRepository(implementation: FirestoreSafetyRepository): SafetyRepository

    @Binds
    abstract fun bindChatRepository(implementation: FirestoreChatRepository): ChatRepository

    @Binds
    abstract fun bindBoardRepository(implementation: FirestoreBoardRepository): BoardRepository
}
