package com.campussync.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.ChatType
import com.campussync.app.data.model.hasUnreadFor
import com.campussync.app.data.model.isMutedBy
import com.campussync.app.data.model.previewText
import com.campussync.app.data.model.titleFor

@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(
                title = "Messages",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "New chat"
                        )
                    }
                }
            )

            when (val listState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading your messages..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = listState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EmptyView(
                        modifier = Modifier.weight(1f),
                        message = "No conversations yet. Start one with a classmate."
                    )
                    Button(
                        onClick = onNewChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(text = "Start a chat")
                    }
                }

                is UiState.Success -> {
                    val content = listState.data
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (content.requests.isNotEmpty()) {
                            item(key = "requests-header") {
                                Text(
                                    text = "Message requests",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 12.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }

                            items(content.requests, key = { "request-${it.id}" }) { chat ->
                                MessageRequestRow(
                                    chat = chat,
                                    enabled = !content.isWorking,
                                    onAccept = { viewModel.accept(chat.id) },
                                    onDecline = { viewModel.decline(chat.id) }
                                )
                            }

                            item(key = "requests-divider") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }

                        items(content.chats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                currentUid = content.currentUid,
                                onClick = { onOpenChat(chat.id) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    currentUid: String,
    onClick: () -> Unit
) {
    val unread = chat.hasUnreadFor(currentUid)
    val title = chat.titleFor(currentUid)
    val senderPrefix = chat.lastMessage
        ?.takeIf { chat.type == ChatType.Group && it.senderId != currentUid }
        ?.senderName
        ?.takeIf { it.isNotBlank() }
        ?.let { "$it: " }
        .orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(name = title, size = 46.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeTime(chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = senderPrefix + chat.previewText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (chat.isMutedBy(currentUid)) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsOff,
                        contentDescription = "Muted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(15.dp)
                    )
                }

                if (unread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRequestRow(
    chat: Chat,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // requestedBy is the sender of the request, so this is the student who
        // wants to start the conversation.
        val name = chat.memberNames[chat.requestedBy].orEmpty().ifBlank { "A student" }

        InitialsAvatar(name = name, size = 42.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chat.previewText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(onClick = onAccept, enabled = enabled) {
            Text(text = "Accept")
        }
        OutlinedButton(onClick = onDecline, enabled = enabled) {
            Text(text = "Decline")
        }
    }
}
