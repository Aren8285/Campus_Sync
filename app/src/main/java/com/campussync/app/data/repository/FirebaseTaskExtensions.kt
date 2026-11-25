package com.campussync.app.data.repository

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener

            val exception = task.exception
            if (exception != null) {
                continuation.resumeWithException(exception)
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            }
        }
    }

internal suspend fun Task<*>.awaitCompletion() {
    suspendCancellableCoroutine<Unit> { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener

            val exception = task.exception
            if (exception != null) {
                continuation.resumeWithException(exception)
            } else {
                continuation.resume(Unit)
            }
        }
    }
}
