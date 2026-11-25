package com.campussync.app.feature.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.RemoteImage
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.Story
import com.campussync.app.data.model.StoryType

@Composable
fun StoryViewerScreen(
    onClose: () -> Unit,
    viewModel: StoryViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val viewerState = state) {
        UiState.Loading -> LoadingView(message = "Loading story...")

        is UiState.Error -> ErrorView(
            message = viewerState.message,
            onRetry = viewModel::load
        )

        UiState.Empty -> EmptyView(
            message = "This story has expired or is no longer available."
        )

        is UiState.Success -> StoryViewer(
            content = viewerState.data,
            onSeen = viewModel::markSeen,
            onClose = onClose
        )
    }
}

@Composable
private fun StoryViewer(
    content: StoryViewerContent,
    onSeen: (String) -> Unit,
    onClose: () -> Unit
) {
    val stories = content.stories
    if (stories.isEmpty()) return

    var index by remember(stories) { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val current = stories.getOrNull(index) ?: return

    LaunchedEffect(index, stories) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = STORY_DURATION_MS, easing = LinearEasing)
        )
        if (progress.value >= 1f) {
            if (index < stories.lastIndex) index += 1 else onClose()
        }
    }

    LaunchedEffect(index) {
        val story = stories.getOrNull(index) ?: return@LaunchedEffect
        if (story.authorId != content.currentUid) {
            onSeen(story.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            current.type == StoryType.Image && current.imageUrl.isNotBlank() -> {
                RemoteImage(
                    url = current.imageUrl,
                    contentDescription = "Story image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (current.text.isNotBlank()) {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp)
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = current.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (index > 0) index -= 1 else onClose()
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        if (index < stories.lastIndex) index += 1 else onClose()
                    }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.forEachIndexed { itemIndex, _ ->
                    val fraction = when {
                        itemIndex < index -> 1f
                        itemIndex == index -> progress.value
                        else -> 0f
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialsAvatar(name = current.authorName, size = 34.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = current.authorName.ifBlank { "Student" },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = formatRelativeTime(current.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close story"
                    )
                }
            }
        }

        if (current.authorId == content.currentUid) {
            Text(
                text = "Seen by ${current.seenBy.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

    }
}

private const val STORY_DURATION_MS = 5_000
