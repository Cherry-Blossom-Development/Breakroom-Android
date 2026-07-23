package com.cherryblossomdev.breakroom.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cherryblossomdev.breakroom.data.models.ScheduledMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    viewModel: ScheduledMessagesViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateDisplayFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.US) }

    LaunchedEffect(state.error) {
        if (state.error != null) kotlinx.coroutines.delay(4000).also { viewModel.clearError() }
    }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) kotlinx.coroutines.delay(2000).also { viewModel.clearSuccess() }
    }

    // Date/time pickers live outside the dialog so they layer above it
    if (showDatePicker) {
        val cal = state.formScheduledDate
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = cal.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Calendar.getInstance().apply { timeInMillis = millis }
                        viewModel.updateFormDate(
                            selected.get(Calendar.YEAR),
                            selected.get(Calendar.MONTH),
                            selected.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = state.formScheduledDate
        DisposableEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute -> viewModel.updateFormTime(hour, minute) },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
            )
            dialog.show()
            onDispose { dialog.dismiss() }
        }
        showTimePicker = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.messages.isNotEmpty()) {
                        items(state.messages, key = { it.id }) { msg ->
                            ScheduledMessageCard(
                                message = msg,
                                onEdit = { viewModel.startEditing(msg) },
                                onCancel = { viewModel.cancelMessage(msg.id) }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "No messages scheduled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::openCreateDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create new Message")
            }
        }

        // Error / success snackbars
        if (state.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer
            ) { Text(state.error!!, color = MaterialTheme.colorScheme.onErrorContainer) }
        }
        if (state.successMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            ) { Text(state.successMessage!!) }
        }

        // Form dialog
        if (state.showFormDialog) {
            ScheduledMessageFormDialog(
                state = state,
                viewModel = viewModel,
                onShowDatePicker = { showDatePicker = true },
                onShowTimePicker = { showTimePicker = true },
                dateDisplayFmt = dateDisplayFmt,
                timeFmt = timeFmt
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledMessageFormDialog(
    state: ScheduledMessagesUiState,
    viewModel: ScheduledMessagesViewModel,
    onShowDatePicker: () -> Unit,
    onShowTimePicker: () -> Unit,
    dateDisplayFmt: SimpleDateFormat,
    timeFmt: SimpleDateFormat
) {
    Dialog(onDismissRequest = viewModel::cancelEditing) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (state.editingId != null) "Edit Scheduled Message" else "Schedule a Message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Room selector
                var roomExpanded by remember { mutableStateOf(false) }
                val selectedRoom = state.rooms.find { it.id == state.formRoomId }
                ExposedDropdownMenuBox(
                    expanded = roomExpanded,
                    onExpandedChange = { roomExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRoom?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Room") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = roomExpanded,
                        onDismissRequest = { roomExpanded = false }
                    ) {
                        state.rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.name) },
                                onClick = {
                                    viewModel.updateFormRoom(room.id)
                                    roomExpanded = false
                                }
                            )
                        }
                    }
                }

                // Message textarea
                OutlinedTextField(
                    value = state.formMessageText,
                    onValueChange = viewModel::updateFormText,
                    label = { Text("Message") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${state.formMessageText.length}/1000") }
                )

                // Date + time row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onShowDatePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dateDisplayFmt.format(state.formScheduledDate.time))
                    }
                    OutlinedButton(
                        onClick = onShowTimePicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(timeFmt.format(state.formScheduledDate.time))
                    }
                }

                // Warning minutes
                OutlinedTextField(
                    value = if (state.formWarningMinutes == 0) "0" else state.formWarningMinutes.toString(),
                    onValueChange = { v ->
                        val n = v.toIntOrNull()
                        if (n != null) viewModel.updateFormWarningMinutes(n)
                        else if (v.isEmpty()) viewModel.updateFormWarningMinutes(0)
                    },
                    label = { Text("Warn me X min before (0–60)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Indicator text
                OutlinedTextField(
                    value = state.formIndicatorText,
                    onValueChange = viewModel::updateFormIndicatorText,
                    label = { Text("Indicator text (appended to message)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::setDefaultIndicator,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Default", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = viewModel::setNoIndicator,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("None", style = MaterialTheme.typography.labelSmall) }
                }

                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Submit / cancel row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = viewModel::submitForm,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (state.editingId != null) "Update Message" else "Schedule Message")
                        }
                    }
                    OutlinedButton(onClick = viewModel::cancelEditing) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageCard(
    message: ScheduledMessage,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val amberColor = Color(0xFFED8936)
    val isWarningSoon = message.status == "warning_sent"
    val isEditingPaused = message.is_editing == 1

    val containerColor = when {
        isWarningSoon -> Color(0x17ED8936)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusLabel = when {
                message.is_editing == 1 -> "Editing paused"
                message.status == "warning_sent" -> "Sending soon"
                message.status == "confirmed" -> "Confirmed"
                else -> "Scheduled"
            }
            val messageLabel = remember(message.room_name, message.room_id, statusLabel, message.scheduled_at, message.message_text) {
                "#${message.room_name ?: message.room_id}. $statusLabel. ${formatScheduledAt(message.scheduled_at)}. ${message.message_text}"
            }
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = messageLabel }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "#${message.room_name ?: message.room_id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusBadge(message)
                }

                Text(
                    text = formatScheduledAt(message.scheduled_at),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = message.message_text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.wrapContentWidth(),
                    enabled = message.status != "warning_sent" || isEditingPaused
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.wrapContentWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(message: ScheduledMessage) {
    val amberColor = Color(0xFFED8936)
    val (label, color) = when {
        message.is_editing == 1 -> "Editing paused" to MaterialTheme.colorScheme.error
        message.status == "warning_sent" -> "Sending soon" to amberColor
        message.status == "confirmed" -> "Confirmed" to MaterialTheme.colorScheme.primary
        else -> "Scheduled" to MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatScheduledAt(scheduledAt: String): String {
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val normalized = scheduledAt.replace(Regex("\\.\\d+"), "").removeSuffix("Z")
        val date = fmt.parse(normalized) ?: return scheduledAt
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val displayFmt = SimpleDateFormat("h:mm a", Locale.US)
        val prefix = when {
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) + 1 -> "Tomorrow"
            else -> SimpleDateFormat("MMM d", Locale.US).format(date)
        }
        "$prefix at ${displayFmt.format(date)}"
    } catch (_: Exception) {
        scheduledAt
    }
}
