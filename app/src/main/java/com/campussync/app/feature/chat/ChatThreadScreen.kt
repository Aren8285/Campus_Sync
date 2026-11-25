package com.campussync.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.InitialsAvatar
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.RemoteImage
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.core.util.formatRelativeTime
import com.campussync.app.data.model.Chat
import com.campussync.app.data.model.ChatStatus
import com.campussync.app.data.model.ChatType
import com.campussync.app.data.model.Message
import com.campussync.app.data.model.MessageType
import com.campussync.app.data.model.UserProfile
import com.campussync.app.data.model.isAdmin
import com.campussync.app.data.model.isMutedBy
import com.campussync.app.data.model.otherMemberId
import com.campussync.app.data.model.titleFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ChatThreadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val leftChat by viewModel.leftChat.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showInfo by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(leftChat) {
        if (leftChat) onBack()
    }

    LaunchedEffect(showInfo) {
        if (showInfo) viewModel.loadStudents()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val threadState = state) {
                UiState.Loading -> LoadingView(message = "Opening chat...")

                is UiState.Error -> ErrorView(
                    message = threadState.message,
                    onRetry = viewModel::retry
                )

                UiState.Empty -> EmptyView(
                    message = "This chat is no longer available."
                )

                is UiState.Success -> ChatThread(
                    content = threadState.data,
                    input = input,
                    isSending = isSending,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::send,
                    onOpenProfile = onOpenProfile,
                    onBack = onBack,
                    onMute = viewModel::toggleMute,
                    onInfo = { showInfo = true },
                    onAccept = viewModel::acceptRequest,
                    onDecline = viewModel::declineRequest,
                    onShareImage = { showImageDialog = true }
                )
            }
        }
    }

    val content = (state as? UiState.Success)?.data
    if (showInfo && content?.chat != null) {
        GroupInfoSheet(
            content = content,
            onDismiss = { showInfo = false },
            onRename = viewModel::renameGroup,
            onAdd = viewModel::addMember,
            onRemove = viewModel::removeMember,
            onSetAdmin = viewModel::setAdmin,
            onLeave = {
                showInfo = false
                viewModel.leaveGroup()
            }
        )
    }

    if (showImageDialog) {
        ImageLinkDialog(
            onDismiss = { showImageDialog = false },
            onSend = { url, caption ->
                viewModel.sendImage(url, caption)
                showImageDialog = false
            }
        )
    }
}

