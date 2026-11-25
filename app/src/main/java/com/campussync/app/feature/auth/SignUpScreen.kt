package com.campussync.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun SignUpScreen(
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScreenHeader(title = "Create account", onBack = onBack)

            when (val colleges = state.colleges) {
                UiState.Loading -> LoadingView(
                    modifier = Modifier.weight(1f),
                    message = "Loading colleges..."
                )

                is UiState.Error -> ErrorView(
                    modifier = Modifier.weight(1f),
                    message = colleges.message,
                    onRetry = viewModel::loadColleges
                )

                UiState.Empty -> Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EmptyView(
                        modifier = Modifier.weight(1f),
                        message = "No colleges are available yet. Ask your campus admin to add one."
                    )
                    TextButton(onClick = viewModel::loadColleges) {
                        Text(text = "Refresh")
                    }
                }

                is UiState.Success -> SignUpForm(
                    state = state,
                    colleges = colleges.data,
                    onFullNameChange = viewModel::onFullNameChange,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onCollegeSelected = viewModel::onCollegeSelected,
                    onSubmit = viewModel::submit,
                    onGoToLogin = onGoToLogin
                )
            }
        }
    }
}

@Composable
private fun SignUpForm(
    state: SignUpUiState,
    colleges: List<College>,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCollegeSelected: (College) -> Unit,
    onSubmit: () -> Unit,
    onGoToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.fullName,
            onValueChange = onFullNameChange,
            label = { Text(text = "Full name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(text = "College email") },
            supportingText = { Text(text = "Use the email your college gave you.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(text = "Password") },
            supportingText = { Text(text = "At least 6 characters.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val selectedCollege = colleges.firstOrNull { it.id == state.selectedCollegeId }
        DropdownField(
            label = "College",
            placeholder = "Select your college",
            options = colleges,
            selected = selectedCollege,
            optionLabel = { college -> college.name },
            onSelected = onCollegeSelected
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
            Text(text = if (state.isSubmitting) "Creating account..." else "Create account")
        }

        TextButton(
            onClick = onGoToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Already have an account? Log in")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
