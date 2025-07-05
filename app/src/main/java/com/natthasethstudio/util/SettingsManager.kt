package com.natthasethstudio.sethpos.util

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREF_NAME = "app_settings"
    private const val KEY_ANIMATED_ANIMALS_ENABLED = "animated_animals_enabled"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun isAnimatedAnimalsEnabled(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_ANIMATED_ANIMALS_ENABLED, true)
    }
    
    fun setAnimatedAnimalsEnabled(context: Context, enabled: Boolean) {
        getSharedPreferences(context).edit()
            .putBoolean(KEY_ANIMATED_ANIMALS_ENABLED, enabled)
            .apply()
    }
} 