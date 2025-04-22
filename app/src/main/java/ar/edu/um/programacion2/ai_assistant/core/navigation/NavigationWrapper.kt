package ar.edu.um.programacion2.computech.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ar.edu.um.programacion2.computech.component.device.DeviceScreen
import ar.edu.um.programacion2.computech.component.home.HomeScreen
import ar.edu.um.programacion2.computech.component.loading.LoadingScreen
import ar.edu.um.programacion2.computech.component.login.LoginScreen

@Composable
fun NavigationWrapper() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Loading) {

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
                navigateToDevice = { id -> navController.navigate(Device(id)) },
                navigateToLogin = { navController.navigate(Login) }
            )
        }
    }
}