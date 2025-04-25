package ar.edu.um.tif.aiAssistant.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ar.edu.um.tif.aiAssistant.component.home.HomeScreen
import ar.edu.um.tif.aiAssistant.component.loading.LoadingScreen
import ar.edu.um.tif.aiAssistant.component.login.LoginScreen

@Composable
fun NavigationWrapper() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {

        composable<Loading> {
            LoadingScreen(
                navigateToLogin = {
                    navController.popBackStack()
                    navController.navigate(Login)
                },
                navigateToHome = {
                    navController.popBackStack()
                    navController.navigate(Home)
                }
            )
        }

        composable<Login> {
            LoginScreen(navigateToHome = {
                navController.popBackStack()
                navController.navigate(Home)
            })
        }

        composable<Home> {
            HomeScreen(
                navigateToLogin = { navController.navigate(Login) }
            )
        }
    }
}