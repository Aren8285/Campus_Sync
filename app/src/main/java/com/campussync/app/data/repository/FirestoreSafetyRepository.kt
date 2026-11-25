package com.campussync.app.data.repository

import com.campussync.app.data.model.AppException
import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget
import com.campussync.app.domain.repository.SafetyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class FirestoreSafetyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SafetyRepository {

    private val blocks = firestore.collection(BLOCKS)
    private val reports = firestore.collection(REPORTS)

    override suspend fun getBlockedUids(): List<String> {
        val uid = requireUid()
        return try {
            blocks
                .whereEqualTo("blockerId", uid)
                .limit(BLOCK_LIMIT.toLong())
                .get()
                .awaitResult()
                .documents
                .mapNotNull { it.getString("blockedId") }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't load your blocked students.")
        }
    }

    override suspend fun isBlocked(targetUid: String): Boolean {
        val uid = requireUid()
        if (targetUid.isBlank() || targetUid == uid) return false

        return try {
            blocks.document(blockId(uid, targetUid)).get().awaitResult().exists()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw error.toFirestoreException("We couldn't check this student.")
        }
    }

    override suspend fun setBlocked(targetUid: String, blocked: Boolean) {
        val uid = requireUid()
        if (targetUid.isBlank()) throw AppException("This student could not be found.")
        if (targetUid == uid) throw AppException("You can't block yourself.")

        val reference = blocks.document(blockId(uid, targetUid))

        try {
            if (blocked) {
                reference.set(
                    mapOf(
                        "blockerId" to uid,
                        "blockedId" to targetUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).awaitCompletion()
            } else {
                reference.delete().awaitCompletion()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException(
                if (blocked) {
                    "We couldn't block this student. Please try again."
                } else {
                    "We couldn't unblock this student. Please try again."
                }
            )
        }
    }

    override suspend fun report(
        target: ReportTarget,
        targetId: String,
        reportedUserId: String,
        reason: ReportReason,
        details: String
    ) {
        val uid = requireUid()
        if (targetId.isBlank()) throw AppException("This item could not be found.")
        if (reportedUserId.isBlank()) throw AppException("This student could not be found.")

        try {
            reports.add(
                mapOf(
                    "reporterId" to uid,
                    "reportedUserId" to reportedUserId,
                    "targetType" to target.wireValue,
                    "targetId" to targetId,
                    "reason" to reason.wireValue,
                    "details" to details.trim().take(MAX_DETAILS_LENGTH),
                    "status" to STATUS_OPEN,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is AppException) throw error
            throw error.toFirestoreException("We couldn't send your report. Please try again.")
        }
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw AppException("You're signed out. Please log in again.")

    private fun blockId(blockerId: String, blockedId: String): String =
        "${blockerId}_$blockedId"

    private companion object {
        const val BLOCKS = "blocks"
        const val REPORTS = "reports"
        const val STATUS_OPEN = "open"
        const val MAX_DETAILS_LENGTH = 500
        const val BLOCK_LIMIT = 200
    }
}
