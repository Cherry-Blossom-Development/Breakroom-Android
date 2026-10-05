package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.EstimateUnits
import com.cherryblossomdev.breakroom.data.models.SplitModes
import com.cherryblossomdev.breakroom.data.models.SplitSubtask
import com.cherryblossomdev.breakroom.data.models.Ticket

private class SubtaskRow(title: String = "", amount: String = "", unit: String) {
    var title by mutableStateOf(title)
    var amount by mutableStateOf(amount)
    var unit by mutableStateOf(unit)
}

// Split a ticket into subtasks (migration 086; web SplitTicketDialog.vue).
// Subtask estimates start as the parent's estimate divided evenly (same
// unit) and stay editable; once any estimate is edited by hand, adding or
// removing rows stops re-dividing. A ticket that's already split can be
// split again to add subtasks: its mode is fixed and nothing is
// pre-divided. Subtasks can be split too.
@Composable
fun SplitTicketDialog(
    ticket: Ticket,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSplit: (mode: String, subtasks: List<SplitSubtask>) -> Unit
) {
    val alreadySplit = ticket.isSplit
    var mode by remember { mutableStateOf(ticket.split_mode ?: SplitModes.HIDDEN) }
    val parentHasEstimate = !alreadySplit && ticket.hasEstimate
    val parentUnit = ticket.estimate_unit ?: "hours"
    val minRows = if (alreadySplit) 1 else 2
    val rows = remember {
        mutableStateListOf<SubtaskRow>().apply { repeat(minRows) { add(SubtaskRow(unit = parentUnit)) } }
    }
    var estimatesEdited by remember { mutableStateOf(false) }

    // Re-divide the parent's estimate whenever the row count changes
    LaunchedEffect(rows.size) {
        if (parentHasEstimate && !estimatesEdited) {
            val parts = EstimateUnits.splitEvenly(ticket.estimateAmount, rows.size)
            rows.forEachIndexed { i, r ->
                r.amount = parts[i]
                r.unit = parentUnit
            }
        }
    }

    // Total of the subtask estimates, when they share one unit
    val filled = rows.filter { it.amount.trim().toDoubleOrNull() != null }
    val subtaskTotal = when {
        filled.isEmpty() -> ""
        filled.map { it.unit }.toSet().size > 1 -> "mixed units"
        else -> {
            val sum = Math.round(filled.sumOf { it.amount.trim().toDouble() } * 100) / 100.0
            Ticket(
                id = 0, company_id = 0, creator_id = 0, title = "",
                estimate_amount = sum.toString(), estimate_unit = filled.first().unit
            ).formattedEstimate
        }
    }
    val estimateError = rows.firstNotNullOfOrNull { EstimateUnits.validate(it.amount) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(if (alreadySplit) "Add subtasks" else "Split into subtasks") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    buildString {
                        append("#${ticket.id} ${ticket.title}")
                        if (ticket.hasEstimate) append(" · estimate ${ticket.formattedEstimate}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "What happens to #${ticket.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(Modifier.selectableGroup()) {
                    ModeOption(
                        selected = mode == SplitModes.HIDDEN,
                        enabled = !alreadySplit && !isSubmitting,
                        title = "Hide it",
                        detail = "Off every board and chart; still reachable from each subtask's link.",
                        onSelect = { mode = SplitModes.HIDDEN }
                    )
                    ModeOption(
                        selected = mode == SplitModes.CATEGORY,
                        enabled = !alreadySplit && !isSubmitting,
                        title = "Make it a category",
                        detail = "Off the Kanban board, but shown over its subtasks on the GANTT chart and as a Burndown filter.",
                        onSelect = { mode = SplitModes.CATEGORY }
                    )
                }
                if (alreadySplit) {
                    Text(
                        "Already split — new subtasks join the existing " +
                            if (ticket.isCategory) "category." else "hidden parent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("Subtasks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                rows.forEachIndexed { i, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.title,
                                onValueChange = { row.title = it.take(255) },
                                placeholder = { Text("Subtask ${i + 1}") },
                                singleLine = true,
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Subtask ${i + 1} title" }
                                    .testTag("split-subtask-title-$i")
                            )
                            IconButton(
                                onClick = { rows.removeAt(i) },
                                enabled = rows.size > minRows && !isSubmitting
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove subtask ${i + 1}")
                            }
                        }
                        EstimateInput(
                            amount = row.amount,
                            unit = row.unit,
                            enabled = !isSubmitting,
                            placeholder = "No estimate",
                            onAmountChange = {
                                row.amount = it
                                estimatesEdited = true
                            },
                            onUnitChange = {
                                row.unit = it
                                estimatesEdited = true
                            }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { rows.add(SubtaskRow(unit = parentUnit)) },
                        enabled = rows.size < SplitModes.MAX_SUBTASKS && !isSubmitting,
                        modifier = Modifier.testTag("split-add-row")
                    ) {
                        Text("+ Add subtask")
                    }
                    if (subtaskTotal.isNotEmpty()) {
                        Text(
                            "Total $subtaskTotal" + if (parentHasEstimate) " (parent: ${ticket.formattedEstimate})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSplit(mode, rows.map { r ->
                        val amount = r.amount.trim().toDoubleOrNull()
                        SplitSubtask(
                            title = r.title.trim(),
                            estimate_amount = amount,
                            estimate_unit = r.unit.takeIf { amount != null }
                        )
                    })
                },
                enabled = !isSubmitting && estimateError == null,
                modifier = Modifier.testTag("split-submit")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (alreadySplit) "Add subtasks" else "Split")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

@Composable
private fun ModeOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    detail: String,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
