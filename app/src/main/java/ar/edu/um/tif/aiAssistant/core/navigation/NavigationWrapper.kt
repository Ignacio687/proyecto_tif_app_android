package ar.edu.um.tif.aiAssistant.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.um.tif.aiAssistant.component.assistant.AssistantScreen
import ar.edu.um.tif.aiAssistant.component.auth.EmailVerificationScreen
import ar.edu.um.tif.aiAssistant.component.auth.ForgotPasswordScreen
import ar.edu.um.tif.aiAssistant.component.auth.GoogleSignInScreen
import ar.edu.um.tif.aiAssistant.component.auth.LoginScreen
import ar.edu.um.tif.aiAssistant.component.auth.RegisterScreen
import ar.edu.um.tif.aiAssistant.component.auth.ResetPasswordScreen
import ar.edu.um.tif.aiAssistant.component.auth.WelcomeScreen
import ar.edu.um.tif.aiAssistant.component.home.HomeScreen
import ar.edu.um.tif.aiAssistant.component.splash.SplashScreen
import ar.edu.um.tif.aiAssistant.component.splash.SplashViewModel

@Composable
fun NavigationWrapper() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Splash) {

        composable<Splash> {
            val viewModel: SplashViewModel = hiltViewModel()
            val navigateTo by viewModel.navigateTo.collectAsState()

            SplashScreen()

            LaunchedEffect(navigateTo) {
                navigateTo?.let {
                    navController.navigate(it) {
                        popUpTo(Splash) { inclusive = true }
                    }
                }
            }
        }

        composable<Welcome> {
            WelcomeScreen(
                onGoogleSignInClick = { navController.navigate(GoogleSignIn) },
                onEmailSignUpClick = { navController.navigate(Register) },
                onEmailLoginClick = { navController.navigate(Login) }
            )
        }

        composable<Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Home) {
                        popUpTo(Welcome) { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate(Register) },
                onForgotPasswordClick = { navController.navigate(ForgotPassword) },
                onEmailVerificationNeeded = { email ->
                    navController.navigate(EmailVerification.apply {
                        this.email = email
                    }) {
                        popUpTo(Login) { inclusive = true }
                    }
                }
            )
        }

        composable<Register> {
            RegisterScreen(
                onRegistrationSuccess = { email ->
                    navController.navigate(EmailVerification.apply {
                        this.email = email
                    }) {
                        popUpTo(Register) { inclusive = true }
                    }
                },
                onLoginClick = { navController.navigate(Login) }
            )
        }

        composable<GoogleSignIn> {
            GoogleSignInScreen(
                onSignInSuccess = {
                    navController.navigate(Home) {
                        popUpTo(Welcome) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<EmailVerification> {
            EmailVerificationScreen(
                onVerificationSuccess = {
                    navController.navigate(Home) {
                        popUpTo(Welcome) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    // Navigate to login screen after email verification
                    navController.navigate(Login) {
                        // Remove email verification screen from back stack
                        popUpTo(EmailVerification) { inclusive = true }
                    }
                },
                onResendCode = { /* Code resent */ },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<ForgotPassword> {
            ForgotPasswordScreen(
                onResetCodeSent = { email ->
                    navController.navigate(ResetPassword) {
                        popUpTo(ForgotPassword) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<ResetPassword> {
            ResetPasswordScreen(
                onPasswordResetSuccess = {
                    navController.navigate(Login) {
                        popUpTo(Welcome) { inclusive = false }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable<Home> {
            HomeScreen(
                navigateToLogin = {
                    navController.navigate(Welcome) {
                        popUpTo(Home) { inclusive = true }
                    }
                },
                navigateToAssistant = { navController.navigate(Assistant) }
            )
        }

        composable<Assistant> {
            AssistantScreen(
                navigateToLogin = {
                    navController.navigate(Welcome) {
                        popUpTo(Assistant) { inclusive = true }
                    }
                },
                navigateBack = { navController.popBackStack() }
            )
        }
    }
}