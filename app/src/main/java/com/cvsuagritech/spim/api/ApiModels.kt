package com.cvsuagritech.spim.api

import com.google.gson.annotations.SerializedName

// User Registration
data class RegisterRequest(
    val username: String,
    @SerializedName("full_name") val fullName: String,
    val password: String,
    val municipality: String,
    @SerializedName("street_barangay") val streetBarangay: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class RegisterResponse(
    val message: String
)

// User Login
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val message: String,
    @SerializedName("user_id") val userId: Int,
    val role: String
)

// Sync Responses
data class SyncResponse(
    val message: String
)

// Statistics
data class DashboardStats(
    val pests: Int,
    val beneficials: Int
)

// Notifications
data class AppNotification(
    val id: Int,
    val message: String,
    val level: String, // "High", "Medium", "Low"
    val timestamp: String,
    @SerializedName("is_read") val isRead: Boolean
)
