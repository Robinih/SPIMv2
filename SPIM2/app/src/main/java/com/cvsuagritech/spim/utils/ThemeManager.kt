package com.cvsuagritech.spim.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS_NAME = "app_preferences"
    private const val THEME_KEY = "theme_mode"
    
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"
    
    /**
     * Set the app theme mode
     * @param context Application context
     * @param themeMode Theme mode (light, dark, or system)
     */
    fun setThemeMode(context: Context, themeMode: String) {
        // Save theme preference to SharedPreferences
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(THEME_KEY, themeMode)
            apply()
        }
        
        // Apply the theme mode
        applyThemeMode(themeMode)
    }
    
    /**
     * Get the current theme mode
     * @param context Application context
     * @return Current theme mode
     */
    fun getCurrentThemeMode(context: Context): String {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(THEME_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }
    
    /**
     * Get the display name for a theme mode
     * @param themeMode Theme mode code
     * @return Display name for the theme mode
     */
    fun getThemeDisplayName(themeMode: String): String {
        return when (themeMode) {
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            THEME_SYSTEM -> "System"
            else -> "System"
        }
    }
    
    /**
     * Get theme mode code from display name
     * @param displayName Display name
     * @return Theme mode code
     */
    fun getThemeModeCode(displayName: String): String {
        return when (displayName) {
            "Light" -> THEME_LIGHT
            "Dark" -> THEME_DARK
            "System" -> THEME_SYSTEM
            else -> THEME_SYSTEM
        }
    }
    
    /**
     * Apply theme mode to the app
     * @param themeMode Theme mode to apply
     */
    private fun applyThemeMode(themeMode: String) {
        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    /**
     * Initialize theme on app startup
     * @param context Application context
     */
    fun initializeTheme(context: Context) {
        val savedTheme = getCurrentThemeMode(context)
        applyThemeMode(savedTheme)
    }
}
