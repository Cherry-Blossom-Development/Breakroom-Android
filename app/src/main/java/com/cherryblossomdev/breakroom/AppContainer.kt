package com.cherryblossomdev.breakroom

import android.content.Context
import com.cherryblossomdev.breakroom.data.AuthRepository
import com.cherryblossomdev.breakroom.data.BlogRepository
import com.cherryblossomdev.breakroom.data.BreakroomRepository
import com.cherryblossomdev.breakroom.data.ChatRepository
import com.cherryblossomdev.breakroom.data.CompanyRepository
import com.cherryblossomdev.breakroom.data.EmploymentRepository
import com.cherryblossomdev.breakroom.data.FeaturesRepository
import com.cherryblossomdev.breakroom.data.FriendsRepository
import com.cherryblossomdev.breakroom.data.GalleryRepository
import com.cherryblossomdev.breakroom.data.HelpDeskRepository
import com.cherryblossomdev.breakroom.data.LyricsRepository
import com.cherryblossomdev.breakroom.data.ModerationRepository
import com.cherryblossomdev.breakroom.data.ProfileRepository
import com.cherryblossomdev.breakroom.data.SessionsRepository
import com.cherryblossomdev.breakroom.data.TokenManager
import com.cherryblossomdev.breakroom.network.RetrofitClient
import com.cherryblossomdev.breakroom.network.SocketManager
import com.cherryblossomdev.breakroom.ui.screens.ArtGalleryViewModel
import com.cherryblossomdev.breakroom.ui.screens.BadgeViewModel
import com.cherryblossomdev.breakroom.ui.screens.BlogViewModel
import com.cherryblossomdev.breakroom.ui.screens.CompanyPortalViewModel
import com.cherryblossomdev.breakroom.ui.screens.EmploymentViewModel
import com.cherryblossomdev.breakroom.ui.screens.FriendsViewModel
import com.cherryblossomdev.breakroom.ui.screens.HelpDeskViewModel
import com.cherryblossomdev.breakroom.ui.screens.HomeViewModel
import com.cherryblossomdev.breakroom.ui.screens.LyricLabViewModel
import com.cherryblossomdev.breakroom.ui.screens.ProfileViewModel
import com.cherryblossomdev.breakroom.ui.screens.SessionsViewModel
import com.cherryblossomdev.breakroom.ui.screens.ToolShedViewModel

class AppContainer(context: Context) {
    val tokenManager by lazy { TokenManager(context) }

    init {
        // Whenever the backend sends a refreshed token, persist it
        RetrofitClient.tokenUpdateCallback = { token -> tokenManager.saveToken(token) }
    }
    val authRepository by lazy { AuthRepository(RetrofitClient.apiService, tokenManager) }
    val socketManager by lazy { SocketManager(tokenManager) }
    val chatRepository by lazy { ChatRepository(RetrofitClient.chatApiService, RetrofitClient.breakroomApiService, socketManager, tokenManager, context) }
    val breakroomRepository by lazy { BreakroomRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val blogRepository by lazy { BlogRepository(RetrofitClient.breakroomApiService, tokenManager, context) }
    val blogViewModel by lazy { BlogViewModel(blogRepository) }
    val friendsRepository by lazy { FriendsRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val friendsViewModel by lazy { FriendsViewModel(friendsRepository) }
    val profileRepository by lazy { ProfileRepository(RetrofitClient.breakroomApiService, tokenManager, context) }
    val profileViewModel by lazy { ProfileViewModel(profileRepository, authRepository) }
    val employmentRepository by lazy { EmploymentRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val employmentViewModel by lazy { EmploymentViewModel(employmentRepository) }
    val helpDeskRepository by lazy { HelpDeskRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val helpDeskViewModel by lazy { HelpDeskViewModel(helpDeskRepository) }
    val companyRepository by lazy { CompanyRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val companyPortalViewModel by lazy { CompanyPortalViewModel(companyRepository) }
    val lyricsRepository by lazy { LyricsRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val lyricLabViewModel by lazy { LyricLabViewModel(lyricsRepository) }
    val galleryRepository by lazy { GalleryRepository(RetrofitClient.breakroomApiService, tokenManager, context) }
    val artGalleryViewModel by lazy { ArtGalleryViewModel(galleryRepository) }
    val featuresRepository by lazy { FeaturesRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val moderationRepository by lazy { ModerationRepository(RetrofitClient.breakroomApiService, tokenManager) }
    val sessionsRepository by lazy { SessionsRepository(RetrofitClient.breakroomApiService, tokenManager, context) }
    val sessionsViewModel by lazy { SessionsViewModel(sessionsRepository) }
    val homeViewModel by lazy { HomeViewModel(authRepository, breakroomRepository) }
    val toolShedViewModel by lazy { ToolShedViewModel(breakroomRepository, featuresRepository) }
    val badgeViewModel by lazy { BadgeViewModel(RetrofitClient.breakroomApiService, tokenManager, socketManager) }
}
