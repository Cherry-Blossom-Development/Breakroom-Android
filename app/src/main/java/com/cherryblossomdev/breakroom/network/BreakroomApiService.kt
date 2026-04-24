package com.cherryblossomdev.breakroom.network

import com.cherryblossomdev.breakroom.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    ): Response<AddBlockResponse>

    @DELETE("api/breakroom/blocks/{blockId}")
    suspend fun removeBlock(
        @Header("Authorization") token: String,
        @Path("blockId") blockId: Int
    ): Response<Unit>

    // Updates feed
    @GET("api/breakroom/updates")
    suspend fun getUpdates(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 20,
        @Query("platform") platform: String = "android"
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

    // Blog settings
    @GET("api/blog/settings")
    suspend fun getBlogSettings(
        @Header("Authorization") token: String
    ): Response<BlogSettingsResponse>

    @POST("api/blog/settings")
    suspend fun createBlogSettings(
        @Header("Authorization") token: String,
        @Body request: BlogSettingsRequest
    ): Response<BlogSettingsResponse>

    @PUT("api/blog/settings")
    suspend fun updateBlogSettings(
        @Header("Authorization") token: String,
        @Body request: BlogSettingsRequest
    ): Response<BlogSettingsResponse>

    @Multipart
    @POST("api/blog/upload-image")
    suspend fun uploadBlogImage(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part
    ): Response<BlogImageUploadResponse>

    @GET("api/blog/check-url/{blogUrl}")
    suspend fun checkBlogUrl(
        @Header("Authorization") token: String,
        @Path("blogUrl") blogUrl: String
    ): Response<BlogUrlCheckResponse>

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

    @POST("api/profile/deletion-request")
    suspend fun submitDeletionRequest(
        @Header("Authorization") token: String
    ): Response<Unit>

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

    @PUT("api/profile/jobs/{jobId}")
    suspend fun updateJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: Int,
        @Body request: AddJobRequest
    ): Response<JobResponse>

    @DELETE("api/profile/jobs/{jobId}")
    suspend fun deleteJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: Int
    ): Response<ProfileActionResponse>

    @GET("api/profile/user/{handle}")
    suspend fun getPublicProfile(
        @Header("Authorization") token: String,
        @Path("handle") handle: String
    ): Response<ProfileResponse>

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

    @GET("api/helpdesk/ticket/{ticketId}/comments")
    suspend fun getTicketComments(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: Int
    ): Response<TicketCommentsResponse>

    @POST("api/helpdesk/ticket/{ticketId}/comments")
    suspend fun addTicketComment(
        @Header("Authorization") token: String,
        @Path("ticketId") ticketId: Int,
        @Body request: AddCommentRequest
    ): Response<TicketCommentResponse>

    @PUT("api/helpdesk/comment/{id}")
    suspend fun updateTicketComment(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: AddCommentRequest
    ): Response<TicketCommentResponse>

    @DELETE("api/helpdesk/comment/{id}")
    suspend fun deleteTicketComment(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

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

    @PUT("api/company/{companyId}")
    suspend fun updateCompany(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int,
        @Body request: UpdateCompanyRequest
    ): Response<CompanyResponse>

    @DELETE("api/company/{companyId}")
    suspend fun deleteCompany(
        @Header("Authorization") token: String,
        @Path("companyId") companyId: Int
    ): Response<Unit>

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

    @DELETE("api/projects/{projectId}")
    suspend fun deleteProject(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: Int
    ): Response<Unit>

    @GET("api/projects/{projectId}")
    suspend fun getProjectWithTickets(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: Int
    ): Response<ProjectWithTicketsResponse>

    @POST("api/projects/{projectId}/tickets")
    suspend fun createProjectTicket(
        @Header("Authorization") token: String,
        @Path("projectId") projectId: Int,
        @Body request: CreateProjectTicketRequest
    ): Response<TicketResponse>

    // Shortcuts endpoints
    @GET("api/shortcuts")
    suspend fun getShortcuts(
        @Header("Authorization") token: String
    ): Response<ShortcutsResponse>

    @GET("api/shortcuts/check")
    suspend fun checkShortcut(
        @Header("Authorization") token: String,
        @Query("url") url: String
    ): Response<ShortcutCheckResponse>

    @POST("api/shortcuts")
    suspend fun createShortcut(
        @Header("Authorization") token: String,
        @Body request: CreateShortcutRequest
    ): Response<ShortcutResponse>

    @DELETE("api/shortcuts/{id}")
    suspend fun deleteShortcut(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<ShortcutMessageResponse>

    // Features endpoints
    @GET("api/features/mine")
    suspend fun getMyFeatures(
        @Header("Authorization") token: String
    ): Response<FeaturesResponse>

    // ==================== Lyric Lab endpoints ====================

    // Songs
    @GET("api/lyrics/songs")
    suspend fun getSongs(
        @Header("Authorization") token: String
    ): Response<SongsResponse>

    @GET("api/lyrics/songs/{songId}")
    suspend fun getSong(
        @Header("Authorization") token: String,
        @Path("songId") songId: Int
    ): Response<SongDetailResponse>

    @POST("api/lyrics/songs")
    suspend fun createSong(
        @Header("Authorization") token: String,
        @Body request: CreateSongRequest
    ): Response<SongResponse>

    @PUT("api/lyrics/songs/{songId}")
    suspend fun updateSong(
        @Header("Authorization") token: String,
        @Path("songId") songId: Int,
        @Body request: UpdateSongRequest
    ): Response<SongResponse>

    @DELETE("api/lyrics/songs/{songId}")
    suspend fun deleteSong(
        @Header("Authorization") token: String,
        @Path("songId") songId: Int
    ): Response<LyricsMessageResponse>

    // Song Collaborators
    @POST("api/lyrics/songs/{songId}/collaborators")
    suspend fun addCollaborator(
        @Header("Authorization") token: String,
        @Path("songId") songId: Int,
        @Body request: AddCollaboratorRequest
    ): Response<CollaboratorResponse>

    @DELETE("api/lyrics/songs/{songId}/collaborators/{userId}")
    suspend fun removeCollaborator(
        @Header("Authorization") token: String,
        @Path("songId") songId: Int,
        @Path("userId") userId: Int
    ): Response<LyricsMessageResponse>

    // Lyrics
    @GET("api/lyrics/standalone")
    suspend fun getStandaloneLyrics(
        @Header("Authorization") token: String
    ): Response<LyricsResponse>

    @GET("api/lyrics/{lyricId}")
    suspend fun getLyric(
        @Header("Authorization") token: String,
        @Path("lyricId") lyricId: Int
    ): Response<LyricResponse>

    @POST("api/lyrics")
    suspend fun createLyric(
        @Header("Authorization") token: String,
        @Body request: CreateLyricRequest
    ): Response<LyricResponse>

    @PUT("api/lyrics/{lyricId}")
    suspend fun updateLyric(
        @Header("Authorization") token: String,
        @Path("lyricId") lyricId: Int,
        @Body request: UpdateLyricRequest
    ): Response<LyricResponse>

    @DELETE("api/lyrics/{lyricId}")
    suspend fun deleteLyric(
        @Header("Authorization") token: String,
        @Path("lyricId") lyricId: Int
    ): Response<LyricsMessageResponse>

    // ==================== Art Gallery endpoints ====================

    @GET("api/gallery/settings")
    suspend fun getGallerySettings(
        @Header("Authorization") token: String
    ): Response<GallerySettingsResponse>

    @POST("api/gallery/settings")
    suspend fun createGallerySettings(
        @Header("Authorization") token: String,
        @Body request: GallerySettingsRequest
    ): Response<GallerySettingsResponse>

    @PUT("api/gallery/settings")
    suspend fun updateGallerySettings(
        @Header("Authorization") token: String,
        @Body request: GallerySettingsRequest
    ): Response<GallerySettingsResponse>

    @GET("api/gallery/check-url/{galleryUrl}")
    suspend fun checkGalleryUrl(
        @Header("Authorization") token: String,
        @Path("galleryUrl") galleryUrl: String
    ): Response<GalleryUrlCheckResponse>

    @GET("api/gallery/artworks")
    suspend fun getArtworks(
        @Header("Authorization") token: String
    ): Response<ArtworksResponse>

    @Multipart
    @POST("api/gallery/artworks")
    suspend fun uploadArtwork(
        @Header("Authorization") token: String,
        @Part("title") title: okhttp3.RequestBody,
        @Part("description") description: okhttp3.RequestBody?,
        @Part("isPublished") isPublished: okhttp3.RequestBody,
        @Part image: MultipartBody.Part
    ): Response<ArtworkResponse>

    @PUT("api/gallery/artworks/{artworkId}")
    suspend fun updateArtwork(
        @Header("Authorization") token: String,
        @Path("artworkId") artworkId: Int,
        @Body request: UpdateArtworkRequest
    ): Response<ArtworkResponse>

    @DELETE("api/gallery/artworks/{artworkId}")
    suspend fun deleteArtwork(
        @Header("Authorization") token: String,
        @Path("artworkId") artworkId: Int
    ): Response<GalleryMessageResponse>

    // ==================== Moderation endpoints ====================

    @POST("api/moderation/flag")
    suspend fun flagContent(
        @Header("Authorization") token: String,
        @Body request: FlagRequest
    ): Response<ModerationMessageResponse>

    @POST("api/moderation/block/{userId}")
    suspend fun moderationBlockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<ModerationMessageResponse>

    @DELETE("api/moderation/block/{userId}")
    suspend fun moderationUnblockUser(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int
    ): Response<ModerationMessageResponse>

    @GET("api/moderation/blocks")
    suspend fun getModerationBlocks(
        @Header("Authorization") token: String
    ): Response<ModerationBlockListResponse>

    // ==================== Sessions endpoints ====================

    @GET("api/sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String
    ): Response<SessionsResponse>

    @Multipart
    @POST("api/sessions")
    suspend fun uploadSession(
        @Header("Authorization") token: String,
        @Part audio: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("recorded_at") recordedAt: RequestBody?,
        @Part("session_type") sessionType: RequestBody?,
        @Part("band_id") bandId: RequestBody?,
        @Part("instrument_id") instrumentId: RequestBody?
    ): Response<SessionResponse>

    @GET("api/sessions/band-members")
    suspend fun getBandMemberSessions(
        @Header("Authorization") token: String
    ): Response<SessionsResponse>

    @POST("api/sessions/{id}/rate")
    suspend fun rateSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int,
        @Body request: RateSessionRequest
    ): Response<SessionRatingResponse>

    @PATCH("api/sessions/{id}")
    suspend fun updateSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int,
        @Body request: UpdateSessionRequest
    ): Response<SessionResponse>

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Int
    ): Response<SessionMessageResponse>

    // ==================== Bands endpoints ====================

    @GET("api/bands")
    suspend fun getBands(
        @Header("Authorization") token: String
    ): Response<BandsListResponse>

    @GET("api/bands/{id}")
    suspend fun getBandDetail(
        @Header("Authorization") token: String,
        @Path("id") bandId: Int
    ): Response<BandDetailResponse>

    @POST("api/bands")
    suspend fun createBand(
        @Header("Authorization") token: String,
        @Body request: CreateBandRequest
    ): Response<BandDetailResponse>

    @POST("api/bands/{id}/invites")
    suspend fun inviteBandMember(
        @Header("Authorization") token: String,
        @Path("id") bandId: Int,
        @Body request: InviteMemberRequest
    ): Response<BandMessageResponse>

    @PATCH("api/bands/{id}/members/me")
    suspend fun respondBandInvite(
        @Header("Authorization") token: String,
        @Path("id") bandId: Int,
        @Body request: BandInviteActionRequest
    ): Response<BandMessageResponse>

    @DELETE("api/bands/{id}/members/{userId}")
    suspend fun removeBandMember(
        @Header("Authorization") token: String,
        @Path("id") bandId: Int,
        @Path("userId") userId: Int
    ): Response<BandMessageResponse>

    // ==================== Instruments endpoint ====================

    @GET("api/instruments")
    suspend fun getInstruments(): Response<InstrumentsResponse>

    // ==================== Badge counts ====================

    @GET("api/user/badge-counts")
    suspend fun getBadgeCounts(
        @Header("Authorization") token: String
    ): Response<BadgeCountsResponse>

    @POST("api/chat/rooms/{roomId}/mark-read")
    suspend fun markRoomRead(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int
    ): Response<Unit>

    @POST("api/chat/rooms/mark-all-read")
    suspend fun markAllRoomsRead(
        @Header("Authorization") token: String
    ): Response<Unit>

    @POST("api/friends/mark-seen")
    suspend fun markFriendsSeen(
        @Header("Authorization") token: String
    ): Response<Unit>

    @POST("api/comments/posts/{postId}/mark-read")
    suspend fun markBlogPostRead(
        @Header("Authorization") token: String,
        @Path("postId") postId: Int
    ): Response<Unit>

    // ==================== Subscriptions ====================

    @GET("api/subscriptions/me")
    suspend fun getSubscriptionStatus(
        @Header("Authorization") token: String
    ): Response<SubscriptionStatusResponse>

    @POST("api/subscriptions/google/verify")
    suspend fun verifyGooglePurchase(
        @Header("Authorization") token: String,
        @Body request: GoogleVerifyRequest
    ): Response<SubscriptionActivatedResponse>

    // ==================== Audio Defaults ====================

    @GET("api/user/audio-defaults")
    suspend fun getAudioDefaults(
        @Header("Authorization") token: String
    ): Response<AudioDefaults>

    @PUT("api/user/audio-defaults")
    suspend fun updateAudioDefaults(
        @Header("Authorization") token: String,
        @Body request: AudioDefaultsRequest
    ): Response<AudioDefaults>
}
