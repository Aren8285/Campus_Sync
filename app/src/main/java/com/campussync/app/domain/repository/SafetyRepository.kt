package com.campussync.app.domain.repository

import com.campussync.app.data.model.ReportReason
import com.campussync.app.data.model.ReportTarget

interface SafetyRepository {
    /** UIDs the signed-in student has blocked. */
    suspend fun getBlockedUids(): List<String>

    /**
     * Checks one block with a single document read instead of loading the whole
     * block list, which is what a profile view needs.
     */
    suspend fun isBlocked(targetUid: String): Boolean

    suspend fun setBlocked(targetUid: String, blocked: Boolean)

    suspend fun report(
        target: ReportTarget,
        targetId: String,
        reportedUserId: String,
        reason: ReportReason,
        details: String
    )
}
