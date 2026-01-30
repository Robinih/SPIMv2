package com.cvsuagritech.spim.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.*

object LanguageManager {
    private const val PREFS_NAME = "app_preferences"
    private const val LANGUAGE_KEY = "language"
    
    /**
     * Wraps the context with the saved locale. 
     * This must be called in attachBaseContext of every Activity.
     */
    fun wrap(context: Context): Context {
        val languageCode = getCurrentLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        
        return context.createConfigurationContext(configuration)
    }

    /**
     * Saves the language and ensures it's written to disk immediately.
     */
    fun setLanguage(context: Context, languageCode: String) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Use commit() to ensure synchronous write before activity restart
        sharedPref.edit().putString(LANGUAGE_KEY, languageCode).commit()
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
}
