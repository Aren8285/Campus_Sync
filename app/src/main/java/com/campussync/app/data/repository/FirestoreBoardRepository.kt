package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.Board
import com.campussync.app.data.model.BoardComment
import com.campussync.app.data.model.BoardThread
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.RevealedAuthor
import com.campussync.app.data.model.UserProfile
import com.campussync.app.domain.repository.BoardRepository
import com.campussync.app.domain.repository.BoardSort
import com.campussync.app.domain.repository.BoardThreadPage
import com.campussync.app.domain.repository.ThreadCursor
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreBoardRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : BoardRepository {

    private val boards = firestore.collection(BOARDS)
    private val users = firestore.collection(USERS)
    private val ghostIdentities = firestore.collection(GHOST_IDENTITIES)
    private val ghostAliases = firestore.collection(GHOST_ALIASES)
    private val ghostLimits = firestore.collection(GHOST_LIMITS)
    private val revealLogs = firestore.collection(REVEAL_LOGS)

    private var cachedCollege: Pair<String, String>? = null

    override fun observeBoards(collegeId: String): Flow<List<Board>> = callbackFlow {
        val registration = boards
            .whereEqualTo("collegeId", collegeId)
            .orderBy("name", Query.Direction.ASCENDING)
            .limit(BOARD_LIMIT.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toFirestoreException("We couldn't load the message boards."))
                    return@addSnapshotListener
                }

                trySend(snapshot?.documents?.mapNotNull { it.toBoard() }.orEmpty())
            }

        awaitClose { registration.remove() }
    }

    override suspend fun getBoard(boardId: String): Board? {
        if (boardId.isBlank()) return null

        return try {
            boards.document(boardId)
                .get()
                .awaitResult()
                .takeIf { it.exists() }
                ?.toBoard()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load this board.")
        }
    }

    override suspend fun getThreads(
        boardId: String,
        sort: BoardSort,
        cursor: ThreadCursor?,
        pageSize: Int
    ): BoardThreadPage {
        val uid = requireUid()
        val collegeId = collegeIdOf(uid)

        try {
            var query = threads(boardId).whereEqualTo("collegeId", collegeId)

            query = when (sort) {
                BoardSort.New -> query
                    .orderBy("pinned", Query.Direction.DESCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)

                BoardSort.Top -> query
                    .orderBy("score", Query.Direction.DESCENDING)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }

            if (cursor != null) {
                query = when (sort) {
                    BoardSort.New -> query.startAfter(
                        cursor.pinned,
                        Timestamp(Date(cursor.createdAt))
                    )

                    BoardSort.Top -> query.startAfter(
                        cursor.score,
                        Timestamp(Date(cursor.createdAt))
                    )
                }
            }

            val snapshot = query.limit(pageSize.toLong()).get().awaitResult()
            val mapped = snapshot.documents.mapNotNull { it.toThread(boardId) }
            val last = mapped.lastOrNull()
            val createdAt = last?.createdAt
            val nextCursor = if (mapped.size >= pageSize && createdAt != null) {
                ThreadCursor(
                    pinned = last.pinned,
                    score = last.score,
                    createdAt = createdAt
                )
            } else {
                null
            }

            return BoardThreadPage(threads = mapped, nextCursor = nextCursor)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load these threads.")
        }
    }

    override fun observeThread(boardId: String, threadId: String): Flow<BoardThread?> =
        callbackFlow {
            val registration = threads(boardId)
                .document(threadId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.toFirestoreException("We couldn't load this thread."))
                        return@addSnapshotListener
                    }

                    trySend(snapshot?.takeIf { it.exists() }?.toThread(boardId))
                }

            awaitClose { registration.remove() }
        }

    override fun observeComments(boardId: String, threadId: String): Flow<List<BoardComment>> =
        callbackFlow {
            val registration = threads(boardId)
                .document(threadId)
                .collection(COMMENTS)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(COMMENT_LIMIT.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error.toFirestoreException("We couldn't load the comments."))
                        return@addSnapshotListener
                    }

                    trySend(snapshot?.documents?.mapNotNull { it.toComment() }.orEmpty())
                }

            awaitClose { registration.remove() }
        }

    override suspend fun createThread(
        boardId: String,
        author: UserProfile,
        title: String,
        body: String,
        anonymous: Boolean
    ): String {
        val uid = requireUid()
        if (author.uid != uid) {
            throw AppException("Your profile doesn't match this account. Please log in again.")
        }

        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        if (cleanTitle.isBlank()) throw AppException("Give your thread a title.")
        if (cleanTitle.length > MAX_TITLE_LENGTH) {
            throw AppException("Titles can be up to $MAX_TITLE_LENGTH characters.")
        }
        if (cleanBody.length > MAX_BODY_LENGTH) {
            throw AppException("Threads can be up to $MAX_BODY_LENGTH characters.")
        }

        try {
            val reference = threads(boardId).document()
            val common = mapOf(
                "boardId" to boardId,
                "collegeId" to author.collegeId,
                "title" to cleanTitle,
                "body" to cleanBody,
                "score" to 0L,
                "upvotedBy" to emptyList<String>(),
                "downvotedBy" to emptyList<String>(),
                "commentCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "pinned" to false,
                "reportCount" to 0L
            )

            if (anonymous) {
                val alias = generateAlias()
                val batch = firestore.batch()

                batch.set(
                    reference,
                    common + mapOf(
                        "isAnonymous" to true,
                        "anonAlias" to alias
                    )
                )
                batch.set(
                    ghostIdentities.document(reference.id),
                    ghostIdentity(
                        uid = uid,
                        collegeId = author.collegeId,
                        boardId = boardId,
                        threadId = reference.id,
                        type = TYPE_THREAD
                    )
                )
                batch.set(
                    ghostAliases.document(aliasDocumentId(reference.id, uid)),
                    ghostAlias(uid, reference.id, alias)
                )
                addGhostLimitWrite(batch, uid)
                batch.commit().awaitCompletion()
            } else {
                reference.set(
                    common + mapOf(
                        "authorId" to uid,
                        "authorName" to author.name,
                        "isAnonymous" to false
                    )
                ).awaitResult()
            }

            return reference.id
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't publish your thread. Please try again.")
        }
    }

    override suspend fun setThreadVote(boardId: String, threadId: String, upvote: Boolean) {
        val uid = requireUid()

        try {
            firestore.runTransaction { transaction ->
                val reference = threads(boardId).document(threadId)
                val snapshot = transaction.get(reference)
                if (!snapshot.exists()) {
                    throw AppException("This thread is no longer available.")
                }

                val (up, down) = applyVote(
                    uid = uid,
                    upvotedBy = snapshot.stringList("upvotedBy"),
                    downvotedBy = snapshot.stringList("downvotedBy"),
                    upvote = upvote
                )

                transaction.update(
                    reference,
                    mapOf(
                        "upvotedBy" to up,
                        "downvotedBy" to down,
                        "score" to (up.size - down.size).toLong()
                    )
                )
                Unit
            }.awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't save your vote. Please try again.")
        }
    }

    override suspend fun setCommentVote(
        boardId: String,
        threadId: String,
        commentId: String,
        upvote: Boolean
    ) {
        val uid = requireUid()

        try {
            firestore.runTransaction { transaction ->
                val reference = threads(boardId)
                    .document(threadId)
                    .collection(COMMENTS)
                    .document(commentId)

                val snapshot = transaction.get(reference)
                if (!snapshot.exists()) {
                    throw AppException("This comment is no longer available.")
                }

                val (up, down) = applyVote(
                    uid = uid,
                    upvotedBy = snapshot.stringList("upvotedBy"),
                    downvotedBy = snapshot.stringList("downvotedBy"),
                    upvote = upvote
                )

                transaction.update(
                    reference,
                    mapOf(
                        "upvotedBy" to up,
                        "downvotedBy" to down,
                        "score" to (up.size - down.size).toLong()
                    )
                )
                Unit
            }.awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't save your vote. Please try again.")
        }
    }

    override suspend fun getMyAlias(threadId: String): String? {
        val uid = requireUid()

        return try {
            ghostAliases.document(aliasDocumentId(threadId, uid))
                .get()
                .awaitResult()
                .getString("alias")
                ?.takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
    }

    override suspend fun addComment(
        author: UserProfile,
        boardId: String,
        threadId: String,
        body: String,
        parentId: String?,
        anonymous: Boolean
    ) {
        val uid = requireUid()
        if (author.uid != uid) {
            throw AppException("Your profile doesn't match this account. Please log in again.")
        }

        val cleanBody = body.trim()
        if (cleanBody.isBlank()) throw AppException("Write a comment first.")
        if (cleanBody.length > MAX_COMMENT_LENGTH) {
            throw AppException("Comments can be up to $MAX_COMMENT_LENGTH characters.")
        }

        val comments = threads(boardId).document(threadId).collection(COMMENTS)
        val cleanParentId = parentId?.takeIf { it.isNotBlank() }

        try {
            val depth = if (cleanParentId == null) {
                0
            } else {
                val parent = comments.document(cleanParentId).get().awaitResult()
                if (!parent.exists()) {
                    throw AppException("That comment is no longer available.")
                }
                (parent.getLong("depth")?.toInt() ?: 0) + 1
            }

            if (depth > BoardRepository.MAX_DEPTH) {
                throw AppException("Replies can be nested up to ${BoardRepository.MAX_DEPTH} levels.")
            }

            val reference = comments.document()
            val batch = firestore.batch()
            val common = mapOf(
                "body" to cleanBody,
                "parentId" to cleanParentId,
                "depth" to depth.toLong(),
                "score" to 0L,
                "upvotedBy" to emptyList<String>(),
                "downvotedBy" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "reportCount" to 0L
            )

            if (anonymous) {
                val alias = resolveAlias(batch, threadId, uid)
                batch.set(
                    reference,
                    common + mapOf(
                        "isAnonymous" to true,
                        "anonAlias" to alias
                    )
                )
                batch.set(
                    ghostIdentities.document(reference.id),
                    ghostIdentity(
                        uid = uid,
                        collegeId = author.collegeId,
                        boardId = boardId,
                        threadId = threadId,
                        type = TYPE_COMMENT
                    )
                )
                addGhostLimitWrite(batch, uid)
            } else {
                batch.set(
                    reference,
                    common + mapOf(
                        "authorId" to uid,
                        "authorName" to author.name,
                        "isAnonymous" to false
                    )
                )
            }

            batch.update(
                threads(boardId).document(threadId),
                "commentCount",
                FieldValue.increment(1)
            )
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't add your comment. Please try again.")
        }
    }

    override suspend fun deleteThread(boardId: String, threadId: String) {
        requireUid()

        try {
            val batch = firestore.batch()
            batch.delete(threads(boardId).document(threadId))
            // A no-op for normal threads; it removes the private identity with
            // the public document for anonymous ones.
            batch.delete(ghostIdentities.document(threadId))
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't delete this thread.")
        }
    }

    override suspend fun deleteComment(boardId: String, threadId: String, commentId: String) {
        requireUid()

        val comments = threads(boardId).document(threadId).collection(COMMENTS)

        try {
            // Firestore has no cascade, so removing a comment with replies would
            // orphan them. Ask the author to remove the replies first.
            val replies = comments
                .whereEqualTo("parentId", commentId)
                .limit(1)
                .get()
                .awaitResult()

            if (!replies.isEmpty) {
                throw AppException("Delete the replies to this comment first.")
            }

            val batch = firestore.batch()
            batch.delete(comments.document(commentId))
            batch.delete(ghostIdentities.document(commentId))
            batch.update(
                threads(boardId).document(threadId),
                "commentCount",
                FieldValue.increment(-1)
            )
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't delete this comment.")
        }
    }

    override suspend fun setPinned(boardId: String, threadId: String, pinned: Boolean) {
        requireUid()

        try {
            threads(boardId).document(threadId)
                .update("pinned", pinned)
                .awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't update the pin.")
        }
    }

    override suspend fun reportAnonymousThread(
        boardId: String,
        threadId: String,
        reason: ReportReason,
        details: String
    ) {
        val uid = requireUid()

        try {
            val batch = firestore.batch()
            batch.set(
                threads(boardId).document(threadId).collection(GHOST_REPORTS).document(uid),
                ghostReport(uid, threadId, TYPE_THREAD, reason, details)
            )
            batch.update(
                threads(boardId).document(threadId),
                "reportCount",
                FieldValue.increment(1)
            )
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            if (error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
            ) {
                throw AppException("You already reported this item.")
            }
            throw error.toFirestoreException("We couldn't send your report. Please try again.")
        }
    }

    override suspend fun reportAnonymousComment(
        boardId: String,
        threadId: String,
        commentId: String,
        reason: ReportReason,
        details: String
    ) {
        val uid = requireUid()
        val comment = threads(boardId)
            .document(threadId)
            .collection(COMMENTS)
            .document(commentId)

        try {
            val batch = firestore.batch()
            batch.set(
                threads(boardId).document(threadId)
                    .collection(GHOST_REPORTS)
                    .document(uid),
                ghostReport(uid, commentId, TYPE_COMMENT, reason, details)
            )
            batch.update(comment, "reportCount", FieldValue.increment(1))
            batch.commit().awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            if (error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
            ) {
                throw AppException("You already reported this item.")
            }
            throw error.toFirestoreException("We couldn't send your report. Please try again.")
        }
    }

    override suspend fun restoreThread(boardId: String, threadId: String) {
        requireUid()

        try {
            threads(boardId).document(threadId)
                .update("reportCount", 0L)
                .awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't restore this thread.")
        }
    }

    override suspend fun restoreComment(boardId: String, threadId: String, commentId: String) {
        requireUid()

        try {
            threads(boardId)
                .document(threadId)
                .collection(COMMENTS)
                .document(commentId)
                .update("reportCount", 0L)
                .awaitCompletion()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't restore this comment.")
        }
    }

    override suspend fun revealAuthor(
        boardId: String,
        itemId: String,
        reason: String
    ): RevealedAuthor {
        val uid = requireUid()
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            throw AppException("Give a reason before revealing the author.")
        }
        if (cleanReason.length > 500) {
            throw AppException("The reason can be up to 500 characters.")
        }

        try {
            // The audit record is written first: if it fails, nothing is revealed.
            revealLogs.add(
                mapOf(
                    "moderatorUid" to uid,
                    "boardId" to boardId,
                    "itemId" to itemId,
                    "reason" to cleanReason,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()

            val identity = ghostIdentities.document(itemId).get().awaitResult()
            if (!identity.exists()) {
                throw AppException("The anonymous author record is missing.")
            }

            val authorUid = identity.getString("uid").orEmpty()
            if (authorUid.isBlank()) {
                throw AppException("The anonymous author record is incomplete.")
            }

            val user = users.document(authorUid).get().awaitResult()
            if (!user.exists()) {
                throw AppException("This author's profile is no longer available.")
            }

            return RevealedAuthor(
                name = user.getString("name").orEmpty(),
                username = user.getString("username").orEmpty()
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't reveal this author.")
        }
    }

    private fun threads(boardId: String) =
        boards.document(boardId).collection(THREADS)

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private suspend fun collegeIdOf(uid: String): String {
        cachedCollege?.let { (cachedUid, collegeId) ->
            if (cachedUid == uid) return collegeId
        }

        val snapshot = try {
            users.document(uid).get().awaitResult()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load your college.")
        }

        val collegeId = snapshot.getString("collegeId").orEmpty()
        if (collegeId.isBlank()) {
            throw AppException("Finish your profile before opening the message boards.")
        }

        cachedCollege = uid to collegeId
        return collegeId
    }

    /**
     * Applies one student's vote without touching anyone else's:
     * same vote again clears it, the opposite vote flips it.
     */
    private fun applyVote(
        uid: String,
        upvotedBy: List<String>,
        downvotedBy: List<String>,
        upvote: Boolean
    ): Pair<List<String>, List<String>> {
        val hasUpvoted = uid in upvotedBy
        val hasDownvoted = uid in downvotedBy

        return if (upvote) {
            when {
                hasUpvoted -> (upvotedBy - uid) to downvotedBy
                else -> (upvotedBy + uid) to (downvotedBy - uid)
            }
        } else {
            when {
                hasDownvoted -> upvotedBy to (downvotedBy - uid)
                else -> (upvotedBy - uid) to (downvotedBy + uid)
            }
        }
    }

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private fun DocumentSnapshot.toBoard(): Board? {
        if (!exists()) return null

        return Board(
            id = id,
            collegeId = getString("collegeId").orEmpty(),
            name = getString("name").orEmpty(),
            description = getString("description").orEmpty(),
            moderatorIds = stringList("moderatorIds"),
            createdAt = getTimestamp("createdAt")?.toDate()?.time
        )
    }

    private fun DocumentSnapshot.toThread(boardId: String): BoardThread? {
        if (!exists()) return null

        return BoardThread(
            id = id,
            boardId = getString("boardId").orEmpty().ifBlank { boardId },
            collegeId = getString("collegeId").orEmpty(),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            title = getString("title").orEmpty(),
            body = getString("body").orEmpty(),
            score = getLong("score") ?: 0L,
            upvotedBy = stringList("upvotedBy"),
            downvotedBy = stringList("downvotedBy"),
            commentCount = getLong("commentCount") ?: 0L,
            createdAt = getTimestamp("createdAt")?.toDate()?.time,
            pinned = getBoolean("pinned") ?: false,
            isAnonymous = getBoolean("isAnonymous") ?: false,
            anonAlias = getString("anonAlias").orEmpty(),
            reportCount = getLong("reportCount") ?: 0L
        )
    }

    private fun DocumentSnapshot.toComment(): BoardComment? {
        if (!exists()) return null

        return BoardComment(
            id = id,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            body = getString("body").orEmpty(),
            parentId = getString("parentId"),
            depth = (getLong("depth") ?: 0L).toInt(),
            score = getLong("score") ?: 0L,
            upvotedBy = stringList("upvotedBy"),
            downvotedBy = stringList("downvotedBy"),
            createdAt = getTimestamp("createdAt")?.toDate()?.time,
            isAnonymous = getBoolean("isAnonymous") ?: false,
            anonAlias = getString("anonAlias").orEmpty(),
            reportCount = getLong("reportCount") ?: 0L
        )
    }

    private suspend fun addGhostLimitWrite(batch: WriteBatch, uid: String) {
        val date = ghostDate()
        val reference = ghostLimits.document(limitDocumentId(uid, date))
        val snapshot = reference.get().awaitResult()
        val count = snapshot.getLong("count") ?: 0L

        if (snapshot.exists() && count >= MAX_GHOST_PER_DAY) {
            throw AppException(
                "You've reached today's limit of $MAX_GHOST_PER_DAY anonymous posts and comments."
            )
        }

        if (snapshot.exists()) {
            batch.update(
                reference,
                mapOf(
                    "count" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                reference,
                mapOf(
                    "uid" to uid,
                    "date" to date,
                    "count" to 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    /** Reuses the caller's alias in this thread, creating one on first use. */
    private suspend fun resolveAlias(batch: WriteBatch, threadId: String, uid: String): String {
        val reference = ghostAliases.document(aliasDocumentId(threadId, uid))
        val snapshot = reference.get().awaitResult()
        val existing = snapshot.getString("alias")?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val alias = generateAlias()
        batch.set(reference, ghostAlias(uid, threadId, alias))
        return alias
    }

    private fun ghostIdentity(
        uid: String,
        collegeId: String,
        boardId: String,
        threadId: String,
        type: String
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "collegeId" to collegeId,
        "boardId" to boardId,
        "threadId" to threadId,
        "type" to type,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun ghostAlias(uid: String, threadId: String, alias: String): Map<String, Any> = mapOf(
        "uid" to uid,
        "threadId" to threadId,
        "alias" to alias,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun ghostReport(
        uid: String,
        itemId: String,
        itemType: String,
        reason: com.campussync.app.data.model.ReportReason,
        details: String
    ): Map<String, Any> = mapOf(
        "reporterId" to uid,
        "itemId" to itemId,
        "itemType" to itemType,
        "reason" to reason.wireValue,
        "details" to details.trim().take(500),
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun generateAlias(): String =
        "${ALIAS_ADJECTIVES.random()} ${ALIAS_ANIMALS.random()} ${Random.nextInt(10, 100)}"

    /** UTC keeps the daily bucket stable across device time zones. */
    private fun ghostDate(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun aliasDocumentId(threadId: String, uid: String): String = "${threadId}_$uid"

    private fun limitDocumentId(uid: String, date: String): String = "${uid}_$date"

    private companion object {
        const val BOARDS = "boards"
        const val THREADS = "threads"
        const val COMMENTS = "comments"
        const val USERS = "users"
        const val GHOST_IDENTITIES = "ghostIdentities"
        const val GHOST_ALIASES = "ghostAliases"
        const val GHOST_LIMITS = "ghostLimits"
        const val GHOST_REPORTS = "ghostReports"
        const val REVEAL_LOGS = "revealLogs"
        const val TYPE_THREAD = "thread"
        const val TYPE_COMMENT = "comment"
        const val BOARD_LIMIT = 50
        const val COMMENT_LIMIT = 200
        const val MAX_TITLE_LENGTH = 120
        const val MAX_BODY_LENGTH = 2000
        const val MAX_COMMENT_LENGTH = 500
        const val MAX_GHOST_PER_DAY = 5L

        val ALIAS_ADJECTIVES = listOf(
            "Quiet", "Brave", "Sleepy", "Curious", "Clever", "Sunny", "Grumpy",
            "Swift", "Lucky", "Cozy", "Bold", "Gentle", "Witty", "Calm", "Nimble"
        )
        val ALIAS_ANIMALS = listOf(
            "Panda", "Otter", "Falcon", "Tiger", "Koala", "Penguin", "Fox",
            "Dolphin", "Hedgehog", "Lynx", "Sparrow", "Turtle", "Wolf", "Crane"
        )
    }
}
