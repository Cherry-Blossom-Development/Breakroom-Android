package com.example.breakroom.data

import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class HelpDeskRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

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

    suspend fun updateTicketStatus(ticketId: Int, newStatus: String): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateTicketRequest(status = newStatus)
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
}
