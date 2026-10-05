package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.ProjectMember

private fun weeksLabel(days: Int) = "${days / 7} week${if (days == 7) "" else "s"}"

// Workspace Settings section (web: ProjectSettingsPage.vue)
@Composable
fun ProjectSettingsScreen(
    viewModel: ProjectSettingsViewModel,
    onLeftProject: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var confirmRemove by remember { mutableStateOf<ProjectMember?>(null) }

    LaunchedEffect(state.leftProject) {
        if (state.leftProject) {
            viewModel.onLeftProjectHandled()
            onLeftProject()
        }
    }

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
        }
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("project-settings"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Project Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SprintCard(state = state, viewModel = viewModel)
            MembersCard(state = state, viewModel = viewModel, onRemove = { confirmRemove = it })
        }
    }

    confirmRemove?.let { member ->
        val isSelf = member.user_id == state.currentUserId
        val prompt = when {
            member.isInvited -> "Cancel the invite for ${member.displayName}?"
            isSelf -> "Leave this project?"
            else -> "Remove ${member.displayName} from this project?"
        }
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            text = { Text(prompt) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    viewModel.removeMember(member)
                }) { Text(state.removeLabel(member), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SprintCard(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sprints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Sprint duration", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionDropdown(
                    label = state.sprintDays?.let { weeksLabel(it) } ?: "",
                    options = state.sprintDurations,
                    optionLabel = { weeksLabel(it) },
                    enabled = state.canManage && !state.savingSettings,
                    onSelect = { viewModel.chooseSprintDays(it) },
                    modifier = Modifier.weight(1f)
                )
                if (state.canManage) {
                    Button(
                        onClick = { viewModel.saveSettings() },
                        enabled = !state.savingSettings && state.sprintDays != state.savedSprintDays
                    ) { Text(if (state.savingSettings) "Saving..." else "Save") }
                }
            }
            state.settingsMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            state.settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!state.canManage) {
                Text(
                    "Only project owners and managers can change settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MembersCard(
    state: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel,
    onRemove: (ProjectMember) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Company employees can always open this project. Membership gives people outside " +
                    "the company access and decides who manages the project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.memberError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (state.members.isEmpty()) {
                Text("No members yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.members.forEach { member ->
                MemberRow(
                    member = member,
                    state = state,
                    onChangeRole = { viewModel.changeRole(member, it) },
                    onRemove = { onRemove(member) }
                )
                HorizontalDivider()
            }

            if (state.canManage) {
                InviteForm(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: ProjectMember,
    state: ProjectSettingsUiState,
    onChangeRole: (String) -> Unit,
    onRemove: () -> Unit
) {
    val busy = state.busyMember == member.user_id
    Column(
        modifier = Modifier.testTag("project-member-${member.user_id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.displayName + if (member.user_id == state.currentUserId) " (you)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                member.handle?.let {
                    Text("@$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (member.isInvited) MemberBadge("Invite pending", Color(0xFFFD7E14))
            if (member.is_employee) {
                Spacer(modifier = Modifier.width(4.dp))
                MemberBadge("Employee", Color(0xFF6F42C1))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canEditMember(member)) {
                // Keep the member's current role listed even if the user
                // can't hand it out (e.g. an owner seen by a manager)
                val options = state.assignableRoles.let { if (member.role in it) it else it + member.role }
                OptionDropdown(
                    label = ProjectRoleText.label(member.role),
                    options = options,
                    optionLabel = { ProjectRoleText.label(it) },
                    enabled = !busy,
                    onSelect = onChangeRole,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ProjectRoleText.label(member.role), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ProjectRoleText.hint(member.role),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.canRemoveMember(member)) {
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(state.removeLabel(member)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteForm(state: ProjectSettingsUiState, viewModel: ProjectSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Invite someone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        // Typing 2+ characters suggests people (company employees first);
        // picking fills the handle, and full handles/emails still work
        ExposedDropdownMenuBox(
            expanded = state.inviteSuggestions.isNotEmpty(),
            onExpandedChange = { if (!it) viewModel.dismissInviteSuggestions() }
        ) {
            OutlinedTextField(
                value = state.inviteIdentifier,
                onValueChange = { viewModel.updateInviteIdentifier(it) },
                label = { Text("Handle or email") },
                singleLine = true,
                enabled = !state.inviting,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .testTag("project-invite-identifier")
            )
            ExposedDropdownMenu(
                expanded = state.inviteSuggestions.isNotEmpty(),
                onDismissRequest = { viewModel.dismissInviteSuggestions() }
            ) {
                state.inviteSuggestions.forEach { user ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(user.fullName.ifEmpty { user.handle }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "@${user.handle}" + if (user.in_company) " \u00B7 in this company" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = { viewModel.pickInviteSuggestion(user) },
                        modifier = Modifier.testTag("invite-suggestion-${user.user_id}")
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionDropdown(
                label = ProjectRoleText.label(state.inviteRole),
                options = state.assignableRoles,
                optionLabel = { ProjectRoleText.label(it) },
                enabled = !state.inviting,
                onSelect = { viewModel.updateInviteRole(it) },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.sendInvite() },
                enabled = !state.inviting && state.inviteIdentifier.isNotBlank()
            ) { Text(if (state.inviting) "Sending..." else "Send Invite") }
        }
        Text(
            "${ProjectRoleText.label(state.inviteRole)}: ${ProjectRoleText.hint(state.inviteRole)}. " +
                "They'll get an email and can accept from their Projects page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.inviteMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        state.inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun MemberBadge(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionDropdown(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
