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
}
