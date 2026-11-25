package com.campussync.app.feature.feed

import android.content.Context
import android.content.Intent
import com.campussync.app.data.model.Post

/** Opens the Android share sheet with the post text and author credit. */
internal fun sharePost(context: Context, post: Post) {
    val body = buildString {
        append(post.text)
        if (post.authorName.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("— ${post.authorName}")
            if (post.authorUsername.isNotBlank()) {
                append(" (@${post.authorUsername})")
            }
            append(" on Campus Sync")
        }
    }

    if (body.isBlank()) return

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}
