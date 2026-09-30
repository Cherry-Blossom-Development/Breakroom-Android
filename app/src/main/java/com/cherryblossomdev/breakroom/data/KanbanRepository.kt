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
}
