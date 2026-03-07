package com.cherryblossomdev.breakroom.navigation

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cherryblossomdev.breakroom.AppContainer
import com.cherryblossomdev.breakroom.data.KanbanRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Shortcut
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.cherryblossomdev.breakroom.service.ChatService
import com.cherryblossomdev.breakroom.ui.components.BottomNavDestination
import com.cherryblossomdev.breakroom.ui.components.BottomNavigationBar
import com.cherryblossomdev.breakroom.ui.components.TopNavigationBar
import com.cherryblossomdev.breakroom.ui.screens.*
import com.cherryblossomdev.breakroom.ui.screens.chat.ChatScreen
import com.cherryblossomdev.breakroom.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.launch

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
    // Bottom nav screens
    object About : Screen("about")
    object ToolShed : Screen("tool-shed")
    object Employment : Screen("employment")
    object HelpDesk : Screen("helpdesk")
    object CompanyPortal : Screen("company-portal")
    // Company detail screen
    object Company : Screen("company/{companyId}/{companyName}") {
        fun createRoute(companyId: Int, companyName: String) = "company/$companyId/${companyName.replace("/", "-")}"
    }
    // Project tickets screen
    object ProjectTickets : Screen("project/{projectId}/{projectName}/tickets") {
        fun createRoute(projectId: Int, projectName: String): String {
            val encodedName = java.net.URLEncoder.encode(projectName, "UTF-8")
            return "project/$projectId/$encodedName/tickets"
        }
    }
    // Lyric Lab screens
    object LyricLab : Screen("lyric-lab")
    object SongDetail : Screen("song/{songId}") {
        fun createRoute(songId: Int) = "song/$songId"
    }
    // Art Gallery
    object ArtGallery : Screen("art-gallery")
    // Kanban
    object KanbanRedirect : Screen("kanban")
    object KanbanBoard : Screen("kanban/board/{projectId}?title={title}") {
        fun createRoute(projectId: Int, title: String): String {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "kanban/board/$projectId?title=$encodedTitle"
        }
    }
}

