package com.cherryblossomdev.breakroom.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File

// The Projects feature: cross-company project list and invites, the project
// workspace (board access flags, dependencies, estimates), settings and
// members, Burndown data, and ticket attachments. The backend answers
// failures with { message }, which is surfaced as-is so the user sees why
// (e.g. "Adding this dependency would create a loop").
class ProjectRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    private fun <T> errorFrom(response: Response<T>, fallback: String): BreakroomResult.Error {
        val message = try {
            Gson().fromJson(response.errorBody()?.string(), ProjectMessageResponse::class.java)?.message
        } catch (e: Exception) {
            null
        }
        return BreakroomResult.Error(message ?: when (response.code()) {
            403 -> "You don't have access to this project"
            404 -> "Not found"
            else -> fallback
        })
    }

    // Runs a call and maps a successful body; any failure becomes an Error
    // carrying the backend's message
    private suspend fun <T, R> call(
        fallback: String,
        request: suspend (auth: String) -> Response<T>,
        map: (T) -> R
    ): BreakroomResult<R> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = request(auth)
            if (response.code() == 401) return BreakroomResult.AuthenticationError
            val body = response.body()
            if (response.isSuccessful && body != null) BreakroomResult.Success(map(body))
            else errorFrom(response, fallback)
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: fallback)
        }
    }

    // ---- Projects page ----

    suspend fun getMyProjects(): BreakroomResult<List<Project>> =
        call("Failed to load projects", { apiService.getMyProjects(it) }) { it.projects }

    suspend fun getMyInvites(): BreakroomResult<List<ProjectInvite>> =
        call("Failed to load invites", { apiService.getMyProjectInvites(it) }) { it.invites }

    suspend fun respondToInvite(projectId: Int, accept: Boolean): BreakroomResult<String> =
        call("Failed to respond to invite", {
            apiService.respondToProjectInvite(it, projectId, if (accept) "accept" else "decline")
        }) { it.message ?: "" }

    // ---- Project board ----

    suspend fun getProject(projectId: Int): BreakroomResult<ProjectWithTicketsResponse> =
        call("Failed to load project", { apiService.getProjectWithTickets(it, projectId) }) { it }

    suspend fun createTicket(
        projectId: Int,
        title: String,
        description: String?,
        priority: String,
        estimateAmount: Double? = null,
        estimateUnit: String? = null
    ): BreakroomResult<Ticket> =
        call("Failed to create ticket", {
            apiService.createProjectTicket(
                it, projectId,
                CreateProjectTicketRequest(title, description, priority, estimateAmount, estimateUnit)
            )
        }) { it.ticket }

    /**
     * Sends only the given fields. A null value is sent as JSON null, which
     * clears the field (unassign, remove estimate). Keys follow the backend:
     * title, description, status, priority, assigned_to, estimate_amount,
     * estimate_unit.
     */
    suspend fun updateTicketFields(ticketId: Int, fields: Map<String, Any?>): BreakroomResult<Ticket> {
        val json = JSONObject()
        fields.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        return call("Failed to update ticket", { apiService.updateTicketFields(it, ticketId, body) }) { it.ticket }
    }

    // ---- Dependencies ----

    suspend fun addDependency(ticketId: Int, dependsOnId: Int): BreakroomResult<List<TicketDependency>> =
        call("Failed to add dependency", {
            apiService.addTicketDependency(it, ticketId, AddTicketDependencyRequest(dependsOnId))
        }) { it.dependencies }

    suspend fun removeDependency(ticketId: Int, dependsOnId: Int): BreakroomResult<List<TicketDependency>> =
        call("Failed to remove dependency", {
            apiService.removeTicketDependency(it, ticketId, dependsOnId)
        }) { it.dependencies }

    // ---- Burndown ----

    suspend fun getBurndown(projectId: Int): BreakroomResult<BurndownResponse> =
        call("Failed to load burndown data", { apiService.getProjectBurndown(it, projectId) }) { it }

    // ---- Settings + members ----

    suspend fun getSettings(projectId: Int): BreakroomResult<ProjectSettingsResponse> =
        call("Failed to load project settings", { apiService.getProjectSettings(it, projectId) }) { it }

    suspend fun updateSprintDuration(projectId: Int, days: Int): BreakroomResult<ProjectSettings> =
        call("Failed to update project settings", {
            apiService.updateProjectSettings(it, projectId, UpdateProjectSettingsRequest(days))
        }) { it.settings }

    suspend fun inviteMember(projectId: Int, identifier: String, role: String): BreakroomResult<ProjectMembersResponse> =
        call("Failed to send invite", {
            apiService.inviteProjectMember(it, projectId, InviteProjectMemberRequest(identifier, role))
        }) { it }

    suspend fun changeMemberRole(projectId: Int, userId: Int, role: String): BreakroomResult<List<ProjectMember>> =
        call("Failed to change role", {
            apiService.updateProjectMember(it, projectId, userId, UpdateProjectMemberRequest(role))
        }) { it.members }

    suspend fun removeMember(projectId: Int, userId: Int): BreakroomResult<List<ProjectMember>> =
        call("Failed to remove member", { apiService.removeProjectMember(it, projectId, userId) }) { it.members }

    // ---- Attachments ----

    suspend fun getAttachments(ticketId: Int): BreakroomResult<List<TicketAttachment>> =
        call("Failed to load attachments", { apiService.getTicketAttachments(it, ticketId) }) { it.attachments }

    // Copies each picked file to cache (content URIs can't be streamed by
    // OkHttp directly) and uploads them in one request, as field "files"
    suspend fun uploadAttachments(ticketId: Int, uris: List<Uri>): BreakroomResult<List<TicketAttachment>> {
        val tempFiles = mutableListOf<File>()
        return try {
            val parts = withContext(Dispatchers.IO) {
                uris.mapIndexed { index, uri ->
                    val resolver = context.contentResolver
                    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                    val fileName = displayName(uri) ?: "file-${index + 1}"
                    val temp = File(context.cacheDir, "ticket-upload-$index-${System.currentTimeMillis()}")
                    tempFiles += temp
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: throw IllegalStateException("Couldn't read $fileName")
                    MultipartBody.Part.createFormData("files", fileName, temp.asRequestBody(mimeType.toMediaTypeOrNull()))
                }
            }
            call("Failed to upload attachments", { apiService.uploadTicketAttachments(it, ticketId, parts) }) { it.attachments }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Failed to upload attachments")
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    // Downloads to the cache (attachments are behind auth, so they can't be
    // handed to another app as a URL) and returns the file for opening/sharing
    suspend fun downloadAttachment(attachment: TicketAttachment): BreakroomResult<File> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.downloadTicketAttachment(auth, attachment.id)
            val body = response.body()
            if (!response.isSuccessful || body == null) return errorFrom(response, "Failed to open attachment")
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "ticket-attachments/${attachment.id}").apply { mkdirs() }
                val file = File(dir, attachment.file_name.replace('/', '_'))
                body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                BreakroomResult.Success(file)
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Failed to open attachment")
        }
    }

    suspend fun deleteAttachment(attachmentId: Int): BreakroomResult<List<TicketAttachment>> =
        call("Failed to remove attachment", { apiService.deleteTicketAttachment(it, attachmentId) }) { it.attachments }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
