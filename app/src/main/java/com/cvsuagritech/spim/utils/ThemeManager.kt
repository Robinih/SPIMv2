package com.cvsuagritech.spim.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS_NAME = "app_preferences"
    private const val THEME_KEY = "theme_mode"
    
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"
    
    fun setThemeMode(context: Context, themeMode: String) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(THEME_KEY, themeMode).commit()
        applyThemeMode(themeMode)
    }
    
    fun getCurrentThemeMode(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(THEME_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }
    
    fun getThemeDisplayName(themeMode: String): String {
        return when (themeMode) {
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            THEME_SYSTEM -> "System"
            else -> "System"
        }
    }
    
    private fun applyThemeMode(themeMode: String) {
        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    fun initializeTheme(context: Context) {
        val savedTheme = getCurrentThemeMode(context)
        applyThemeMode(savedTheme)
    }
}
