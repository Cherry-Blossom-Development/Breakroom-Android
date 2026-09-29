package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.Project
import com.cherryblossomdev.breakroom.data.models.ProjectInvite

// Cross-company Projects page (web: ProjectsPage.vue). Tapping a project's
// title or "View Tickets" opens its board; the company name opens the company.
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Project) -> Unit,
    onOpenCompany: (companyId: Int, companyName: String) -> Unit,
    onOpenCompanyPortal: () -> Unit,
    onShortcutsChanged: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("projects-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.invites.isNotEmpty()) {
                item {
                    InvitesCard(
                        invites = uiState.invites,
                        respondingTo = uiState.respondingTo,
                        error = uiState.inviteError,
                        onRespond = viewModel::respondToInvite
                    )
                }
            }

            when {
                uiState.isLoading && uiState.projects.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                uiState.error != null && uiState.projects.isEmpty() -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }

                else -> {
                    item {
                        ProjectsHeader(
                            activeCount = uiState.activeCount,
                            companies = uiState.companies,
                            selected = uiState.companyFilter,
                            onSelect = viewModel::setCompanyFilter
                        )
                    }

                    if (uiState.projects.isEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Text(
                                    "No projects yet. Projects belong to companies — join or create one in the Company Portal.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = onOpenCompanyPortal) { Text("Open Company Portal") }
                            }
                        }
                    } else {
                        items(uiState.filteredProjects, key = { it.id }) { project ->
                            ProjectListCard(
                                project = project,
                                hasShortcut = uiState.shortcutIds.containsKey(project.homepageUrl()),
                                isTogglingShortcut = uiState.togglingShortcutFor == project.id,
                                onOpen = { onOpenProject(project) },
                                onOpenCompany = {
                                    onOpenCompany(project.company_id, project.company_name ?: "Company")
                                },
                                onToggleShortcut = { viewModel.toggleShortcut(project, onShortcutsChanged) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun InvitesCard(
    invites: List<ProjectInvite>,
    respondingTo: Int?,
    error: String?,
    onRespond: (ProjectInvite, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project-invites"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Project Invitations (${invites.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            invites.forEach { invite ->
                val busy = respondingTo == invite.project_id
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    invite.company_name?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(invite.project_title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${invite.inviterName ?: "Someone"} invited you as a ${invite.role}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onRespond(invite, true) }, enabled = !busy) { Text("Accept") }
                        OutlinedButton(onClick = { onRespond(invite, false) }, enabled = !busy) { Text("Decline") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectsHeader(
    activeCount: Int,
    companies: List<CompanyFilterOption>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Projects ($activeCount)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (companies.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            val label = companies.firstOrNull { it.id == selected }?.name ?: "All companies"
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All companies") },
                        onClick = { onSelect(null); expanded = false }
                    )
                    companies.forEach { company ->
                        DropdownMenuItem(
                            text = { Text(company.name) },
                            onClick = { onSelect(company.id); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectListCard(
    project: Project,
    hasShortcut: Boolean,
    isTogglingShortcut: Boolean,
    onOpen: () -> Unit,
    onOpenCompany: () -> Unit,
    onToggleShortcut: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (project.isActive) 1f else 0.6f)
            .testTag("project-card-${project.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            project.company_name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenCompany)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = project.isActive, onClick = onOpen)
                )
                if (project.isActive) {
                    IconButton(onClick = onToggleShortcut, enabled = !isTogglingShortcut, modifier = Modifier.size(32.dp)) {
                        if (isTogglingShortcut) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (hasShortcut) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (hasShortcut) "Remove shortcut" else "Add shortcut",
                                modifier = Modifier.size(20.dp),
                                tint = if (hasShortcut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (project.isDefault) ProjectBadge("Default", MaterialTheme.colorScheme.tertiaryContainer)
                ProjectBadge(
                    if (project.isPublic) "Public" else "Private",
                    if (project.isPublic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
                ProjectBadge(
                    if (project.isActive) "Active" else "Inactive",
                    if (project.isActive) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            }

            if (!project.description.isNullOrBlank()) {
                Text(
                    project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    project.ticketCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (project.isActive) {
                    Button(onClick = onOpen) {
                        Icon(Icons.Outlined.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Tickets")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
