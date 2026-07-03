package com.jack.meuholerite.ads

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore by preferencesDataStore(name = "ads_prefs")

object AdsDataStore {
    private val KEY_LAST_DAILY_SHOWN_TIME = longPreferencesKey("last_daily_shown_time")
    private val KEY_LAST_INTERVAL_SHOWN_TIME = longPreferencesKey("last_interval_shown_time")
    private val KEY_WAS_SHOWN_AFTER_IMPORT = booleanPreferencesKey("was_shown_after_import")
    private val KEY_ADS_REMOVED = booleanPreferencesKey("ads_removed")
    private val KEY_REMOVE_ADS_PROMPT_SHOWN = booleanPreferencesKey("remove_ads_prompt_shown")
    private val KEY_TOTAL_ADS_SHOWN = androidx.datastore.preferences.core.intPreferencesKey("total_ads_shown")
    private val KEY_APP_OPEN_COUNT = androidx.datastore.preferences.core.intPreferencesKey("app_open_count")

    suspend fun isAdsRemoved(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_ADS_REMOVED] ?: false }.first()
    }

    fun isAdsRemovedFlow(context: Context) = context.dataStore.data.map { it[KEY_ADS_REMOVED] ?: false }

    suspend fun setAdsRemoved(context: Context, removed: Boolean) {
        context.dataStore.edit { it[KEY_ADS_REMOVED] = removed }
    }

    suspend fun incrementAppOpenCount(context: Context) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_APP_OPEN_COUNT] ?: 0
            prefs[KEY_APP_OPEN_COUNT] = current + 1
        }
    }

    suspend fun canShowDonationBanner(context: Context): Boolean {
        if (isAdsRemoved(context)) return false
        
        val count = context.dataStore.data.map { it[KEY_APP_OPEN_COUNT] ?: 0 }.first()
        // Mostra a cada 3 aberturas (3, 6, 9...)
        return count > 0 && count % 3 == 0
    }

    suspend fun wasRemoveAdsPromptShown(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_REMOVE_ADS_PROMPT_SHOWN] ?: false }.first()
    }

    suspend fun markRemoveAdsPromptShown(context: Context) {
        context.dataStore.edit { it[KEY_REMOVE_ADS_PROMPT_SHOWN] = true }
    }

    suspend fun canShowDailyAd(context: Context): Boolean {
        val lastShown = context.dataStore.data.map { it[KEY_LAST_DAILY_SHOWN_TIME] ?: 0L }.first()
        if (lastShown == 0L) return true
        
        val lastCalendar = Calendar.getInstance().apply { timeInMillis = lastShown }
        val currentCalendar = Calendar.getInstance()
        
        return lastCalendar.get(Calendar.DAY_OF_YEAR) != currentCalendar.get(Calendar.DAY_OF_YEAR) ||
               lastCalendar.get(Calendar.YEAR) != currentCalendar.get(Calendar.YEAR)
    }

    suspend fun markDailyAdShown(context: Context) {
        context.dataStore.edit { it[KEY_LAST_DAILY_SHOWN_TIME] = System.currentTimeMillis() }
    }

    suspend fun canShowIntervalAd(context: Context): Boolean {
        val lastShown = context.dataStore.data.map { it[KEY_LAST_INTERVAL_SHOWN_TIME] ?: 0L }.first()
        val threeMinutesInMillis = 3 * 60 * 1000L
        return (System.currentTimeMillis() - lastShown) >= threeMinutesInMillis
    }

    suspend fun markIntervalAdShown(context: Context) {
        context.dataStore.edit { it[KEY_LAST_INTERVAL_SHOWN_TIME] = System.currentTimeMillis() }
    }

    suspend fun wasShownAfterImport(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_WAS_SHOWN_AFTER_IMPORT] ?: false }.first()
    }

    suspend fun markShownAfterImport(context: Context) {
        context.dataStore.edit { it[KEY_WAS_SHOWN_AFTER_IMPORT] = true }
    }

    suspend fun getTotalAdsShown(context: Context): Int {
        return context.dataStore.data.map { it[KEY_TOTAL_ADS_SHOWN] ?: 0 }.first()
    }

    suspend fun incrementAdsShown(context: Context) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_TOTAL_ADS_SHOWN] ?: 0
            prefs[KEY_TOTAL_ADS_SHOWN] = current + 1
        }
    }
}
