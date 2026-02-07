package com.cvsuagritech.spim.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @Multipart
    @POST("api/sync/identify")
    suspend fun syncIdentify(
        @Part("user_id") userId: RequestBody,
        @Part("insect_name") insectName: RequestBody,
        @Part("confidence") confidence: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<SyncResponse>

    @Multipart
    @POST("api/sync/count")
    suspend fun syncCount(
        @Part("user_id") userId: RequestBody,
        @Part("total_count") totalCount: RequestBody,
        @Part("breakdown") breakdown: RequestBody,
        @Part("confidence") confidence: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<SyncResponse>

    @Multipart
    @POST("api/recommendation")
    suspend fun submitRecommendation(
        @Part("user_id") userId: RequestBody,
        @Part("insect_name") insectName: RequestBody?,
        @Part("description") description: RequestBody,
        @Part image: MultipartBody.Part
    ): Response<SyncResponse>

    @GET("api/stats/dashboard")
    suspend fun getDashboardStats(): Response<DashboardStats>

    // Updated based on the latest guide: user_id is a REQUIRED query parameter
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("user_id") userId: Int
    ): Response<List<AppNotification>>

    // Updated: Mark all as read using user_id query parameter
    @POST("api/notifications/read/all")
    suspend fun markAllNotificationsAsRead(
        @Query("user_id") userId: Int
    ): Response<MarkReadResponse>
}

// Response model for mark read action as per guide
data class MarkReadResponse(
    val message: String,
    val count: Int
)
