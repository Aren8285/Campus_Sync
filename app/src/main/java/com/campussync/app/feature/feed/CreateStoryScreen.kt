package com.campussync.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.data.model.StoryType

@Composable
fun CreateStoryScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: CreateStoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.posted) {
        if (state.posted) onPosted()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "New story", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Stories disappear after 24 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StoryTypeChips(
                selected = state.type,
                onSelect = viewModel::onTypeChange
            )

            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = {
                    Text(
                        text = if (state.type == StoryType.Text) {
                            "Story text"
                        } else {
                            "Caption (optional)"
                        }
                    )
                },
                supportingText = { Text(text = "${state.text.length}/200 characters") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            if (state.type == StoryType.Image) {
                OutlinedTextField(
                    value = state.imageUrl,
                    onValueChange = viewModel::onImageUrlChange,
                    label = { Text(text = "Image link") },
                    placeholder = { Text(text = "https://...") },
                    supportingText = {
                        Text(text = "Use a public image link. Files are not uploaded.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !state.isPosting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (state.isPosting) "Sharing..." else "Share story")
            }
        }
    }
}

@Composable
private fun StoryTypeChips(
    selected: StoryType,
    onSelect: (StoryType) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == StoryType.Text,
            onClick = { onSelect(StoryType.Text) },
            label = { Text(text = "Text") }
        )
        FilterChip(
            selected = selected == StoryType.Image,
            onClick = { onSelect(StoryType.Image) },
            label = { Text(text = "Image") }
        )
    }
}
