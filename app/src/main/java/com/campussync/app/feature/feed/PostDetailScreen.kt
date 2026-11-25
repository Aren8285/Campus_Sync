package com.campussync.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ReportDialog
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.Comment
import com.campussync.app.data.model.ReportTarget

@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commentText by viewModel.commentText.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var reportTarget by remember { mutableStateOf<Comment?>(null) }
    var postReportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(title = "Post", onBack = onBack)

            when (val detailState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading post..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = detailState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "This post is no longer available."
                )

                is UiState.Success -> {
                    val content = detailState.data
                    val currentUid = content.currentProfile?.uid.orEmpty()

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "post") {
                            PostCard(
                                post = content.post,
                                currentUid = currentUid,
                                onOpen = {},
                                onAuthorClick = { onOpenProfile(content.post.authorId) },
                                onLike = { viewModel.toggleLike(content.post) },
                                onComment = {},
                                onShare = { sharePost(context, content.post) },
                                onDelete = { viewModel.deletePost(content.post.id) },
                                onBlock = {
                                    viewModel.blockUser(
                                        targetUid = content.post.authorId,
                                        name = content.post.authorName
                                    )
                                },
                                onReport = { postReportOpen = true },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        item(key = "comments-header") {
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            Text(
                                text = "Comments (${content.post.commentCount})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        val topLevel = content.comments.filter { !it.isReply }
                        val replies = content.comments
                            .filter { it.isReply }
                            .groupBy { it.parentId }

                        if (topLevel.isEmpty()) {
                            item(key = "no-comments") {
                                Text(
                                    text = "No comments yet. Be the first to reply.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        items(topLevel, key = { "c-${it.id}" }) { comment ->
                            CommentRow(
                                comment = comment,
                                currentUid = currentUid,
                                isReply = false,
                                onReply = { viewModel.replyTo(comment) },
                                onAuthorClick = { onOpenProfile(comment.authorId) },
                                onBlock = {
                                    viewModel.blockUser(comment.authorId, comment.authorName)
                                },
                                onReport = { reportTarget = comment },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            replies[comment.id].orEmpty().forEach { reply ->
                                CommentRow(
                                    comment = reply,
                                    currentUid = currentUid,
                                    isReply = true,
                                    onReply = { viewModel.replyTo(comment) },
                                    onAuthorClick = { onOpenProfile(reply.authorId) },
                                    onBlock = {
                                        viewModel.blockUser(reply.authorId, reply.authorName)
                                    },
                                    onReport = { reportTarget = reply },
                                    modifier = Modifier.padding(start = 44.dp, end = 16.dp)
                                )
                            }
                        }

                        item(key = "bottom-space") {
                            Box(modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }

                    Surface(tonalElevation = 2.dp) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val target = replyTarget
                            if (target != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Replying to @${target.authorUsername}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = viewModel::cancelReply) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Cancel reply"
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = viewModel::onCommentTextChange,
                                    placeholder = { Text(text = "Add a comment...") },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4
                                )
                                IconButton(
                                    onClick = viewModel::sendComment,
                                    enabled = commentText.isNotBlank() && !isSending,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send comment"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = reportTarget
    if (target != null) {
        ReportDialog(
            title = "Report comment",
            onDismiss = { reportTarget = null },
            onSubmit = { reason, details ->
                viewModel.report(
                    target = ReportTarget.Comment,
                    targetId = target.id,
                    reportedUserId = target.authorId,
                    reason = reason,
                    details = details
                )
                reportTarget = null
            }
        )
    }

    val detail = (state as? UiState.Success)?.data
    if (postReportOpen && detail != null) {
        ReportDialog(
            title = "Report post",
            onDismiss = { postReportOpen = false },
            onSubmit = { reason, details ->
                viewModel.report(
                    target = ReportTarget.Post,
                    targetId = detail.post.id,
                    reportedUserId = detail.post.authorId,
                    reason = reason,
                    details = details
                )
                postReportOpen = false
            }
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    currentUid: String,
    isReply: Boolean,
    onReply: () -> Unit,
    onAuthorClick: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isOwn = currentUid.isNotBlank() && comment.authorId == currentUid

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InitialsAvatar(
            name = comment.authorName,
            size = if (isReply) 28.dp else 34.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onAuthorClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = comment.authorName.ifBlank { "Student" },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = " · ${formatRelativeTime(comment.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReply) {
                    Text(text = "Reply", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(32.dp)
            ) {
                Text(text = "⋯", style = MaterialTheme.typography.titleMedium)
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (!isOwn) {
                    DropdownMenuItem(
                        text = { Text(text = "Report comment") },
                        onClick = {
                            menuOpen = false
                            onReport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = "Block author") },
                        onClick = {
                            menuOpen = false
                            onBlock()
                        }
                    )
                }
            }
        }
    }
}
