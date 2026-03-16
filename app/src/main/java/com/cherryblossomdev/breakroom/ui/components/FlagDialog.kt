package com.cherryblossomdev.breakroom.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cherryblossomdev.breakroom.data.models.FlagRequest
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Self-contained flag/report dialog. Calls the moderation API directly.
 * @param token Bearer token ("Bearer <token>")
 * @param contentType One of: post, comment, chat_message, artwork, lyrics, user, other
 * @param contentId Nullable — required for all types except "other"
 * @param onDismiss Called when the dialog is dismissed (cancel or after successful submit)
 * @param onFlagged Called after a successful flag submission
 */
@Composable
fun FlagDialog(
    token: String,
    contentType: String,
    contentId: Int?,
    onDismiss: () -> Unit,
    onFlagged: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Report Content") },
        text = {
            Column {
                Text(
                    text = "This content will be hidden and reviewed by our moderation team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    placeholder = { Text("Describe the issue...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !submitting
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = null
                    submitting = true
                    scope.launch {
                        try {
                            val response = RetrofitClient.breakroomApiService.flagContent(
                                token = token,
                                request = FlagRequest(
                                    content_type = contentType,
                                    content_id = contentId,
                                    reason = reason.trim().ifEmpty { null }
                                )
                            )
                            if (response.isSuccessful) {
                                onFlagged()
                                onDismiss()
                            } else {
                                error = "Failed to submit report. Please try again."
                            }
                        } catch (e: Exception) {
                            error = "Network error. Please try again."
                        } finally {
                            submitting = false
                        }
                    }
                },
                enabled = !submitting
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Report", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!submitting) onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}