@Composable
private fun ChatThread(
    content: ChatThreadContent,
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onBack: () -> Unit,
    onMute: () -> Unit,
    onInfo: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onShareImage: () -> Unit
) {
    val chat = content.chat ?: return
    val me = content.currentUid
    val listState = rememberLazyListState()
    val muted = chat.isMutedBy(me)
    val canWrite = chat.status == ChatStatus.Accepted || chat.requestedBy == me

    val lastOwn = content.messages.lastOrNull { it.senderId == me }
    val lastMessage = content.messages.lastOrNull()
    val readLabel = if (lastOwn == null || lastOwn.id != lastMessage?.id) {
        null
    } else {
        val ownAt = lastOwn.createdAt ?: 0L
        val readers = chat.members
            .filter { it != me }
            .count { (chat.readAt[it] ?: 0L) >= ownAt }

        when {
            readers == 0 -> "Sent"
            chat.type == ChatType.Direct -> "Read"
            else -> "Read by $readers"
        }
    }

    LaunchedEffect(content.messages.size, content.typingNames.size) {
        if (content.messages.isNotEmpty()) {
            val target = content.messages.lastIndex + if (content.typingNames.isNotEmpty()) 1 else 0
            listState.animateScrollToItem(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = chat.titleFor(me),
            onBack = onBack,
            actions = {
                IconButton(onClick = onMute) {
                    Icon(
                        imageVector = if (muted) {
                            Icons.Outlined.NotificationsOff
                        } else {
                            Icons.Outlined.Notifications
                        },
                        contentDescription = if (muted) "Unmute chat" else "Mute chat"
                    )
                }
                if (chat.type == ChatType.Group) {
                    IconButton(onClick = onInfo) {
                        Icon(
                            imageVector = Icons.Outlined.Group,
                            contentDescription = "Group info"
                        )
                    }
                }
            }
        )

        if (chat.status == ChatStatus.Request) {
            RequestNotice(
                chat = chat,
                currentUid = me,
                onAccept = onAccept,
                onDecline = onDecline
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (content.messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (canWrite) {
                            "No messages yet. Say hello."
                        } else {
                            "No messages yet. Accept the request to reply."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(content.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isOwn = message.senderId == me,
                    showSender = chat.type == ChatType.Group,
                    readLabel = if (message.id == lastOwn?.id) readLabel else null,
                    onOpenProfile = onOpenProfile
                )
            }

            if (content.typingNames.isNotEmpty()) {
                item(key = "typing") {
                    Text(
                        text = typingLabel(content.typingNames),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (canWrite) {
            MessageComposer(
                value = input,
                isSending = isSending,
                onValueChange = onInputChange,
                onSend = onSend,
                onShareImage = onShareImage
            )
        } else {
            Surface(tonalElevation = 2.dp) {
                Text(
                    text = "Accept this message request to reply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RequestNotice(
    chat: Chat,
    currentUid: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val otherUid = chat.otherMemberId(currentUid)
    val name = chat.memberNames[otherUid.orEmpty()].orEmpty().ifBlank { "This student" }
    val isMine = chat.requestedBy == currentUid

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isMine) "Message request sent" else "$name wants to message you",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (isMine) {
                    "You can send a message while you wait for them to accept."
                } else {
                    "Accept to reply. Declining removes this chat from your inbox."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isMine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept) {
                        Text(text = "Accept")
                    }
                    OutlinedButton(onClick = onDecline) {
                        Text(text = "Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    showSender: Boolean,
    readLabel: String?,
    onOpenProfile: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (showSender && !isOwn) {
            TextButton(
                onClick = { onOpenProfile(message.senderId) },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = message.senderName.ifBlank { "Student" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Surface(
            color = if (isOwn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.type == MessageType.Image && message.imageUrl.isNotBlank()) {
                    RemoteImage(
                        url = message.imageUrl,
                        contentDescription = "Shared image",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        maxDimension = 720
                    )
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOwn) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(
                            top = if (message.type == MessageType.Image &&
                                message.imageUrl.isNotBlank()
                            ) {
                                6.dp
                            } else {
                                0.dp
                            }
                        )
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatRelativeTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (readLabel != null) {
                Text(
                    text = readLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareImage: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onShareImage) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Share an image link"
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(text = "Message") },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupInfoSheet(
    content: ChatThreadContent,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onAdd: (UserProfile) -> Unit,
    onRemove: (String) -> Unit,
    onSetAdmin: (String, Boolean) -> Unit,
    onLeave: () -> Unit
) {
    val chat = content.chat ?: return
    val me = content.currentUid
    val isAdmin = chat.isAdmin(me)
    var title by remember(chat.id, chat.title) { mutableStateOf(chat.title) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Group info",
                style = MaterialTheme.typography.titleLarge
            )

            if (isAdmin) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = "Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onRename(title) },
                    enabled = title.isNotBlank() && title.trim() != chat.title,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Save name")
                }
            }

            HorizontalDivider()

            Text(
                text = "Members (${chat.members.size})",
                style = MaterialTheme.typography.titleSmall
            )

            chat.members.forEach { uid ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InitialsAvatar(
                        name = chat.memberNames[uid].orEmpty(),
                        size = 36.dp
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text(
                            text = chat.memberNames[uid].orEmpty().ifBlank { "Student" },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.isAdmin(uid)) {
                            Text(
                                text = "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isAdmin && uid != me) {
                        TextButton(
                            onClick = { onSetAdmin(uid, !chat.isAdmin(uid)) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (chat.isAdmin(uid)) "Demote" else "Make admin",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { onRemove(uid) }) {
                            Icon(
                                imageVector = Icons.Outlined.PersonRemove,
                                contentDescription = "Remove from group"
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Add classmates",
                style = MaterialTheme.typography.titleSmall
            )

            if (content.isLoadingStudents) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (content.students.isEmpty()) {
                Text(
                    text = "No more classmates to add right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                content.students.take(20).forEach { student ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsAvatar(name = student.name, size = 32.dp)
                        Text(
                            text = student.name.ifBlank { "Student" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { onAdd(student) },
                            enabled = isAdmin
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = "Add to group"
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Leave group")
            }
        }
    }
}

@Composable
private fun ImageLinkDialog(
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Share an image link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(text = "https:// image link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(text = "Caption (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(url, caption) },
                enabled = url.isNotBlank()
            ) {
                Text(text = "Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

private fun typingLabel(names: List<String>): String = when {
    names.isEmpty() -> ""
    names.size == 1 -> "${names.first()} is typing..."
    else -> "${names.joinToString(", ")} are typing..."
}
