package com.example.breakroom.data

import android.util.Log
import com.example.breakroom.data.models.*
import com.example.breakroom.network.BreakroomApiService

class CompanyRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "CompanyRepository"
    }

    private fun getAuthHeader(): String? {
        return tokenManager.getBearerToken()
    }

    suspend fun searchCompanies(query: String): BreakroomResult<List<Company>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.searchCompanies(authHeader, query)
            if (response.isSuccessful) {
                BreakroomResult.Success(response.body()?.companies ?: emptyList())
            } else {
                BreakroomResult.Error("Failed to search companies")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getMyCompanies(): BreakroomResult<List<Company>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getMyCompanies: Calling API with token: ${authHeader.take(20)}...")
            val response = apiService.getMyCompanies(authHeader)
            Log.d(TAG, "getMyCompanies: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "getMyCompanies: Body is null? ${body == null}")
                if (body != null) {
                    Log.d(TAG, "getMyCompanies: Body.companies is null? ${body.companies == null}")
                    Log.d(TAG, "getMyCompanies: Body.data is null? ${body.data == null}")
                    Log.d(TAG, "getMyCompanies: Body.companies size: ${body.companies?.size ?: 0}")
                    Log.d(TAG, "getMyCompanies: Body.data size: ${body.data?.size ?: 0}")
                    body.getCompanyList().forEachIndexed { index, company ->
                        Log.d(TAG, "getMyCompanies: Company[$index]: id=${company.id}, name=${company.name}")
                    }
                }
                val companies = body?.getCompanyList() ?: emptyList()
                Log.d(TAG, "getMyCompanies: Returning ${companies.size} companies")
                BreakroomResult.Success(companies)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getMyCompanies: Error - $errorBody")
                BreakroomResult.Error("Failed to load your companies")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMyCompanies: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createCompany(
        name: String,
        description: String?,
        address: String?,
        city: String?,
        state: String?,
        country: String?,
        postalCode: String?,
        phone: String?,
        email: String?,
        website: String?,
        employeeTitle: String
    ): BreakroomResult<Company> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = CreateCompanyRequest(
                name = name,
                description = description,
                address = address,
                city = city,
                state = state,
                country = country,
                postal_code = postalCode,
                phone = phone,
                email = email,
                website = website,
                employee_title = employeeTitle
            )
            val response = apiService.createCompany(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.company?.let {
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No company data returned")
            } else {
                BreakroomResult.Error("Failed to create company")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompany(companyId: Int): BreakroomResult<Company> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getCompany: Fetching company $companyId...")
            val response = apiService.getCompany(authHeader, companyId)
            if (response.isSuccessful) {
                response.body()?.company?.let {
                    Log.d(TAG, "getCompany: Got company ${it.name}")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No company data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getCompany: Error - $errorBody")
                BreakroomResult.Error("Failed to load company")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCompany: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompanyEmployees(companyId: Int): BreakroomResult<List<CompanyEmployee>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getCompanyEmployees: Fetching employees for company $companyId...")
            val response = apiService.getCompanyEmployees(authHeader, companyId)
            if (response.isSuccessful) {
                val body = response.body()
                val employees = body?.getEmployeeList() ?: emptyList()
                Log.d(TAG, "getCompanyEmployees: Got ${employees.size} employees")
                BreakroomResult.Success(employees)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getCompanyEmployees: Error - $errorBody")
                BreakroomResult.Error("Failed to load employees")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCompanyEmployees: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateCompanyEmployee(
        companyId: Int,
        employeeId: Int,
        title: String?,
        department: String?,
        hireDate: String?,
        isAdmin: Boolean
    ): BreakroomResult<CompanyEmployee> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "updateCompanyEmployee: Updating employee $employeeId in company $companyId...")
            // Normalize hire date to YYYY-MM-DD format (strip time portion if present)
            val normalizedHireDate = hireDate?.let {
                if (it.contains("T")) it.substringBefore("T") else it
            }
            val request = UpdateEmployeeRequest(title = title, department = department, hire_date = normalizedHireDate, is_admin = if (isAdmin) 1 else 0)
            Log.d(TAG, "updateCompanyEmployee: Request - title=$title, department=$department, hireDate=$normalizedHireDate, isAdmin=$isAdmin")
            val response = apiService.updateCompanyEmployee(authHeader, companyId, employeeId, request)
            Log.d(TAG, "updateCompanyEmployee: Response code=${response.code()}")
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "updateCompanyEmployee: Response body=$body")
                body?.employee?.let {
                    Log.d(TAG, "updateCompanyEmployee: Updated successfully")
                    BreakroomResult.Success(it)
                } ?: run {
                    // If no employee returned but success, reload from list
                    Log.d(TAG, "updateCompanyEmployee: No employee in response, but was successful")
                    BreakroomResult.Error("Update may have succeeded - please refresh")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "updateCompanyEmployee: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to update employee: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateCompanyEmployee: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createPosition(
        companyId: Int,
        title: String,
        description: String?,
        requirements: String?,
        benefits: String?,
        department: String?,
        employmentType: String?,
        locationType: String?,
        city: String?,
        state: String?,
        payType: String?,
        payRateMin: Double?,
        payRateMax: Double?
    ): BreakroomResult<Position> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "createPosition: Creating position '$title' for company $companyId...")
            val request = CreatePositionRequest(
                company_id = companyId,
                title = title,
                description = description,
                requirements = requirements,
                benefits = benefits,
                department = department,
                employment_type = employmentType,
                location_type = locationType,
                city = city,
                state = state,
                pay_type = payType,
                pay_rate_min = payRateMin,
                pay_rate_max = payRateMax
            )
            val response = apiService.createPosition(authHeader, companyId, request)
            if (response.isSuccessful) {
                response.body()?.position?.let {
                    Log.d(TAG, "createPosition: Created position ${it.id}")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No position data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "createPosition: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to create position: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createPosition: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deletePosition(positionId: Int): BreakroomResult<Unit> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "deletePosition: Deleting position $positionId...")
            val response = apiService.deletePosition(authHeader, positionId)
            if (response.isSuccessful) {
                Log.d(TAG, "deletePosition: Deleted successfully")
                BreakroomResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "deletePosition: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to delete position: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deletePosition: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updatePosition(
        positionId: Int,
        title: String?,
        description: String?,
        requirements: String?,
        benefits: String?,
        department: String?,
        employmentType: String?,
        locationType: String?,
        city: String?,
        state: String?,
        country: String?,
        payType: String?,
        payRateMin: Double?,
        payRateMax: Double?
    ): BreakroomResult<Position> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "updatePosition: Updating position $positionId...")
            Log.d(TAG, "updatePosition: title=$title, dept=$department, empType=$employmentType, locType=$locationType")
            Log.d(TAG, "updatePosition: city=$city, state=$state, country=$country")
            Log.d(TAG, "updatePosition: payType=$payType, min=$payRateMin, max=$payRateMax")
            val request = UpdatePositionRequest(
                title = title,
                description = description,
                requirements = requirements,
                benefits = benefits,
                department = department,
                employment_type = employmentType,
                location_type = locationType,
                city = city,
                state = state,
                country = country,
                pay_type = payType,
                pay_rate_min = payRateMin,
                pay_rate_max = payRateMax,
                status = "open"
            )
            val response = apiService.updatePosition(authHeader, positionId, request)
            if (response.isSuccessful) {
                response.body()?.position?.let {
                    Log.d(TAG, "updatePosition: Updated successfully")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No position data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "updatePosition: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to update position: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updatePosition: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompanyPositions(companyId: Int): BreakroomResult<List<Position>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getCompanyPositions: Fetching positions for company $companyId...")
            // Fetch all positions and filter by company_id
            val response = apiService.getPositions(authHeader)
            if (response.isSuccessful) {
                val allPositions = response.body()?.positions ?: emptyList()
                val companyPositions = allPositions.filter { it.company_id == companyId }
                Log.d(TAG, "getCompanyPositions: Got ${companyPositions.size} positions for company $companyId (out of ${allPositions.size} total)")
                BreakroomResult.Success(companyPositions)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getCompanyPositions: Error - $errorBody")
                BreakroomResult.Error("Failed to load positions")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCompanyPositions: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getCompanyProjects(companyId: Int): BreakroomResult<List<Project>> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getCompanyProjects: Fetching projects for company $companyId...")
            val response = apiService.getCompanyProjects(authHeader, companyId)
            if (response.isSuccessful) {
                val projects = response.body()?.projects ?: emptyList()
                Log.d(TAG, "getCompanyProjects: Got ${projects.size} projects for company $companyId")
                BreakroomResult.Success(projects)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getCompanyProjects: Error - $errorBody")
                BreakroomResult.Error("Failed to load projects")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCompanyProjects: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createProject(
        companyId: Int,
        title: String,
        description: String?,
        isPublic: Boolean
    ): BreakroomResult<Project> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "createProject: Creating project '$title' for company $companyId...")
            val request = CreateProjectRequest(
                company_id = companyId,
                title = title,
                description = description,
                is_public = isPublic
            )
            val response = apiService.createProject(authHeader, request)
            if (response.isSuccessful) {
                response.body()?.project?.let {
                    Log.d(TAG, "createProject: Created project ${it.id}")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No project data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "createProject: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to create project: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createProject: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateProject(
        projectId: Int,
        title: String?,
        description: String?,
        isPublic: Boolean?,
        isActive: Boolean?
    ): BreakroomResult<Project> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "updateProject: Updating project $projectId...")
            val request = UpdateProjectRequest(
                title = title,
                description = description,
                is_public = isPublic,
                is_active = isActive
            )
            val response = apiService.updateProject(authHeader, projectId, request)
            if (response.isSuccessful) {
                response.body()?.project?.let {
                    Log.d(TAG, "updateProject: Updated successfully")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No project data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "updateProject: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to update project: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateProject: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getProjectWithTickets(projectId: Int): BreakroomResult<ProjectWithTicketsResponse> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "getProjectWithTickets: Fetching project $projectId with tickets...")
            val response = apiService.getProjectWithTickets(authHeader, projectId)
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "getProjectWithTickets: Got project ${it.project.title} with ${it.tickets.size} tickets")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No project data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getProjectWithTickets: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to load project tickets: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getProjectWithTickets: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateTicketStatus(ticketId: Int, newStatus: String): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "updateTicketStatus: Updating ticket $ticketId to status $newStatus...")
            val request = UpdateTicketRequest(status = newStatus)
            val response = apiService.updateTicket(authHeader, ticketId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let {
                    Log.d(TAG, "updateTicketStatus: Success")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No ticket data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "updateTicketStatus: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to update ticket: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateTicketStatus: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun assignTicket(ticketId: Int, assigneeId: Int?): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "assignTicket: Assigning ticket $ticketId to user $assigneeId...")
            val request = UpdateTicketRequest(assigned_to = assigneeId)
            val response = apiService.updateTicket(authHeader, ticketId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let {
                    Log.d(TAG, "assignTicket: Success")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No ticket data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "assignTicket: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to assign ticket: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "assignTicket: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createProjectTicket(
        projectId: Int,
        title: String,
        description: String?,
        priority: String
    ): BreakroomResult<Ticket> {
        val authHeader = getAuthHeader() ?: return BreakroomResult.Error("Not logged in")
        return try {
            Log.d(TAG, "createProjectTicket: Creating ticket for project $projectId...")
            val request = CreateProjectTicketRequest(
                title = title,
                description = description,
                priority = priority
            )
            val response = apiService.createProjectTicket(authHeader, projectId, request)
            if (response.isSuccessful) {
                response.body()?.ticket?.let {
                    Log.d(TAG, "createProjectTicket: Success - ticket ${it.id} created")
                    BreakroomResult.Success(it)
                } ?: BreakroomResult.Error("No ticket data returned")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "createProjectTicket: Error code=${response.code()}, body=$errorBody")
                BreakroomResult.Error("Failed to create ticket: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createProjectTicket: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }
}
