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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.ui.components.DropdownField
import com.campussync.app.core.ui.components.EmptyView
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.core.ui.components.ScreenHeader
import com.campussync.app.core.ui.components.UiState
import com.campussync.app.data.model.College

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(title = "Finish your profile")

            if (state.isComplete) {
                LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Setting up your profile..."
                )
            } else {
                when (val colleges = state.colleges) {
                    UiState.Loading -> LoadingView(
                        modifier = Modifier.weight(1f),
                        message = "Loading colleges..."
                    )

                    is UiState.Error -> ErrorView(
                        modifier = Modifier.weight(1f),
                        message = colleges.message,
                        onRetry = viewModel::load
                    )

                    UiState.Empty -> Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyView(
                            modifier = Modifier.weight(1f),
                            message = "No colleges are available yet. Ask your campus admin to add one."
                        )
                        TextButton(onClick = viewModel::load) {
                            Text(text = "Refresh")
                        }
                    }

                    is UiState.Success -> OnboardingForm(
                        state = state,
                        colleges = colleges.data,
                        onSelectCollege = viewModel::selectCollege,
                        onNameChange = viewModel::onNameChange,
                        onUsernameChange = viewModel::onUsernameChange,
                        onDepartmentChange = viewModel::onDepartmentChange,
                        onYearChange = viewModel::onYearChange,
                        onGraduationYearChange = viewModel::onGraduationYearChange,
                        onBioChange = viewModel::onBioChange,
                        onSkillsChange = viewModel::onSkillsChange,
                        onToggleInterest = viewModel::toggleInterest,
                        onSubmit = viewModel::submit
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingForm(
    state: OnboardingUiState,
    colleges: List<College>,
    onSelectCollege: (College) -> Unit,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onDepartmentChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onGraduationYearChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onSkillsChange: (String) -> Unit,
    onToggleInterest: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val selectedCollege = colleges.firstOrNull { it.id == state.collegeId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Just a few details before you join your campus.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.canChooseCollege) {
            DropdownField(
                label = "College",
                placeholder = "Select your college",
                options = colleges,
                selected = selectedCollege,
                optionLabel = { college -> college.name },
                onSelected = onSelectCollege
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "College",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.collegeName.ifBlank { "Selected during sign-up" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        ProfileFormFields(
            name = state.name,
            onNameChange = onNameChange,
            username = state.username,
            onUsernameChange = onUsernameChange,
            department = state.department,
            onDepartmentChange = onDepartmentChange,
            year = state.year,
            onYearChange = onYearChange,
            graduationYear = state.graduationYear,
            onGraduationYearChange = onGraduationYearChange,
            bio = state.bio,
            onBioChange = onBioChange,
            skillsText = state.skillsText,
            onSkillsChange = onSkillsChange,
            selectedInterests = state.selectedInterests,
            onToggleInterest = onToggleInterest
        )

        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (state.isSubmitting) "Saving..." else "Finish setup")
        }
    }
}
