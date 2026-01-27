package com.jack.meuholerite.utils

import android.content.Context
import com.google.gson.Gson
import com.jack.meuholerite.model.EspelhoPonto

class StorageManager(context: Context) {
    private val prefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveEspelho(espelho: EspelhoPonto) {
        val json = gson.toJson(espelho)
        prefs.edit().putString("saved_espelho", json).apply()
    }

    fun getSavedEspelho(): EspelhoPonto? {
        val json = prefs.getString("saved_espelho", null)
        return if (json != null) {
            try {
                gson.fromJson(json, EspelhoPonto::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearData() {
        prefs.edit().remove("saved_espelho").apply()
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("dark_mode", false)
    }

    fun hasDarkModeSet(): Boolean {
        return prefs.contains("dark_mode")
    }
}
