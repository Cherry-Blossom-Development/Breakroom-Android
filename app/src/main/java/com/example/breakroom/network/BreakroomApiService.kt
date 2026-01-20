package com.example.breakroom.network

import com.example.breakroom.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface BreakroomApiService {

    // Layout endpoints
    @GET("api/breakroom/layout")
    suspend fun getLayout(
        @Header("Authorization") token: String
    ): Response<BreakroomLayoutResponse>

    @PUT("api/breakroom/layout")
    suspend fun updateLayout(
        @Header("Authorization") token: String,
        @Body request: UpdateLayoutRequest
    ): Response<Unit>

    @POST("api/breakroom/blocks")
    suspend fun addBlock(
        @Header("Authorization") token: String,
        @Body request: AddBlockRequest
    ): Response<BreakroomBlock>

    @DELETE("api/breakroom/blocks/{blockId}")
    suspend fun removeBlock(
        @Header("Authorization") token: String,
        @Path("blockId") blockId: Int
    ): Response<Unit>

    // Updates feed
    @GET("api/breakroom/updates")
    suspend fun getUpdates(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 20
    ): Response<BreakroomUpdatesResponse>

    // Profile for user location
    @GET("api/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    // News feed
    @GET("api/breakroom/news")
    suspend fun getNews(
        @Header("Authorization") token: String
    ): Response<NewsResponse>

    // Blog feed (friends posts)
    @GET("api/blog/feed")
    suspend fun getBlogFeed(
        @Header("Authorization") token: String
    ): Response<BlogFeedResponse>

    // Blog management - Get users own posts
    @GET("api/blog/posts")
    suspend fun getMyBlogPosts(
        @Header("Authorization") token: String
    ): Response<BlogPostsResponse>

    // Blog management - Get a single post
    @GET("api/blog/posts/{postId}")
    suspend fun getBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<BlogPostResponse>

    // Blog management - Create a new post
    @POST("api/blog/posts")
    suspend fun createBlogPost(
        @Header("Authorization") token: String,
        @Body request: CreateBlogPostRequest
    ): Response<BlogPostResponse>

    // Blog management - Update a post
    @PUT("api/blog/posts/{postId}")
    suspend fun updateBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int,
        @Body request: UpdateBlogPostRequest
    ): Response<BlogPostResponse>

    // Blog management - Delete a post
    @DELETE("api/blog/posts/{postId}")
    suspend fun deleteBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<Unit>

    // Blog - View a published post (with author info)
    @GET("api/blog/view/{postId}")
    suspend fun viewBlogPost(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<BlogViewResponse>

    // Friends endpoints
    @GET("api/friends")
    suspend fun getFriends(
        @Header("Authorization") token: String
    ): Response<FriendsListResponse>

    @GET("api/friends/requests")
    suspend fun getFriendRequests(
        @Header("Authorization") token: String
    ): Response<FriendRequestsResponse>

    @GET("api/friends/sent")
    suspend fun getSentRequests(
        @Header("Authorization") token: String
    ): Response<SentRequestsResponse>

    @GET("api/friends/blocked")
    suspend fun getBlockedUsers(
        @Header("Authorization") token: String
    ): Response<BlockedUsersResponse>

    @POST("api/friends/request/{userId}")
    suspend fun sendFriendRequest(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @POST("api/friends/accept/{userId}")
    suspend fun acceptFriendRequest(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @POST("api/friends/decline/{userId}")
    suspend fun declineFriendRequest(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @DELETE("api/friends/request/{userId}")
    suspend fun cancelFriendRequest(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @DELETE("api/friends/{userId}")
    suspend fun removeFriend(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @POST("api/friends/block/{userId}")
    suspend fun blockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    @DELETE("api/friends/block/{userId}")
    suspend fun unblockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<FriendActionResponse>

    // User search
    @GET("api/user/all")
    suspend fun getAllUsers(
        @Header("Authorization") token: String
    ): Response<AllUsersResponse>

    // Profile endpoints (getProfile already defined above for user location)
    @PUT("api/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ProfileResponse>

    @PUT("api/profile/location")
    suspend fun updateLocation(
        @Header("Authorization") token: String,
        @Body request: UpdateLocationRequest
    ): Response<ProfileResponse>

    @PUT("api/profile/timezone")
    suspend fun updateTimezone(
        @Header("Authorization") token: String,
        @Body request: UpdateTimezoneRequest
    ): Response<ProfileResponse>

    @Multipart
    @POST("api/profile/photo")
    suspend fun uploadPhoto(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): Response<PhotoUploadResponse>

    @DELETE("api/profile/photo")
    suspend fun deletePhoto(
        @Header("Authorization") token: String
    ): Response<ProfileActionResponse>

    @GET("api/profile/skills/search")
    suspend fun searchSkills(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<SkillsSearchResponse>

    @POST("api/profile/skills")
    suspend fun addSkill(
        @Header("Authorization") token: String,
        @Body request: AddSkillRequest
    ): Response<SkillResponse>

    @DELETE("api/profile/skills/{skillId}")
    suspend fun removeSkill(
        @Header("Authorization") token: String,
        @Path("skillId") skillId: Int
    ): Response<ProfileActionResponse>

    @POST("api/profile/jobs")
    suspend fun addJob(
        @Header("Authorization") token: String,
        @Body request: AddJobRequest
    ): Response<JobResponse>

    @DELETE("api/profile/jobs/{jobId}")
    suspend fun deleteJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: Int
    ): Response<ProfileActionResponse>

    // Employment/Positions endpoints
    @GET("api/positions")
    suspend fun getPositions(
        @Header("Authorization") token: String
    ): Response<PositionsResponse>

    @POST("api/positions/company/{companyId}")
    suspend fun createPosition(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int,
        @Body request: CreatePositionRequest
    ): Response<CreatePositionResponse>

    @DELETE("api/positions/{positionId}")
    suspend fun deletePosition(
        @Header("Authorization") token: String,
        @Path("positionId") positionId: Int
    ): Response<DeletePositionResponse>

    @PUT("api/positions/{positionId}")
    suspend fun updatePosition(
        @Header("Authorization") token: String,
        @Path("positionId") positionId: Int,
        @Body request: UpdatePositionRequest
    ): Response<UpdatePositionResponse>

    // HelpDesk endpoints
    @GET("api/helpdesk/company/{companyId}")
    suspend fun getHelpDeskCompany(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<HelpDeskCompanyResponse>

    @GET("api/helpdesk/tickets/{companyId}")
    suspend fun getTickets(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<TicketsResponse>

    @POST("api/helpdesk/tickets")
    suspend fun createTicket(
        @Header("Authorization") token: String,
        @Body request: CreateTicketRequest
    ): Response<TicketResponse>

    @PUT("api/helpdesk/ticket/{ticketId}")
    suspend fun updateTicket(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: Int,
        @Body request: UpdateTicketRequest
    ): Response<TicketResponse>

    // Company endpoints
    @GET("api/company/search")
    suspend fun searchCompanies(
        @Header("Authorization") token: String,
        @Query("q") query: String
    ): Response<CompanySearchResponse>

    @GET("api/company/my/list")
    suspend fun getMyCompanies(
        @Header("Authorization") token: String
    ): Response<MyCompaniesResponse>

    @POST("api/company")
    suspend fun createCompany(
        @Header("Authorization") token: String,
        @Body request: CreateCompanyRequest
    ): Response<CompanyResponse>

    @GET("api/company/{companyId}")
    suspend fun getCompany(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<CompanyResponse>

    @GET("api/company/{companyId}/employees")
    suspend fun getCompanyEmployees(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<CompanyEmployeesResponse>

    @PUT("api/company/{companyId}/employees/{employeeId}")
    suspend fun updateCompanyEmployee(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int,
        @Path("employeeId") employeeId: Int,
        @Body request: UpdateEmployeeRequest
    ): Response<UpdateEmployeeResponse>

    // Projects endpoints
    @GET("api/projects/company/{companyId}")
    suspend fun getCompanyProjects(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<ProjectsResponse>

    @POST("api/projects")
    suspend fun createProject(
        @Header("Authorization") token: String,
        @Body request: CreateProjectRequest
    ): Response<CreateProjectResponse>

    @PUT("api/projects/{projectId}")
    suspend fun updateProject(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: Int,
        @Body request: UpdateProjectRequest
    ): Response<UpdateProjectResponse>

    @GET("api/projects/{projectId}")
    suspend fun getProjectWithTickets(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: Int
    ): Response<ProjectWithTicketsResponse>
}
