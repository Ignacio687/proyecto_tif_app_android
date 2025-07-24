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
import ar.edu.um.tif.aiAssistant.component.settings.SettingsScreen
import ar.edu.um.tif.aiAssistant.component.splash.SplashScreen
import ar.edu.um.tif.aiAssistant.component.splash.SplashViewModel
import ar.edu.um.tif.aiAssistant.core.state.AppScreen
import ar.edu.um.tif.aiAssistant.core.state.AppStateManager
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStateManagerEntryPoint {
    fun appStateManager(): AppStateManager
}

@Composable
fun NavigationWrapper(navigateToAssistant: Boolean = false) {
    val context = LocalContext.current
    val appStateManager = EntryPointAccessors.fromApplication(
        context,
        AppStateManagerEntryPoint::class.java
    ).appStateManager()

    val navController = rememberNavController()

    // Handle direct navigation to assistant from popup
    LaunchedEffect(navigateToAssistant) {
        if (navigateToAssistant) {
            navController.navigate(Assistant) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Splash) {

        composable<Splash> {
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.SPLASH)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.WELCOME)
            }
            WelcomeScreen(
                onGoogleSignInClick = { navController.navigate(GoogleSignIn) },
                onEmailSignUpClick = { navController.navigate(Register) },
                onEmailLoginClick = { navController.navigate(Login) }
            )
        }

        composable<Login> {
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.LOGIN)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.REGISTER)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.OTHER)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.OTHER)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.OTHER)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.OTHER)
            }
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
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.HOME)
            }
            HomeScreen(
                navigateToLogin = {
                    navController.navigate(Welcome) {
                        popUpTo(Home) { inclusive = true }
                    }
                },
                navigateToAssistant = { navController.navigate(Assistant) },
                navigateToSettings = { navController.navigate(Settings) }
            )
        }

        composable<Assistant> {
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.ASSISTANT)
            }
            AssistantScreen(
                navigateToLogin = {
                    navController.navigate(Welcome) {
                        popUpTo(Assistant) { inclusive = true }
                    }
                },
                navigateToHome = {
                    navController.navigate(Home) {
                        popUpTo(Assistant) { inclusive = true }
                    }
                }
            )
        }

        composable<Settings> {
            LaunchedEffect(Unit) {
                appStateManager.setCurrentScreen(AppScreen.SETTINGS)
            }
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}