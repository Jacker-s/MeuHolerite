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
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun clearData() {
        prefs.edit().remove("saved_espelho").apply()
    }

    // ======================
    // 🌙 DARK MODE
    // ======================
    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("dark_mode", false)
    }

    fun hasDarkModeSet(): Boolean {
        return prefs.contains("dark_mode")
    }

    // ======================
    // 👁️ VISIBILIDADE
    // ======================
    fun isHideValuesEnabled(): Boolean {
        return prefs.getBoolean("hide_values_enabled", false)
    }

    fun setHideValues(enabled: Boolean) {
        prefs.edit().putBoolean("hide_values_enabled", enabled).apply()
    }

    // ======================
    // 🔐 APP LOCK / SEGURANÇA
    // ======================
    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean("app_lock_enabled", false)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    fun hasPin(): Boolean {
        return !prefs.getString("app_lock_pin", "").isNullOrBlank()
    }

    fun getPin(): String {
        return prefs.getString("app_lock_pin", "") ?: ""
    }

    fun setPin(pin: String) {
        prefs.edit().putString("app_lock_pin", pin).apply()
    }

    fun clearPin() {
        prefs.edit().remove("app_lock_pin").apply()
    }
}
