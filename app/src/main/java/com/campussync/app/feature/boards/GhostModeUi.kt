package com.campussync.app.feature.boards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The anonymous switch used by the thread composer and the comment composer.
 * The moderator note is always shown on the create screen; the comment composer
 * only shows it once the switch is on, to keep the bottom bar short.
 */
@Composable
fun AnonymityToggle(
    anonymous: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showNote: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = anonymous,
                onCheckedChange = onToggle
            )

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Post anonymously",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Your name and username stay off this post.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showNote) {
            Text(
                text = "Moderators can see who posted if there is a serious report.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (anonymous) {
            Text(
                text = "Up to 5 anonymous posts and comments per day.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Alias with the small privacy mark. Tapping it never opens a profile. */
@Composable
fun GhostAuthorText(
    alias: String,
    isOp: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = "Anonymous",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = alias.ifBlank { "Anonymous" },
            style = style,
            color = MaterialTheme.colorScheme.primary
        )
        if (isOp) {
            Text(
                text = " · OP",
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
