package com.campussync.app.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A student's profile photo when they set a public https link.
 *
 * Initials are the fallback while the photo loads, when it fails, and when no
 * link is set, so the profile header never shows an empty hole.
 */
@Composable
fun ProfileAvatar(
    name: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val trimmed = avatarUrl.trim()
    if (!trimmed.startsWith("https://")) {
        InitialsAvatar(name = name, modifier = modifier, size = size)
        return
    }

    RemoteImage(
        url = trimmed,
        contentDescription = "Profile photo of ${name.ifBlank { "student" }}",
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        maxDimension = 512,
        fallback = { InitialsAvatar(name = name, size = size) }
    )
}
