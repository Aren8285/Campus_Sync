package com.campussync.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@Composable
fun CreatePostScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: CreatePostViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.posted) {
        if (state.posted) onPosted()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "New post", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text(text = "What's happening on campus?") },
                supportingText = {
                    Text(text = "${state.text.length}/1000 characters")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10
            )

            Text(
                text = "Images (optional, up to 4 links)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.imageUrls.forEachIndexed { index, url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { viewModel.onImageUrlChange(index, it) },
                        label = { Text(text = "Image link ${index + 1}") },
                        placeholder = { Text(text = "https://...") },
                        supportingText = {
                            Text(text = "Use a public image link. Files are not uploaded.")
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    if (state.imageUrls.size > 1) {
                        IconButton(onClick = { viewModel.removeImageUrl(index) }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove image link"
                            )
                        }
                    }
                }
            }

            if (state.imageUrls.size < 4) {
                Button(
                    onClick = viewModel::addImageUrl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null
                    )
                    Text(
                        text = "Add image link",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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
                Text(text = if (state.isPosting) "Posting..." else "Post")
            }
        }
    }
}
