package com.example.breakroom.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.breakroom.data.AuthRepository
import com.example.breakroom.data.BlogRepository
import com.example.breakroom.data.BreakroomRepository
import com.example.breakroom.data.ChatRepository
import com.example.breakroom.data.FriendsRepository
import com.example.breakroom.data.ProfileRepository
import com.example.breakroom.data.TokenManager
import com.example.breakroom.network.RetrofitClient
import com.example.breakroom.network.SocketManager
import com.example.breakroom.service.ChatService
import com.example.breakroom.ui.components.NavDestination
import com.example.breakroom.ui.components.TopNavigationBar
import com.example.breakroom.ui.screens.*
import com.example.breakroom.ui.screens.chat.ChatScreen
import com.example.breakroom.ui.screens.chat.ChatViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
    object Blog : Screen("blog")
    object BlogEditor : Screen("blog/editor?postId={postId}") {
        fun createRoute(postId: Int?) = if (postId != null) "blog/editor?postId=$postId" else "blog/editor"
    }
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

    // Chat dependencies
    val socketManager = remember { SocketManager(tokenManager) }
    val chatRepository = remember {
        ChatRepository(RetrofitClient.chatApiService, socketManager, tokenManager, context)
    }

    // Breakroom dependencies
    val breakroomRepository = remember {
        BreakroomRepository(RetrofitClient.breakroomApiService, tokenManager)
    }

    // Blog dependencies
    val blogRepository = remember {
        BlogRepository(RetrofitClient.breakroomApiService, tokenManager)
    }
    val blogViewModel = remember { BlogViewModel(blogRepository) }

    // Friends dependencies
    val friendsRepository = remember {
        FriendsRepository(RetrofitClient.breakroomApiService, tokenManager)
    }
    val friendsViewModel = remember { FriendsViewModel(friendsRepository) }

    // Profile dependencies
    val profileRepository = remember {
        ProfileRepository(RetrofitClient.breakroomApiService, tokenManager, context)
    }
    val profileViewModel = remember { ProfileViewModel(profileRepository, authRepository) }

    // Store current user ID for chat (updated after login)
    val currentUserId = remember { mutableIntStateOf(0) }

    // Determine start destination based on login state
    val startDestination = if (authRepository.isLoggedIn()) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    // Fetch user ID and start chat service if already logged in
    LaunchedEffect(startDestination) {
        if (authRepository.isLoggedIn()) {
            // Fetch user ID if we don't have it yet
            if (currentUserId.intValue == 0) {
                val meResult = authRepository.getMe()
                if (meResult is com.example.breakroom.data.AuthResult.Success) {
                    currentUserId.intValue = meResult.data.userId
                }
            }

            // Start chat service
            val serviceIntent = Intent(context, ChatService::class.java).apply {
                action = ChatService.ACTION_START
            }
            context.startService(serviceIntent)
        }
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
                    onLoginSuccess = { userId ->
                        currentUserId.intValue = userId
                        // Start chat service
                        val serviceIntent = Intent(context, ChatService::class.java).apply {
                            action = ChatService.ACTION_START
                        }
                        context.startService(serviceIntent)
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
                    onSignupSuccess = { userId ->
                        currentUserId.intValue = userId
                        // Start chat service
                        val serviceIntent = Intent(context, ChatService::class.java).apply {
                            action = ChatService.ACTION_START
                        }
                        context.startService(serviceIntent)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val viewModel = remember { HomeViewModel(authRepository, breakroomRepository) }
                HomeScreen(
                    viewModel = viewModel,
                    chatRepository = chatRepository,
                    tokenManager = tokenManager,
                    onLogout = {
                        // Stop chat service
                        val serviceIntent = Intent(context, ChatService::class.java).apply {
                            action = ChatService.ACTION_STOP
                        }
                        context.startService(serviceIntent)
                        socketManager.disconnect()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Blog.route) {
                BlogScreen(
                    viewModel = blogViewModel,
                    onNavigateToEditor = { postId ->
                        navController.navigate(Screen.BlogEditor.createRoute(postId))
                    }
                )
            }

            composable(
                route = Screen.BlogEditor.route,
                arguments = listOf(
                    navArgument("postId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val postIdStr = backStackEntry.arguments?.getString("postId")
                val postId = postIdStr?.toIntOrNull()
                BlogEditorScreen(
                    viewModel = blogViewModel,
                    postId = postId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Chat.route) {
                val chatViewModel = remember {
                    ChatViewModel(chatRepository, currentUserId.intValue)
                }
                ChatScreen(viewModel = chatViewModel)
            }

            composable(Screen.Friends.route) {
                FriendsScreen(viewModel = friendsViewModel)
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onLoggedOut = {
                        // Stop chat service
                        val serviceIntent = Intent(context, ChatService::class.java).apply {
                            action = ChatService.ACTION_STOP
                        }
                        context.startService(serviceIntent)
                        socketManager.disconnect()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
