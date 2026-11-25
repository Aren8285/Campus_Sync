package com.campussync.app.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campussync.app.core.session.SessionState
import com.campussync.app.core.session.SessionViewModel
import com.campussync.app.core.ui.components.ErrorView
import com.campussync.app.core.ui.components.LoadingView
import com.campussync.app.feature.auth.AuthFlow
import com.campussync.app.feature.auth.VerifyEmailScreen
import com.campussync.app.feature.profile.OnboardingScreen

@Composable
fun CampusSyncApp(
    initialChatId: String? = null,
    onInitialChatHandled: () -> Unit = {},
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = sessionState) {
            SessionState.Loading -> LoadingView(message = "Checking your session...")

            SessionState.SignedOut -> AuthFlow()

            is SessionState.Unverified -> VerifyEmailScreen()

            is SessionState.NeedsOnboarding -> OnboardingScreen()

            is SessionState.Ready -> MainShell(
                initialChatId = initialChatId,
                onInitialChatHandled = onInitialChatHandled
            )

            is SessionState.Error -> ErrorView(
                message = state.message,
                onRetry = sessionViewModel::retry
            )
        }
    }
}
