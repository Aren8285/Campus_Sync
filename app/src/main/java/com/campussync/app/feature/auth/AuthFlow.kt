package com.campussync.app.feature.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campussync.app.core.navigation.Routes

@Composable
fun AuthFlow() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Welcome,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.Welcome) {
            WelcomeScreen(
                onSignUp = { navController.navigate(Routes.SignUp) },
                onLogin = { navController.navigate(Routes.Login) }
            )
        }

        composable(Routes.SignUp) {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onGoToLogin = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Welcome)
                    }
                }
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onForgotPassword = { navController.navigate(Routes.ForgotPassword) },
                onGoToSignUp = {
                    navController.navigate(Routes.SignUp) {
                        popUpTo(Routes.Welcome)
                    }
                }
            )
        }

        composable(Routes.ForgotPassword) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
