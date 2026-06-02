package com.cherryblossomdev.breakroom.navigation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cherryblossomdev.breakroom.AppContainer
import com.cherryblossomdev.breakroom.ModerationStore
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
import com.cherryblossomdev.breakroom.data.models.StoreCollection
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object ForgotPassword : Screen("forgot-password")
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
    // Sessions
    object Sessions : Screen("sessions")
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
    object Eula : Screen("eula")
    object EulaView : Screen("eula-view")
    object PrivacyPolicy : Screen("privacy-policy")
    object Legal : Screen("legal")
    object PublicProfile : Screen("user/{handle}") {
        fun createRoute(handle: String) = "user/$handle"
    }
    // Collections
    object Collections : Screen("collections")
    object CollectionDetail : Screen("collections/{collectionId}?name={name}") {
        fun createRoute(collectionId: Int, name: String): String {
            val encoded = java.net.URLEncoder.encode(name, "UTF-8")
            return "collections/$collectionId?name=$encoded"
        }
    }
    object CollectionsOrders : Screen("collections-orders")
    object CollectionsShipping : Screen("collections-shipping")
    object CollectionsPayment : Screen("collections-payment")
    object CollectionsStorefront : Screen("collections-storefront")
    object Impersonate : Screen("impersonate")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakroomNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deps = remember { AppContainer(context) }

    // Store current user ID for chat (updated after login)
    val currentUserId = remember { mutableIntStateOf(0) }

    // Determine start destination based on login state.
    // Skip EULA entirely if already accepted locally.
    val startDestination = when {
        !deps.authRepository.isLoggedIn() -> Screen.Login.route
        deps.tokenManager.isEulaAccepted() -> Screen.Home.route
        else -> Screen.Eula.route
    }

    // Badge state — collected here so it's available to bottom nav and drawer
    val badgeState by deps.badgeViewModel.state.collectAsState()

    var isAdmin by remember { mutableStateOf(false) }
    var isImpersonating by remember { mutableStateOf(deps.tokenManager.isImpersonating()) }

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

            // Load moderation block list
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                when (val result = deps.moderationRepository.getBlockList()) {
                    is BreakroomResult.Success -> ModerationStore.setBlockList(result.data)
                    else -> { /* silently fail */ }
                }
            }

            // Start chat service
            val serviceIntent = Intent(context, ChatService::class.java).apply {
                action = ChatService.ACTION_START
            }
            context.startService(serviceIntent)

            // Load badge counts
            deps.badgeViewModel.fetchAll()

            // Check admin access
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                isAdmin = deps.adminRepository.checkAdminAccess()
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""

    // Chat room selection — suppresses outer nav bars when a room is open
    var chatRoomSelected by remember { mutableStateOf(false) }

    // Check if we're on a logged-in screen (show top nav)
    val showTopNav = (currentRoute in listOf(
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
        Screen.KanbanRedirect.route,
        Screen.Sessions.route
    ) || currentRoute.startsWith("company/") || currentRoute.startsWith("project/") || currentRoute.startsWith("song/") || currentRoute.startsWith("kanban/board/") || currentRoute.startsWith("collections/")
    ) && !(currentRoute == Screen.Chat.route && chatRoomSelected)

    // Show bottom nav on main screens
    val showBottomNav = showTopNav

    val isHomeScreen = currentRoute == Screen.Home.route

    val topBarTitle = when {
        currentRoute == Screen.Chat.route -> "Chat Rooms"
        currentRoute == Screen.Profile.route -> "Profile"
        currentRoute == Screen.Friends.route -> "Friends"
        currentRoute == Screen.Blog.route -> "My Blog"
        currentRoute == Screen.Sessions.route -> "Sessions"
        currentRoute == Screen.ArtGallery.route -> "Art Gallery"
        currentRoute == Screen.ToolShed.route -> "Tool Shed"
        currentRoute == Screen.LyricLab.route -> "Lyric Lab"
        currentRoute == Screen.Employment.route -> "Jobs"
        currentRoute == Screen.CompanyPortal.route -> "Company Portal"
        currentRoute == Screen.Collections.route -> "Artist Showcase"
        currentRoute == Screen.CollectionsOrders.route -> "Artist Showcase"
        currentRoute == Screen.CollectionsShipping.route -> "Artist Showcase"
        currentRoute == Screen.CollectionsPayment.route -> "Payment Setup"
        currentRoute == Screen.CollectionsStorefront.route -> "Storefront"
        currentRoute.startsWith("collections/") -> "Artist Showcase"
        currentRoute == Screen.KanbanRedirect.route -> "Kanban"
        currentRoute.startsWith("kanban/") -> "Kanban"
        currentRoute.startsWith("project/") -> "Kanban"
        currentRoute.startsWith("company/") -> "Company Details"
        else -> "Breakroom"
    }

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

    // Hoisted ProfileScreen actions for the top bar
    var profileOnEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var profileOnRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
    var profileIsEditMode by remember { mutableStateOf(false) }

    // Hoisted generic top bar refresh (used by Friends and similar screens)
    var topBarRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Hoisted Lyric Lab top bar add action (tab-aware)
    var lyricLabOnAdd by remember { mutableStateOf<(() -> Unit)?>(null) }

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
            url == "/sessions" -> navController.navigate(Screen.Sessions.route)
            url == "/collections" -> navController.navigate(Screen.Collections.route)
        }
    }

    // Helper for logout
    fun performLogout() {
        val serviceIntent = Intent(context, ChatService::class.java).apply {
            action = ChatService.ACTION_STOP
        }
        context.startService(serviceIntent)
        deps.socketManager.disconnect()
        shortcuts.clear()
        ModerationStore.clear()
        deps.featuresRepository.clearCache()
        deps.badgeViewModel.reset()
        deps.tokenManager.clearImpersonation()
        isImpersonating = false
        isAdmin = false
        scope.launch {
            deps.authRepository.logout()
        }
        navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Home.route) { inclusive = true }
        }
    }

    // Helper to stop impersonating
    fun stopImpersonating() {
        scope.launch {
            deps.adminRepository.stopImpersonation()
            isImpersonating = false
        }
    }

    // Reload user-specific data after a successful login
    fun reloadAfterLogin() {
        deps.featuresRepository.clearCache()
        deps.badgeViewModel.fetchAll()
        scope.launch {
            isAdmin = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                deps.adminRepository.checkAdminAccess()
            }
        }
        scope.launch {
            when (val result = deps.breakroomRepository.loadShortcuts()) {
                is BreakroomResult.Success -> {
                    shortcuts.clear()
                    shortcuts.addAll(result.data)
                }
                else -> { shortcuts.clear() }
            }
        }
        scope.launch {
            when (val result = deps.moderationRepository.getBlockList()) {
                is BreakroomResult.Success -> ModerationStore.setBlockList(result.data)
                else -> { /* silently fail */ }
            }
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
                    trailingContent = {
                        if (badgeState.totalChatUnread > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(badgeState.totalChatUnread.toString())
                            }
                        }
                    },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Chat.route) }
                )
                ListItem(
                    headlineContent = { Text("Friends") },
                    leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                    trailingContent = {
                        if (badgeState.friendRequestsUnread > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(badgeState.friendRequestsUnread.toString())
                            }
                        }
                    },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Friends.route) }
                )
                ListItem(
                    headlineContent = { Text("Blog") },
                    leadingContent = { Icon(Icons.Outlined.Article, contentDescription = null) },
                    trailingContent = {
                        if (badgeState.blogCommentsUnread > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(badgeState.blogCommentsUnread.toString())
                            }
                        }
                    },
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
                        headlineContent = { Text(if (shortcut.url == "/collections") "Artist Showcase" else shortcut.name) },
                        modifier = Modifier.clickable {
                            scope.launch { drawerState.close() }
                            navigateToShortcut(shortcut)
                        }
                    )
                }

                if (isAdmin) {
                    ListItem(
                        headlineContent = { Text("Impersonate User") },
                        leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                        modifier = Modifier.clickable { drawerNavigate(Screen.Impersonate.route) }
                    )
                }

                ListItem(
                    headlineContent = { Text("Legal") },
                    leadingContent = { Icon(Icons.Default.Gavel, contentDescription = null) },
                    modifier = Modifier.clickable { drawerNavigate(Screen.Legal.route) }
                )

                Spacer(modifier = Modifier.weight(1f))

                Divider()

                // Logout
                ListItem(
                    headlineContent = { Text("Logout") },
                    leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    modifier = Modifier
                        .testTag("drawer-logout-item")
                        .clickable {
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
                    Column {
                        if (isImpersonating) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFCA28))
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Impersonating: ${deps.tokenManager.getImpersonatedHandle()}",
                                    color = Color.Black,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { stopImpersonating() }) {
                                    Text("Stop", color = Color.Black)
                                }
                            }
                        }
                        TopNavigationBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            title = topBarTitle,
                            isHomeScreen = isHomeScreen,
                            onAddBlock = homeOnAddBlock,
                            onRefresh = homeOnRefresh,
                            isRefreshing = homeIsRefreshing,
                            isProfileScreen = currentRoute == Screen.Profile.route,
                            onProfileEdit = profileOnEdit,
                            onProfileRefresh = profileOnRefresh,
                            profileIsEditMode = profileIsEditMode,
                            onTopBarRefresh = topBarRefresh,
                            onAdd = lyricLabOnAdd,
                            windowInsets = WindowInsets(0)
                        )
                    }
                }
            },
            bottomBar = {
                if (showBottomNav) {
                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        chatUnread = badgeState.totalChatUnread,
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
            },
            contentWindowInsets = WindowInsets(0),
            modifier = Modifier.statusBarsPadding()
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
                        onNavigateToForgotPassword = {
                            navController.navigate(Screen.ForgotPassword.route)
                        },
                        onLoginSuccess = { userId ->
                            currentUserId.intValue = userId
                            reloadAfterLogin()
                            deps.homeViewModel.loadData()
                            // Start chat service
                            val serviceIntent = Intent(context, ChatService::class.java).apply {
                                action = ChatService.ACTION_START
                            }
                            context.startService(serviceIntent)
                            val dest = if (deps.tokenManager.isEulaAccepted()) Screen.Home.route else Screen.Eula.route
                            navController.navigate(dest) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.ForgotPassword.route) {
                    val viewModel = remember { ForgotPasswordViewModel(deps.authRepository) }
                    ForgotPasswordScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = {
                            navController.popBackStack()
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
                            reloadAfterLogin()
                            // Start chat service
                            val serviceIntent = Intent(context, ChatService::class.java).apply {
                                action = ChatService.ACTION_START
                            }
                            context.startService(serviceIntent)
                            navController.navigate(Screen.Eula.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Eula.route) {
                    val viewModel = remember { EulaViewModel(deps.authRepository) }
                    EulaScreen(
                        viewModel = viewModel,
                        onAccepted = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Eula.route) { inclusive = true }
                            }
                        },
                        onNavigateToPrivacyPolicy = {
                            navController.navigate(Screen.PrivacyPolicy.route)
                        }
                    )
                }

                composable(Screen.PrivacyPolicy.route) {
                    PrivacyPolicyScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(Screen.Home.route) {
                    LaunchedEffect(Unit) {
                        deps.badgeViewModel.markAllRoomsRead()
                    }
                    HomeScreen(
                        viewModel = deps.homeViewModel,
                        chatRepository = deps.chatRepository,
                        tokenManager = deps.tokenManager,
                        moderationRepository = deps.moderationRepository,
                        onNavigateToProfile = { handle ->
                            navController.navigate(Screen.PublicProfile.createRoute(handle))
                        },
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
                    val badgeStateLocal by deps.badgeViewModel.state.collectAsState()
                    BlogScreen(
                        viewModel = deps.blogViewModel,
                        blogUnreadByPost = badgeStateLocal.blogUnreadByPost,
                        onMarkBlogPostRead = { postId -> deps.badgeViewModel.markBlogPostRead(postId) },
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
                    ChatScreen(
                        viewModel = chatViewModel,
                        token = deps.tokenManager.getBearerToken(),
                        moderationRepository = deps.moderationRepository,
                        onNavigateToProfile = { handle ->
                            navController.navigate(Screen.PublicProfile.createRoute(handle))
                        },
                        onMarkRoomRead = { roomId -> deps.badgeViewModel.markRoomRead(roomId) },
                        onRoomSelectionChanged = { selected -> chatRoomSelected = selected }
                    )
                }

                composable(Screen.Friends.route) {
                    LaunchedEffect(Unit) {
                        deps.badgeViewModel.markFriendsRead()
                        topBarRefresh = { deps.friendsViewModel.loadAll() }
                    }
                    DisposableEffect(Unit) {
                        onDispose { topBarRefresh = null }
                    }
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
                        },
                        onRegisterActions = { onEdit, onRefresh ->
                            profileOnEdit = onEdit
                            profileOnRefresh = onRefresh
                        },
                        onEditModeChanged = { isEditMode ->
                            profileIsEditMode = isEditMode
                        }
                    )
                }

                composable(Screen.Legal.route) {
                    val viewModel = remember { LegalViewModel(deps.authRepository) }
                    LegalScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEula = { navController.navigate(Screen.EulaView.route) },
                        onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
                    )
                }

                composable(Screen.EulaView.route) {
                    val viewModel = remember { EulaViewModel(deps.authRepository) }
                    EulaScreen(
                        viewModel = viewModel,
                        onAccepted = { navController.popBackStack() },
                        onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                        viewOnly = true
                    )
                }

                composable(Screen.PublicProfile.route) { backStackEntry ->
                    val handle = backStackEntry.arguments?.getString("handle") ?: ""
                    val viewModel = remember(handle) {
                        PublicProfileViewModel(deps.profileRepository, handle)
                    }
                    PublicProfileScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        moderationRepository = deps.moderationRepository,
                        currentUserId = currentUserId.intValue
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
                                "/sessions" -> navController.navigate(Screen.Sessions.route)
                                "/collections" -> navController.navigate(Screen.Collections.route)
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
                        },
                        onSetTopBarAdd = { lyricLabOnAdd = it }
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

                composable(Screen.Sessions.route) {
                    LaunchedEffect(Unit) {
                        deps.sessionsViewModel.loadAll()
                    }
                    SessionsScreen(
                        viewModel = deps.sessionsViewModel,
                        subscriptionViewModel = deps.subscriptionViewModel
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
                        ProjectTicketsViewModel(deps.companyRepository, deps.helpDeskRepository, projectId, deps.tokenManager.getUsername() ?: "")
                    }
                    ProjectTicketsScreen(
                        viewModel = projectTicketsViewModel,
                        projectName = projectName,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ── Collections ───────────────────────────────────────────────

                composable(Screen.Collections.route) {
                    val viewModel = remember { CollectionsViewModel(deps.collectionsRepository) }
                    CollectionsScreen(
                        viewModel = viewModel,
                        onNavigateToCollection = { collection ->
                            navController.navigate(
                                Screen.CollectionDetail.createRoute(collection.id, collection.name)
                            )
                        },
                        onNavigateToOrders = { navController.navigate(Screen.CollectionsOrders.route) },
                        onNavigateToShipping = { navController.navigate(Screen.CollectionsShipping.route) },
                        onNavigateToPayment = { navController.navigate(Screen.CollectionsPayment.route) },
                        onNavigateToStorefront = { navController.navigate(Screen.CollectionsStorefront.route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.CollectionDetail.route,
                    arguments = listOf(
                        navArgument("collectionId") { type = NavType.IntType },
                        navArgument("name") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = "Collection"
                        }
                    )
                ) { backStackEntry ->
                    val collectionId = backStackEntry.arguments?.getInt("collectionId") ?: 0
                    val encodedName = backStackEntry.arguments?.getString("name") ?: "Collection"
                    val collectionName = try {
                        java.net.URLDecoder.decode(encodedName, "UTF-8")
                    } catch (e: Exception) { encodedName }
                    val viewModel = remember(collectionId) {
                        CollectionDetailViewModel(deps.collectionsRepository, collectionId, collectionName)
                    }
                    CollectionDetailScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.CollectionsOrders.route) {
                    val viewModel = remember { CollectionsOrdersViewModel(deps.collectionsRepository) }
                    CollectionsOrdersScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                }

                composable(Screen.CollectionsShipping.route) {
                    val viewModel = remember { CollectionsShippingViewModel(deps.collectionsRepository) }
                    CollectionsShippingScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                }

                composable(Screen.CollectionsPayment.route) {
                    val viewModel = remember { CollectionsPaymentViewModel(deps.collectionsRepository) }
                    CollectionsPaymentScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                }

                composable(Screen.CollectionsStorefront.route) {
                    val viewModel = remember { CollectionsStorefrontViewModel(deps.collectionsRepository) }
                    CollectionsStorefrontScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                }

                // ── Admin ─────────────────────────────────────────────────────

                composable(Screen.Impersonate.route) {
                    val viewModel = remember { ImpersonateViewModel(deps.adminRepository) }
                    ImpersonateScreen(
                        viewModel = viewModel,
                        onImpersonated = {
                            isImpersonating = true
                            reloadAfterLogin()
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
