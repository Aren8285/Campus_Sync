package com.campussync.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ReportDialog
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.UserProfile

@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val safetyState by viewModel.safetyState.collectAsStateWithLifecycle()
    val openChatId by viewModel.openChatId.collectAsStateWithLifecycle()
    var reportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(openChatId) {
        val chatId = openChatId
        if (chatId != null) {
            viewModel.clearOpenChat()
            onOpenChat(chatId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Student profile", onBack = onBack)

        when (val profileState = state) {
            UiState.Loading -> LoadingView(
                modifier = Modifier.weight(1f),
                message = "Loading profile..."
            )

            is UiState.Error -> ErrorView(
                modifier = Modifier.weight(1f),
                message = profileState.message,
                onRetry = viewModel::load
            )

            UiState.Empty -> EmptyView(
                modifier = Modifier.weight(1f),
                message = "This profile doesn't exist."
            )

            is UiState.Success -> ProfileContent(
                profile = profileState.data,
                safetyState = safetyState,
                onMessage = viewModel::messageStudent,
                onToggleFollow = viewModel::toggleFollow,
                onToggleBlock = viewModel::toggleBlock,
                onReport = { reportOpen = true }
            )
        }
    }

    if (reportOpen) {
        ReportDialog(
            title = "Report student",
            onDismiss = { reportOpen = false },
            onSubmit = { reason, details ->
                viewModel.report(reason, details)
                reportOpen = false
            }
        )
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    safetyState: ProfileSafetyState,
    onMessage: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
    onReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProfileDetails(profile = profile)

        if (!safetyState.isOwnProfile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onMessage,
                    enabled = !safetyState.isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Message")
                }

                OutlinedButton(
                    onClick = onToggleFollow,
                    enabled = !safetyState.isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (safetyState.isFollowing) "Unfollow" else "Follow"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleBlock,
                    enabled = !safetyState.isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (safetyState.isBlocked) {
                            "Unblock student"
                        } else {
                            "Block student"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onReport,
                    enabled = !safetyState.isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Report")
                }
            }
        }

        safetyState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
