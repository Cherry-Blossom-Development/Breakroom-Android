package com.example.breakroom.network

import com.example.breakroom.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ChatApiService {

    // Room endpoints
    @GET("api/chat/rooms")
    suspend fun getRooms(
        @Header("Authorization") token: String
    ): Response<RoomsResponse>

    @POST("api/chat/rooms")
    suspend fun createRoom(
        @Header("Authorization") token: String,
        @Body request: CreateRoomRequest
    ): Response<RoomResponse>

    @PUT("api/chat/rooms/{roomId}")
    suspend fun updateRoom(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int,
        @Body request: UpdateRoomRequest
    ): Response<RoomResponse>

    @DELETE("api/chat/rooms/{roomId}")
    suspend fun deleteRoom(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int
    ): Response<Unit>

    // Message endpoints
    @GET("api/chat/rooms/{roomId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int,
        @Query("limit") limit: Int = 50,
        @Query("before") before: Int? = null
    ): Response<MessagesResponse>

    @POST("api/chat/rooms/{roomId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int,
        @Body request: SendMessageRequest
    ): Response<MessageResponse>

    @Multipart
    @POST("api/chat/rooms/{roomId}/image")
    suspend fun uploadImage(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int,
        @Part image: MultipartBody.Part,
        @Part("message") message: RequestBody? = null
    ): Response<MessageResponse>

    // Member endpoints
    @GET("api/chat/rooms/{roomId}/members")
    suspend fun getRoomMembers(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int
    ): Response<MembersResponse>

    // Invite endpoints
    @GET("api/chat/invites")
    suspend fun getPendingInvites(
        @Header("Authorization") token: String
    ): Response<InvitesResponse>

    @POST("api/chat/invites/{roomId}/accept")
    suspend fun acceptInvite(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int
    ): Response<RoomResponse>

    @POST("api/chat/invites/{roomId}/decline")
    suspend fun declineInvite(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int
    ): Response<Unit>

    @POST("api/chat/rooms/{roomId}/invite")
    suspend fun inviteUser(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: Int,
        @Body request: InviteUserRequest
    ): Response<Unit>
}