@Composable
fun BreakroomNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deps = remember { AppContainer(context) }

    // Store current user ID for chat (updated after login)
    val currentUserId = remember { mutableIntStateOf(0) }

    // Determine start destination based on login state
    val startDestination = if (deps.authRepository.isLoggedIn()) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    // Fetch user ID and start chat service if already logged in
    LaunchedEffect(startDestination) {
        if (deps.authRepository.isLoggedIn()) {
            // Fetch user ID on IO thread to avoid blocking UI
            if (currentUserId.intValue == 0) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val meResult = deps.authRepository.getMe()
                    if (meResult is com.cherryblossomdev.breakroom.data.AuthResult.Success) {
                        currentUserId.intValue = meResult.data.userId
                    }
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
        Screen.Profile.route,
        Screen.About.route,
        Screen.ToolShed.route,
        Screen.Employment.route,
        Screen.HelpDesk.route,
        Screen.CompanyPortal.route,
        Screen.LyricLab.route,
        Screen.ArtGallery.route,
        Screen.KanbanRedirect.route
    ) || currentRoute.startsWith("company/") || currentRoute.startsWith("project/") || currentRoute.startsWith("song/") || currentRoute.startsWith("kanban/board/")

    // Show bottom nav on main screens
    val showBottomNav = showTopNav

    val isHomeScreen = currentRoute == Screen.Home.route

    // Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Shortcuts loaded at NavGraph level for the drawer
    val shortcuts = remember { mutableStateListOf<Shortcut>() }

    // Load shortcuts when logged in
    LaunchedEffect(startDestination) {
        if (deps.authRepository.isLoggedIn()) {
            when (val result = deps.breakroomRepository.loadShortcuts()) {
                is BreakroomResult.Success -> {
                    shortcuts.clear()
                    shortcuts.addAll(result.data)
                }
                else -> { /* silently fail */ }
            }
        }
    }

    // Hoisted HomeScreen actions for the top bar
    var homeOnAddBlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    var homeOnRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
    var homeIsRefreshing by remember { mutableStateOf(false) }

    // Helper to navigate to a shortcut URL
    fun navigateToShortcut(shortcut: Shortcut) {
        val url = shortcut.url
        when {
            url.startsWith("/project/") -> {
                val projectId = url.removePrefix("/project/").toIntOrNull()
                if (projectId != null) {
                    navController.navigate(
                        Screen.ProjectTickets.createRoute(projectId, shortcut.name)
                    )
                }
            }
            url == "/help-desk" -> navController.navigate(Screen.HelpDesk.route)
            url == "/company-portal" -> navController.navigate(Screen.CompanyPortal.route)
            url == "/employment" -> navController.navigate(Screen.Employment.route)
            url == "/tool-shed" -> navController.navigate(Screen.ToolShed.route)
            url == "/lyrics" -> navController.navigate(Screen.LyricLab.route)
            url == "/art-gallery" -> navController.navigate(Screen.ArtGallery.route)
            url == "/blog" -> navController.navigate(Screen.Blog.route)
            url == "/kanban" -> navController.navigate(Screen.KanbanRedirect.route)
        }
    }

    // Helper for logout
    fun performLogout() {
        val serviceIntent = Intent(context, ChatService::class.java).apply {
            action = ChatService.ACTION_STOP
        }
        context.startService(serviceIntent)
        deps.socketManager.disconnect()
        scope.launch {
            deps.authRepository.logout()
        }
        navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    // Helper to navigate from drawer
    fun drawerNavigate(route: String) {
        scope.launch { drawerState.close() }
        if (route != currentRoute) {
            navController.navigate(route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showTopNav,
        drawerContent = {
            ModalDrawerSheet {
                // App title
                Text(
                    text = "Breakroom",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                )

                Divider()

                // Navigation links
                ListItem(
                    headlineContent = { Text("Profile") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Profile.route) }
                )
                ListItem(
                    headlineContent = { Text("Chat") },
                    leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Chat.route) }
                )
                ListItem(
                    headlineContent = { Text("Friends") },
                    leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Friends.route) }
                )
                ListItem(
                    headlineContent = { Text("Blog") },
                    leadingContent = { Icon(Icons.Outlined.Article, contentDescription = null) },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Blog.route) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Shortcuts section
                Text(
                    text = "Shortcuts",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("Tool Shed") },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = null) },
                    modifier = Modifier.clickable {
                        drawerNavigate(Screen.ToolShed.route)
                    }
                )

                shortcuts.forEach { shortcut ->
                    ListItem(
                        headlineContent = { Text(shortcut.name) },
                        modifier = Modifier.clickable {
                            scope.launch { drawerState.close() }
                            navigateToShortcut(shortcut)
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Divider()

                // Logout
                ListItem(
                    headlineContent = { Text("Logout") },
                    leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch { drawerState.close() }
                        performLogout()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showTopNav) {
                    TopNavigationBar(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        isHomeScreen = isHomeScreen,
                        onAddBlock = homeOnAddBlock,
                        onRefresh = homeOnRefresh,
                        isRefreshing = homeIsRefreshing
                    )
                }
            },
            bottomBar = {
                if (showBottomNav) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { destination ->
                            val route = when (destination) {
                                BottomNavDestination.HOME -> Screen.Home.route
                                BottomNavDestination.CHAT -> Screen.Chat.route
                                BottomNavDestination.EMPLOYMENT -> Screen.Employment.route
                                BottomNavDestination.COMPANY_PORTAL -> Screen.CompanyPortal.route
                                BottomNavDestination.TOOL_SHED -> Screen.ToolShed.route
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
                    val viewModel = remember { LoginViewModel(deps.authRepository) }
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
                    val viewModel = remember { SignupViewModel(deps.authRepository) }
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
                    HomeScreen(
                        viewModel = deps.homeViewModel,
                        chatRepository = deps.chatRepository,
                        tokenManager = deps.tokenManager,
                        onLogout = {
                            // Stop chat service
                            val serviceIntent = Intent(context, ChatService::class.java).apply {
                                action = ChatService.ACTION_STOP
                            }
                            context.startService(serviceIntent)
                            deps.socketManager.disconnect()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        onRegisterActions = { onAddBlock, onRefresh ->
                            homeOnAddBlock = onAddBlock
                            homeOnRefresh = onRefresh
                        },
                        onUpdateRefreshing = { refreshing ->
                            homeIsRefreshing = refreshing
                        }
                    )
                }

                composable(Screen.Blog.route) {
                    BlogScreen(
                        viewModel = deps.blogViewModel,
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
                        viewModel = deps.blogViewModel,
                        postId = postId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Chat.route) {
                    val chatViewModel = remember {
                        ChatViewModel(deps.chatRepository, currentUserId.intValue)
                    }
                    ChatScreen(viewModel = chatViewModel)
                }

                composable(Screen.Friends.route) {
                    FriendsScreen(viewModel = deps.friendsViewModel)
                }

                composable(Screen.Profile.route) {
                    // Reload profile data when screen is navigated to
                    LaunchedEffect(Unit) {
                        deps.profileViewModel.loadProfile()
                    }
                    ProfileScreen(
                        viewModel = deps.profileViewModel,
                        onLoggedOut = {
                            // Stop chat service
                            val serviceIntent = Intent(context, ChatService::class.java).apply {
                                action = ChatService.ACTION_STOP
                            }
                            context.startService(serviceIntent)
                            deps.socketManager.disconnect()
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                    )
                }

                // Bottom nav screens
                composable(Screen.About.route) {
                    AboutScreen()
                }

                composable(Screen.ToolShed.route) {
                    ToolShedScreen(
                        viewModel = deps.toolShedViewModel,
                        onNavigateToTool = { tool ->
                            when (tool.route) {
                                "/lyrics" -> navController.navigate(Screen.LyricLab.route)
                                "/art-gallery" -> navController.navigate(Screen.ArtGallery.route)
                                "/blog" -> navController.navigate(Screen.Blog.route)
                                "/kanban" -> navController.navigate(Screen.KanbanRedirect.route)
                            }
                        },
                        onShortcutsChanged = {
                            // Reload shortcuts in the drawer
                            scope.launch {
                                when (val result = deps.breakroomRepository.loadShortcuts()) {
                                    is BreakroomResult.Success -> {
                                        shortcuts.clear()
                                        shortcuts.addAll(result.data)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    )
                }

                composable(Screen.ArtGallery.route) {
                    LaunchedEffect(Unit) {
                        deps.artGalleryViewModel.loadData()
                    }
                    ArtGalleryScreen(viewModel = deps.artGalleryViewModel)
                }

                composable(Screen.KanbanRedirect.route) {
                    val kanbanRepository = remember { KanbanRepository(RetrofitClient.breakroomApiService, deps.tokenManager) }
                    val viewModel = remember { KanbanRedirectViewModel(kanbanRepository) }
                    KanbanRedirectScreen(
                        viewModel = viewModel,
                        onNavigateToBoard = { projectId, projectTitle ->
                            navController.navigate(Screen.KanbanBoard.createRoute(projectId, projectTitle)) {
                                popUpTo(Screen.KanbanRedirect.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.KanbanBoard.route,
                    arguments = listOf(
                        navArgument("projectId") { type = NavType.IntType },
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = "Kanban"
                        }
                    )
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getInt("projectId") ?: 0
                    val encodedTitle = backStackEntry.arguments?.getString("title") ?: "Kanban"
                    val projectTitle = try {
                        java.net.URLDecoder.decode(encodedTitle, "UTF-8")
                    } catch (e: Exception) {
                        encodedTitle
                    }
                    val kanbanRepository = remember { KanbanRepository(RetrofitClient.breakroomApiService, deps.tokenManager) }
                    val viewModel = remember(projectId) { KanbanBoardViewModel(kanbanRepository, projectId, projectTitle) }
                    KanbanBoardScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.LyricLab.route) {
                    LaunchedEffect(Unit) {
                        deps.lyricLabViewModel.loadData()
                    }
                    LyricLabScreen(
                        viewModel = deps.lyricLabViewModel,
                        onNavigateToSong = { songId ->
                            navController.navigate(Screen.SongDetail.createRoute(songId))
                        }
                    )
                }

                composable(
                    route = Screen.SongDetail.route,
                    arguments = listOf(
                        navArgument("songId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val songId = backStackEntry.arguments?.getInt("songId") ?: 0
                    val songDetailViewModel = remember(songId) {
                        SongDetailViewModel(deps.lyricsRepository, songId)
                    }
                    SongDetailScreen(
                        viewModel = songDetailViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Employment.route) {
                    // Reload employment data when screen is navigated to
                    LaunchedEffect(Unit) {
                        deps.employmentViewModel.loadPositions()
                    }
                    EmploymentScreen(viewModel = deps.employmentViewModel)
                }

                composable(Screen.HelpDesk.route) {
                    // Reload help desk data when screen is navigated to
                    LaunchedEffect(Unit) {
                        deps.helpDeskViewModel.loadData()
                    }
                    HelpDeskScreen(viewModel = deps.helpDeskViewModel)
                }

                composable(Screen.CompanyPortal.route) {
                    // Reload company data when screen is navigated to
                    LaunchedEffect(Unit) {
                        deps.companyPortalViewModel.loadMyCompanies()
                    }
                    CompanyPortalScreen(
                        viewModel = deps.companyPortalViewModel,
                        onNavigateToCompany = { company ->
                            navController.navigate(Screen.Company.createRoute(company.id, company.name))
                        }
                    )
                }

                composable(
                    route = Screen.Company.route,
                    arguments = listOf(
                        navArgument("companyId") { type = NavType.IntType },
                        navArgument("companyName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val companyId = backStackEntry.arguments?.getInt("companyId") ?: 0
                    val companyName = backStackEntry.arguments?.getString("companyName") ?: ""
                    val companyViewModel = remember(companyId) {
                        CompanyViewModel(deps.companyRepository, deps.breakroomRepository, companyId)
                    }
                    CompanyScreen(
                        viewModel = companyViewModel,
                        companyName = companyName,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToProjectTickets = { projectId, projectName ->
                            navController.navigate(Screen.ProjectTickets.createRoute(projectId, projectName))
                        },
                        onShortcutsChanged = {
                            scope.launch {
                                when (val result = deps.breakroomRepository.loadShortcuts()) {
                                    is BreakroomResult.Success -> {
                                        shortcuts.clear()
                                        shortcuts.addAll(result.data)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    )
                }

                composable(
                    route = Screen.ProjectTickets.route,
                    arguments = listOf(
                        navArgument("projectId") { type = NavType.IntType },
                        navArgument("projectName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getInt("projectId") ?: 0
                    val encodedProjectName = backStackEntry.arguments?.getString("projectName") ?: ""
                    val projectName = try {
                        java.net.URLDecoder.decode(encodedProjectName, "UTF-8")
                    } catch (e: Exception) {
                        encodedProjectName
                    }
                    val projectTicketsViewModel = remember(projectId) {
                        ProjectTicketsViewModel(deps.companyRepository, projectId, deps.tokenManager.getUsername() ?: "")
                    }
                    ProjectTicketsScreen(
                        viewModel = projectTicketsViewModel,
                        projectName = projectName,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
