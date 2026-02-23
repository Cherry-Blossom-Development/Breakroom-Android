package com.cherryblossomdev.breakroom.data

import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService

class KanbanRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    private fun getAuthHeader(): String? = tokenManager.getBearerToken()

    suspend fun getMyCompanies(): BreakroomResult<List<Company>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getMyCompanies(auth)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.getCompanyList() ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load companies")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createCompany(name: String, description: String, employeeTitle: String): BreakroomResult<Company> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateCompanyRequest(
                name = name,
                description = description,
                address = null, city = null, state = null, country = null,
                postal_code = null, phone = null, email = null, website = null,
                employee_title = employeeTitle
            )
            val response = apiService.createCompany(auth, request)
            if (response.isSuccessful) {
                response.body()?.company?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No company data")
            } else {
                BreakroomResult.Error("Failed to create company")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompanyProjects(companyId: Int): BreakroomResult<List<Project>> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getCompanyProjects(auth, companyId)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.projects ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to load projects")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getProjectWithTickets(projectId: Int): BreakroomResult<ProjectWithTicketsResponse> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getProjectWithTickets(auth, projectId)
            if (response.isSuccessful) {
                response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No project data")
            } else {
                BreakroomResult.Error("Failed to load project")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createProjectTicket(
        projectId: Int,
        title: String,
        description: String?,
        priority: String
    ): BreakroomResult<Ticket> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateProjectTicketRequest(title = title, description = description, priority = priority)
            val response = apiService.createProjectTicket(auth, projectId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No ticket data")
            } else {
                BreakroomResult.Error("Failed to create ticket")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateTicket(
        ticketId: Int,
        status: String? = null,
        title: String? = null,
        description: String? = null,
        priority: String? = null
    ): BreakroomResult<Ticket> {
        val auth = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateTicketRequest(title = title, description = description, status = status, priority = priority)
            val response = apiService.updateTicket(auth, ticketId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No ticket data")
            } else {
                BreakroomResult.Error("Failed to update ticket")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
