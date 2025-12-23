package com.vexor.vault.ui

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.vexor.vault.security.VaultPreferences

object ThemeManager {
    
    private const val PREF_THEME = "app_theme"
    
    // Theme modes
    const val THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    const val THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES
    
    fun applyTheme(activity: Activity) {
        val prefs = activity.getSharedPreferences("vexor_settings", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt(PREF_THEME, THEME_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
    
    fun setTheme(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences("vexor_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_THEME, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
    
    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences("vexor_settings", Context.MODE_PRIVATE)
        return prefs.getInt(PREF_THEME, THEME_SYSTEM)
    }
}
