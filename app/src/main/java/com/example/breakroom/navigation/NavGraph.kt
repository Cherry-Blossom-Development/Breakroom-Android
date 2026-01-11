package com.example.breakroom.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.breakroom.data.AuthRepository
import com.example.breakroom.data.TokenManager
import com.example.breakroom.network.RetrofitClient
import com.example.breakroom.ui.components.NavDestination
import com.example.breakroom.ui.components.TopNavigationBar
import com.example.breakroom.ui.screens.*

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
    object Blog : Screen("blog")
    object Chat : Screen("chat")
    object Friends : Screen("friends")
    object Profile : Screen("profile")
}

@Composable
fun BreakroomNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    val tokenManager = remember { TokenManager(context) }
    val authRepository = remember {
        AuthRepository(RetrofitClient.apiService, tokenManager)
    }

    // Determine start destination based on login state
    val startDestination = if (authRepository.isLoggedIn()) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // Check if we're on a logged-in screen (show top nav)
    val showTopNav = currentRoute in listOf(
        Screen.Home.route,
        Screen.Blog.route,
        Screen.Chat.route,
        Screen.Friends.route,
        Screen.Profile.route
    )

    Scaffold(
        topBar = {
            if (showTopNav) {
                TopNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        val route = when (destination) {
                            NavDestination.HOME -> Screen.Home.route
                            NavDestination.BLOG -> Screen.Blog.route
                            NavDestination.CHAT -> Screen.Chat.route
                            NavDestination.FRIENDS -> Screen.Friends.route
                            NavDestination.PROFILE -> Screen.Profile.route
                        }
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Login.route) {
                val viewModel = remember { LoginViewModel(authRepository) }
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToSignup = {
                        navController.navigate(Screen.Signup.route)
                    },
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Signup.route) {
                val viewModel = remember { SignupViewModel(authRepository) }
                SignupScreen(
                    viewModel = viewModel,
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                    onSignupSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val viewModel = remember { HomeViewModel(authRepository) }
                HomeScreen(
                    viewModel = viewModel,
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Blog.route) {
                BlogScreen()
            }

            composable(Screen.Chat.route) {
                ChatScreen()
            }

            composable(Screen.Friends.route) {
                FriendsScreen()
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
