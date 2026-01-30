package com.cvsuagritech.spim.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_NOTIFICATION_ID = "last_notification_id"
    }

    fun setLogin(isLoggedIn: Boolean, username: String? = null, userId: Int = -1) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
            putString(KEY_USERNAME, username)
            putInt(KEY_USER_ID, userId)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getUserId(): Int {
        return prefs.getInt(KEY_USER_ID, -1)
    }

    fun getLastNotificationId(): Int {
        return prefs.getInt(KEY_LAST_NOTIFICATION_ID, -1)
    }

    fun setLastNotificationId(id: Int) {
        val currentMax = getLastNotificationId()
        if (id > currentMax) {
            prefs.edit().putInt(KEY_LAST_NOTIFICATION_ID, id).apply()
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
