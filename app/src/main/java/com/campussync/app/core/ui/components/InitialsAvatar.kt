package com.campussync.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    val initialsStyle = when {
        size >= 72.dp -> MaterialTheme.typography.headlineSmall
        size >= 44.dp -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.labelLarge
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsOf(name),
            style = initialsStyle,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
