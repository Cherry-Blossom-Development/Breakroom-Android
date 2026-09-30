package com.cherryblossomdev.breakroom.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherryblossomdev.breakroom.data.models.TicketAttachment
import com.cherryblossomdev.breakroom.network.RetrofitClient
import java.io.File

// Ticket attachments (migration 085), shared by the project board and the
// Help Desk (web: components/TicketAttachments.vue). Presentational: the
// caller decides whether picked files upload right away (Help Desk) or wait
// for Save Changes (board), and passes the pending state back in.

const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024
const val MAX_ATTACHMENT_FILES = 10
private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

/** A picked file that hasn't been uploaded yet. */
data class PendingFile(val uri: Uri, val name: String, val size: Long, val mimeType: String) {
    val isImage: Boolean get() = mimeType in IMAGE_TYPES
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${Math.round(bytes / 1024.0)} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

/** Name, size and type of picked content URIs. */
fun describePickedFiles(context: Context, uris: List<Uri>): List<PendingFile> = uris.mapIndexed { i, uri ->
    var name: String? = null
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    PendingFile(
        uri = uri,
        name = name ?: "file-${i + 1}",
        size = size,
        mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    )
}

/** The backend's per-file and per-upload limits, checked before sending; null if fine. */
fun attachmentLimitError(files: List<PendingFile>, alreadyPending: Int = 0): String? {
    val tooBig = files.filter { it.size > MAX_ATTACHMENT_BYTES }
    if (tooBig.isNotEmpty()) {
        return "${tooBig.joinToString(", ") { it.name }} ${if (tooBig.size == 1) "is" else "are"} over 25 MB"
    }
    if (files.size + alreadyPending > MAX_ATTACHMENT_FILES) return "Attach at most $MAX_ATTACHMENT_FILES files at a time"
    return null
}

/** Hands a downloaded attachment to whatever app opens its type. Returns an error or null. */
fun openDownloadedFile(context: Context, file: File, mimeType: String): String? {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "No app on this device can open ${file.name}"
    }
}

/** Remembers a system file picker (multiple files, any type) that reports described files. */
@Composable
fun rememberAttachmentPicker(onPicked: (List<PendingFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(describePickedFiles(context, uris))
    }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
fun TicketAttachmentsSection(
    attachments: List<TicketAttachment>,
    pendingFiles: List<PendingFile>,
    pendingRemovals: List<Int>,
    canAttach: Boolean,
    canRemove: (TicketAttachment) -> Boolean,
    busy: Boolean,
    error: String?,
    // Bearer token for image thumbnails (files are behind ticket access checks)
    authHeader: String?,
    onAdd: (List<PendingFile>) -> Unit,
    onRemove: (TicketAttachment) -> Unit,
    onUndoRemove: (TicketAttachment) -> Unit,
    onDropPending: (Int) -> Unit,
    onOpen: (TicketAttachment) -> Unit
) {
    var localError by remember { mutableStateOf<String?>(null) }
    val pick = rememberAttachmentPicker { files ->
        localError = attachmentLimitError(files, pendingFiles.size)
        if (localError == null) onAdd(files)
    }

    Card(modifier = Modifier.fillMaxWidth().testTag("ticket-attachments")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Attachments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canAttach) {
                    OutlinedButton(onClick = { localError = null; pick() }, enabled = !busy) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (busy) "Uploading..." else "Attach files")
                    }
                }
            }

            if (attachments.isEmpty() && pendingFiles.isEmpty()) {
                Text(
                    if (canAttach) "No attachments. Use Attach files (up to 25 MB each)." else "No attachments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            attachments.forEach { a ->
                val removing = a.id in pendingRemovals
                AttachmentRow(
                    name = a.file_name,
                    meta = formatFileSize(a.size_bytes) + (a.uploader_handle?.let { " · $it" } ?: ""),
                    thumbnail = if (a.is_image) attachmentThumbnail(a, authHeader) else null,
                    struck = removing,
                    onClick = { onOpen(a) },
                    trailing = {
                        when {
                            removing -> TextButton(onClick = { onUndoRemove(a) }) { Text("Undo") }
                            canRemove(a) -> IconButton(onClick = { onRemove(a) }, enabled = !busy) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${a.file_name}")
                            }
                        }
                    }
                )
            }
            pendingFiles.forEachIndexed { i, f ->
                AttachmentRow(
                    name = f.name,
                    meta = (if (f.size >= 0) formatFileSize(f.size) + " · " else "") + "unsaved",
                    thumbnail = if (f.isImage) f.uri else null,
                    struck = false,
                    italicMeta = true,
                    onClick = null,
                    trailing = {
                        IconButton(onClick = { onDropPending(i) }, enabled = !busy) {
                            Icon(Icons.Filled.Close, contentDescription = "Don't attach ${f.name}")
                        }
                    }
                )
            }

            (localError ?: error)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun attachmentThumbnail(a: TicketAttachment, authHeader: String?): Any {
    val context = LocalContext.current
    return remember(a.id, authHeader) {
        ImageRequest.Builder(context)
            .data("${RetrofitClient.BASE_URL}api/helpdesk/attachment/${a.id}")
            .apply { authHeader?.let { addHeader("Authorization", it) } }
            .crossfade(true)
            .build()
    }
}

@Composable
private fun AttachmentRow(
    name: String,
    meta: String,
    thumbnail: Any?,
    struck: Boolean,
    italicMeta: Boolean = false,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open", onClick = onClick) else Modifier)
                .padding(vertical = 4.dp)
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                )
            } else {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (struck) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta + if (struck) " · removing" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (italicMeta) FontStyle.Italic else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}
