package com.example.caffeineguard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity // <--- CHANGED IMPORT
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.caffeineguard.data.SessionManager
import com.example.caffeineguard.ui.*

// CHANGED: Must inherit from FragmentActivity for Biometrics to work
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CaffeineGuardApp()
                }
            }
        }
    }
}

@Composable
fun CaffeineGuardApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = SessionManager(context)

    val savedUser = sessionManager.getCurrentUsername()
    val isLoggedIn = sessionManager.isLoggedIn()

    val startRoute = when {
        savedUser != null && isLoggedIn -> "home/$savedUser"
        savedUser != null && !isLoggedIn -> "welcome_back/$savedUser"
        else -> "login"
    }

    NavHost(navController = navController, startDestination = startRoute) {

        composable("login") {
            LoginScreen(
                onLoginSuccess = { username ->
                    sessionManager.setLoggedIn(username)
                    navController.navigate("home/$username") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { username ->
                    navController.navigate("home/$username") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "home/{user}",
            arguments = listOf(navArgument("user") { type = NavType.StringType })
        ) { backStackEntry ->
            val userName = backStackEntry.arguments?.getString("user") ?: "User"

            CaffeineScreen(
                userName = userName,
                onNavigateToSettings = {
                    navController.navigate("settings/$userName")
                }
            )
        }

        composable(
            route = "settings/{user}",
            arguments = listOf(navArgument("user") { type = NavType.StringType })
        ) { backStackEntry ->
            val currentUser = backStackEntry.arguments?.getString("user") ?: "User"

            SettingsScreen(
                userName = currentUser,
                onBack = { navController.popBackStack() },
                onLogOut = {
                    sessionManager.logoutCurrentUser()
                    navController.navigate("welcome_back/$currentUser") { popUpTo(0) }
                },
                onDeleteAccount = {
                    sessionManager.deleteUser(currentUser)
                    navController.navigate("login") { popUpTo(0) }
                }
            )
        }

        composable(
            route = "welcome_back/{user}",
            arguments = listOf(navArgument("user") { type = NavType.StringType })
        ) { backStackEntry ->
            val lockedUser = backStackEntry.arguments?.getString("user") ?: "User"

            WelcomeBackScreen(
                username = lockedUser,
                onLoginSuccess = {
                    sessionManager.setLoggedIn(lockedUser)
                    navController.navigate("home/$lockedUser") { popUpTo(0) }
                },
                onSwitchAccount = {
                    navController.navigate("login") { popUpTo(0) }
                }
            )
        }
    }
}