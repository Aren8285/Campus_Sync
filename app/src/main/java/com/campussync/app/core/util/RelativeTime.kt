package com.campussync.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats a Firestore timestamp for feed rows, for example "just now" or "3h". */
fun formatRelativeTime(millis: Long?, now: Long = System.currentTimeMillis()): String {
    if (millis == null || millis <= 0L) return "just now"

    val diff = (now - millis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m"
        diff < 86_400_000L -> "${diff / 3_600_000L}h"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
    }
}
