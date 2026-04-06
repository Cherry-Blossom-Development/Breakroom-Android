package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class HelpDeskRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    fun getUsername(): String = tokenManager.getUsername() ?: ""

    suspend fun getCompany(companyId: Int): BreakroomResult<HelpDeskCompany> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getHelpDeskCompany(authHeader, companyId)
            if (response.isSuccessful) {
                response.body()?.company?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No company data")
            } else {
                BreakroomResult.Error("Failed to load company")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getTickets(companyId: Int): BreakroomResult<List<Ticket>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getTickets(authHeader, companyId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.tickets ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load tickets")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createTicket(
        companyId: Int,
        title: String,
        description: String?,
        priority: String
    ): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateTicketRequest(
                company_id = companyId,
                title = title,
                description = description,
                priority = priority
            )
            val response = apiService.createTicket(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No ticket data")
            } else {
                BreakroomResult.Error("Failed to create ticket")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateTicket(
        ticketId: Int,
        title: String,
        description: String?,
        priority: String,
        status: String
    ): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateTicketRequest(
                title = title,
                description = description,
                priority = priority,
                status = status
            )
            val response = apiService.updateTicket(authHeader, ticketId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No ticket data")
            } else {
                BreakroomResult.Error("Failed to update ticket")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getComments(ticketId: Int): BreakroomResult<List<TicketComment>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getTicketComments(authHeader, ticketId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.comments ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load comments")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun addComment(ticketId: Int, content: String): BreakroomResult<TicketComment> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.addTicketComment(authHeader, ticketId, AddCommentRequest(content))
            if (response.isSuccessful) {
                response.body()?.comment?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No comment data")
            } else {
                BreakroomResult.Error("Failed to add comment")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateComment(id: Int, content: String): BreakroomResult<TicketComment> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.updateTicketComment(authHeader, id, AddCommentRequest(content))
            if (response.isSuccessful) {
                response.body()?.comment?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No comment data")
            } else {
                BreakroomResult.Error("Failed to update comment")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteComment(id: Int): BreakroomResult<Unit> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteTicketComment(authHeader, id)
            if (response.isSuccessful) {
                BreakroomResult.Success(Unit)
            } else {
                BreakroomResult.Error("Failed to delete comment")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
