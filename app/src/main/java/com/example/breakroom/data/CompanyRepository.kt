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
            Log.d(TAG, "getMyCompanies: Calling API...")
            val response = apiService.getMyCompanies(authHeader)
            Log.d(TAG, "getMyCompanies: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "getMyCompanies: Body is null? ${body == null}")
                val companies = body?.companies ?: emptyList()
                Log.d(TAG, "getMyCompanies: Got ${companies.size} companies from 'companies' field")
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
}
