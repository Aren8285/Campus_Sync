package com.campussync.app.domain.repository

import com.campussync.app.data.model.Board
import com.campussync.app.data.model.BoardComment
import com.campussync.app.data.model.BoardThread
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.RevealedAuthor
import com.campussync.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** How a board orders its threads. Pinned threads always come first. */
enum class BoardSort {
    New,
    Top
}

/**
 * Cursor for the next thread page. Both sorts order by two fields, so the cursor
 * keeps the sort-specific leading value plus the shared creation time.
 */
data class ThreadCursor(
    val pinned: Boolean,
    val score: Long,
    val createdAt: Long
)

data class BoardThreadPage(
    val threads: List<BoardThread>,
    val nextCursor: ThreadCursor?
)

interface BoardRepository {
    /** Boards for one college, alphabetically. */
    fun observeBoards(collegeId: String): Flow<List<Board>>

    suspend fun getBoard(boardId: String): Board?

    suspend fun getThreads(
        boardId: String,
        sort: BoardSort,
        cursor: ThreadCursor?,
        pageSize: Int = PAGE_SIZE
    ): BoardThreadPage

    fun observeThread(boardId: String, threadId: String): Flow<BoardThread?>

    fun observeComments(boardId: String, threadId: String): Flow<List<BoardComment>>

    suspend fun createThread(
        boardId: String,
        author: UserProfile,
        title: String,
        body: String,
        anonymous: Boolean
    ): String

    /** The caller's alias in this thread, or null before their first anonymous item. */
    suspend fun getMyAlias(threadId: String): String?

    /** Adds, removes, or flips this student's vote in one transaction. */
    suspend fun setThreadVote(boardId: String, threadId: String, upvote: Boolean)

    /** Adds, removes, or flips this student's vote in one transaction. */
    suspend fun setCommentVote(
        boardId: String,
        threadId: String,
        commentId: String,
        upvote: Boolean
    )

    suspend fun addComment(
        author: UserProfile,
        boardId: String,
        threadId: String,
        body: String,
        parentId: String?,
        anonymous: Boolean
    )

    suspend fun deleteThread(boardId: String, threadId: String)

    suspend fun deleteComment(boardId: String, threadId: String, commentId: String)

    suspend fun setPinned(boardId: String, threadId: String, pinned: Boolean)

    /** One ghost report per user, plus +1 on the item's reportCount. */
    suspend fun reportAnonymousThread(
        boardId: String,
        threadId: String,
        reason: ReportReason,
        details: String
    )

    suspend fun reportAnonymousComment(
        boardId: String,
        threadId: String,
        commentId: String,
        reason: ReportReason,
        details: String
    )

    /** Moderators only: clear the report count so an item is visible again. */
    suspend fun restoreThread(boardId: String, threadId: String)

    suspend fun restoreComment(boardId: String, threadId: String, commentId: String)

    /** Moderators only: records the audit log first, then reads the identity. */
    suspend fun revealAuthor(boardId: String, itemId: String, reason: String): RevealedAuthor

    companion object {
        const val PAGE_SIZE = 10
        const val MAX_DEPTH = 3
    }
}
