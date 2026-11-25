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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onViewPublicProfile: (String) -> Unit,
    onOpenChats: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Profile")

        when (val profileState = state) {
            UiState.Loading -> LoadingView(
                modifier = Modifier.weight(1f),
                message = "Loading your profile..."
            )

            is UiState.Error -> ErrorView(
                modifier = Modifier.weight(1f),
                message = profileState.message,
                onRetry = viewModel::retry
            )

            UiState.Empty -> EmptyView(
                modifier = Modifier.weight(1f),
                message = "Your profile isn't set up yet."
            )

            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileDetails(profile = profileState.data)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Edit profile")
                    }
                    OutlinedButton(
                        onClick = { onViewPublicProfile(profileState.data.uid) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Public profile")
                    }
                }

                OutlinedButton(
                    onClick = onOpenChats,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Messages")
                }

                HorizontalDivider()

                logoutState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedButton(
                    onClick = viewModel::logout,
                    enabled = !logoutState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (logoutState.isLoading) "Logging out..." else "Log out")
                }
            }
        }
    }
}
