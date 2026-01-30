package com.cvsuagritech.spim.utils

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LanguageManager {
    private const val PREFS_NAME = "app_preferences"
    private const val LANGUAGE_KEY = "language"
    
    fun setLanguage(context: Context, languageCode: String) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(LANGUAGE_KEY, languageCode)
            apply()
        }
        
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val config = Configuration()
        config.setLocale(locale)
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
    
    fun getCurrentLanguage(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(LANGUAGE_KEY, "en") ?: "en"
    }
    
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "tl" -> "Tagalog"
            "en" -> "English"
            else -> "English"
        }
    }
    
    fun getLanguageCode(displayName: String): String {
        return when (displayName) {
            "Tagalog" -> "tl"
            "English" -> "en"
            else -> "en"
        }
    }
}
