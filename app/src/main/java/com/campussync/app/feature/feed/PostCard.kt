package com.campussync.app.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.RemoteImage
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.Post
import com.campussync.app.data.model.isLikedBy

@Composable
fun PostCard(
    post: Post,
    currentUid: String,
    onOpen: () -> Unit,
    onAuthorClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOwn = currentUid.isNotBlank() && post.authorId == currentUid
    val liked = post.isLikedBy(currentUid)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onAuthorClick),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InitialsAvatar(name = post.authorName, size = 44.dp)

                        Column {
                            Text(
                                text = post.authorName.ifBlank { "Student" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (post.authorUsername.isNotBlank()) {
                                        append("@${post.authorUsername} · ")
                                    }
                                    append(formatRelativeTime(post.createdAt))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    PostMenu(
                        isOwn = isOwn,
                        onDelete = onDelete,
                        onBlock = onBlock,
                        onReport = onReport
                    )
                }

                if (post.text.isNotBlank()) {
                    Text(
                        text = post.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (post.imageUrls.isNotEmpty()) {
                    PostImages(urls = post.imageUrls)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onLike) {
                    Icon(
                        imageVector = if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        modifier = Modifier.size(20.dp),
                        tint = if (liked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = post.likedBy.size.toString(),
                        color = if (liked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                TextButton(onClick = onComment) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = post.commentCount.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share post",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMenu(
    isOwn: Boolean,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Post options"
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isOwn) {
                DropdownMenuItem(
                    text = { Text(text = "Delete post") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(text = "Report post") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Flag, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onReport()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = "Block author") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Block, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onBlock()
                    }
                )
            }
        }
    }
}

@Composable
private fun PostImages(urls: List<String>) {
    val shape = RoundedCornerShape(12.dp)

    if (urls.size == 1) {
        RemoteImage(
            url = urls.first(),
            contentDescription = "Post image",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        urls.take(4).chunked(2).forEach { rowUrls ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowUrls.forEach { url ->
                    RemoteImage(
                        url = url,
                        contentDescription = "Post image",
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp)
                            .clip(shape)
                    )
                }
                if (rowUrls.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
