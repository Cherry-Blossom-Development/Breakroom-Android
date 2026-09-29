package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.Project
import kotlinx.coroutines.launch

private fun ProjectSection.icon(): ImageVector = when (this) {
    ProjectSection.KANBAN -> Icons.Outlined.ViewColumn
    ProjectSection.GANTT -> Icons.Outlined.TableChart
    ProjectSection.BURNDOWN -> Icons.Outlined.BarChart
    ProjectSection.SETTINGS -> Icons.Outlined.Settings
}

// Project-level workspace (web: ProjectWorkspacePage.vue). Replaces the app's
// top bar, bottom bar and drawer with its own title bar and project menu;
// Back returns to wherever the user came from. The selected section is kept
// in-screen (not on the back stack), matching web where switching tabs
// doesn't change where Back goes.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectWorkspaceScreen(
    viewModel: ProjectWorkspaceViewModel,
    initialSection: ProjectSection,
    onBack: () -> Unit,
    kanbanContent: @Composable (Project) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var sectionRoute by rememberSaveable { mutableStateOf(initialSection.route) }
    val section = ProjectSection.fromRoute(sectionRoute)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        "Project",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                    )
                    val menuItem: @Composable (ProjectSection) -> Unit = { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            icon = { Icon(item.icon(), contentDescription = null) },
                            selected = item == section,
                            onClick = {
                                sectionRoute = item.route
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .testTag("project-menu-${item.route}")
                        )
                    }
                    listOf(ProjectSection.KANBAN, ProjectSection.GANTT, ProjectSection.BURNDOWN).forEach { menuItem(it) }
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    // Pinned to the bottom, as on web
                    menuItem(ProjectSection.SETTINGS)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    // NavGraph's root Scaffold already pads for the system bars
                    windowInsets = WindowInsets(0),
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("project-workspace-back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                uiState.project?.title ?: if (uiState.error != null) "Project" else "Loading...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = uiState.project?.let { p ->
                                listOfNotNull(p.company_name, section.label).joinToString(" · ")
                            }
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            enabled = uiState.project != null,
                            modifier = Modifier.testTag("project-menu-toggle")
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Project menu")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val project = uiState.project
                when {
                    uiState.error != null -> WorkspaceMessage(
                        message = uiState.error ?: "",
                        isError = true,
                        actionLabel = "Back",
                        onAction = onBack
                    )
                    project == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> when (section) {
                        ProjectSection.KANBAN -> kanbanContent(project)
                        ProjectSection.GANTT,
                        ProjectSection.BURNDOWN,
                        ProjectSection.SETTINGS -> WorkspaceMessage(
                            message = "${section.label} isn't available in the Android app yet.",
                            isError = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceMessage(
    message: String,
    isError: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
