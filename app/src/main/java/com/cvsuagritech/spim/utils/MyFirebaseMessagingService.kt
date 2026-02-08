package com.cvsuagritech.spim.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cvsuagritech.spim.MainNavActivity
import com.cvsuagritech.spim.R
import com.cvsuagritech.spim.api.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging Service
 * Receives push notifications from Firebase and displays them in the status bar
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FCM", "Message received from: ${message.from}")
        
        // Extract notification data
        val title = message.notification?.title ?: "SPIM Alert"
        val body = message.notification?.body ?: ""
        val level = message.data["level"] ?: "Medium"
        
        // Display notification in status bar
        showNotification(title, body, level)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM token: $token")
        
        // Send updated token to backend
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId()
        
        if (userId != -1) {
            sendTokenToServer(userId, token)
        } else {
            // Save token for later registration when user logs in
            sessionManager.savePendingFcmToken(token)
        }
    }

    private fun sendTokenToServer(userId: Int, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.registerDeviceToken(
                    com.cvsuagritech.spim.api.RegisterDeviceTokenRequest(userId, token)
                )
                if (response.isSuccessful) {
                    Log.d("FCM", "Token registered successfully with server")
                } else {
                    Log.e("FCM", "Failed to register token: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error registering token: ${e.message}")
            }
        }
    }

    private fun showNotification(title: String, body: String, level: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SPIM_ALERTS",
                "SPIM Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts about pest activity and weather"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainNavActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build the notification
        val notification = NotificationCompat.Builder(this, "SPIM_ALERTS")
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        
        // Show notification
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
        Log.d("FCM", "Notification displayed: $title")
    }
}
