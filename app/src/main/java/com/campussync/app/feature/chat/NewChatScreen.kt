package com.campussync.app.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun NewChatScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val createdChatId by viewModel.createdChatId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(createdChatId) {
        val chatId = createdChatId
        if (chatId != null) {
            viewModel.clearCreated()
            onCreated(chatId)
        }
    }

    LaunchedEffect(message) {
        val pending = message
        if (pending != null) {
            snackbarHostState.showSnackbar(pending)
            viewModel.clearMessage()
        }
    }

    val isGroup = selected.size > 1 || title.isNotBlank()

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
            ScreenHeader(title = "New chat", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text(text = "Group name (optional)") },
                    supportingText = {
                        Text(text = "Leave empty to message one classmate directly.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(text = "Search classmates") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            when (val listState = state) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading your college's students..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = listState.message,
                    onRetry = viewModel::load
                )

                UiState.Empty -> EmptyView(
                    modifier = Modifier.weight(1f),
                    message = "No other students in your college yet."
                )

                is UiState.Success -> {
                    val visible = listState.data.filter { student ->
                        query.isBlank() ||
                            student.name.contains(query, ignoreCase = true) ||
                            student.username.contains(query, ignoreCase = true)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (visible.isEmpty()) {
                            item(key = "no-match") {
                                Text(
                                    text = "No classmates match that search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        items(visible, key = { it.uid }) { student ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSelected(student.uid) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialsAvatar(name = student.name, size = 40.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = student.name.ifBlank { "Student" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${student.username}" +
                                            if (student.department.isBlank()) {
                                                ""
                                            } else {
                                                " · ${student.department}"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = student.uid in selected,
                                    onCheckedChange = { viewModel.toggleSelected(student.uid) }
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::create,
                enabled = selected.isNotEmpty() && !isCreating && (!isGroup || title.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = when {
                        isCreating -> "Starting..."
                        isGroup -> "Create group"
                        else -> "Start chat"
                    }
                )
            }
        }
    }
}
