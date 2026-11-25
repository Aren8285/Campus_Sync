package com.campussync.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ProfileAvatar
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Edit profile", onBack = onBack)

        when (val profileState = state.profileState) {
            UiState.Loading -> LoadingView(
                modifier = Modifier.weight(1f),
                message = "Loading your profile..."
            )

            is UiState.Error -> ErrorView(
                modifier = Modifier.weight(1f),
                message = profileState.message,
                onRetry = viewModel::loadProfile
            )

            UiState.Empty -> EmptyView(
                modifier = Modifier.weight(1f),
                message = "Your profile isn't set up yet."
            )

            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "College, role, badges, and karma can't be changed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ProfileAvatar(
                    name = state.name,
                    avatarUrl = state.avatarUrl,
                    size = 88.dp
                )

                OutlinedTextField(
                    value = state.avatarUrl,
                    onValueChange = viewModel::onAvatarUrlChange,
                    label = { Text(text = "Profile photo link (optional)") },
                    supportingText = {
                        Text(
                            text = "Use a public https:// image link. " +
                                "Your initials are shown otherwise."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ProfileFormFields(
                    name = state.name,
                    onNameChange = viewModel::onNameChange,
                    username = state.username,
                    onUsernameChange = viewModel::onUsernameChange,
                    department = state.department,
                    onDepartmentChange = viewModel::onDepartmentChange,
                    year = state.year,
                    onYearChange = viewModel::onYearChange,
                    graduationYear = state.graduationYear,
                    onGraduationYearChange = viewModel::onGraduationYearChange,
                    bio = state.bio,
                    onBioChange = viewModel::onBioChange,
                    skillsText = state.skillsText,
                    onSkillsChange = viewModel::onSkillsChange,
                    selectedInterests = state.selectedInterests,
                    onToggleInterest = viewModel::toggleInterest
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (state.isSaving) "Saving..." else "Save changes")
                }
            }
        }
    }
}
