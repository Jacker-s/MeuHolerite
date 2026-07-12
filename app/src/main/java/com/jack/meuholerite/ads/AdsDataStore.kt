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
    private val KEY_LAST_TIMED_INTERSTITIAL_SHOWN_TIME = longPreferencesKey("last_timed_interstitial_shown_time")
    private val KEY_WAS_SHOWN_AFTER_IMPORT = booleanPreferencesKey("was_shown_after_import")
    private val KEY_ADS_REMOVED = booleanPreferencesKey("ads_removed")
    private val KEY_REMOVE_ADS_PROMPT_SHOWN = booleanPreferencesKey("remove_ads_prompt_shown")
    private val KEY_TOTAL_ADS_SHOWN = androidx.datastore.preferences.core.intPreferencesKey("total_ads_shown")
    private val KEY_APP_OPEN_COUNT = androidx.datastore.preferences.core.intPreferencesKey("app_open_count")
    private val KEY_DAILY_COUNT = androidx.datastore.preferences.core.intPreferencesKey("daily_ad_count")
    private val KEY_DAILY_COUNT_DATE = longPreferencesKey("daily_ad_count_date")
    const val INITIAL_TIMED_INTERSTITIAL_DELAY_MS = 3 * 60 * 1000L
    private const val MIN_INTERVAL_MS = 90_000L
    private const val TIMED_INTERSTITIAL_REPEAT_MS = 10 * 60 * 1000L
    private const val MAX_ADS_PER_DAY = 2

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
        val now = System.currentTimeMillis()
        val prefs = context.dataStore.data.first()
        val countDateMs = prefs[KEY_DAILY_COUNT_DATE] ?: 0L
        val count = prefs[KEY_DAILY_COUNT] ?: 0
        val countCal = java.util.Calendar.getInstance().apply { timeInMillis = countDateMs }
        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val isNewDay = countCal.get(java.util.Calendar.DAY_OF_YEAR) != nowCal.get(java.util.Calendar.DAY_OF_YEAR) || countCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR)
        return if (isNewDay) true else count < MAX_ADS_PER_DAY
    }

    suspend fun markDailyAdShown(context: Context) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val countDateMs = prefs[KEY_DAILY_COUNT_DATE] ?: 0L
            val count = prefs[KEY_DAILY_COUNT] ?: 0
            val countCal = java.util.Calendar.getInstance().apply { timeInMillis = countDateMs }
            val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val isNewDay = countCal.get(java.util.Calendar.DAY_OF_YEAR) != nowCal.get(java.util.Calendar.DAY_OF_YEAR) || countCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR)
            prefs[KEY_DAILY_COUNT_DATE] = now
            prefs[KEY_DAILY_COUNT] = if (isNewDay) 1 else count + 1
            prefs[KEY_LAST_DAILY_SHOWN_TIME] = now
        }
    }

    suspend fun canShowIntervalAd(context: Context): Boolean {
        val lastShown = context.dataStore.data.map { it[KEY_LAST_INTERVAL_SHOWN_TIME] ?: 0L }.first()
        return (System.currentTimeMillis() - lastShown) >= MIN_INTERVAL_MS
    }

    suspend fun markIntervalAdShown(context: Context) {
        context.dataStore.edit { it[KEY_LAST_INTERVAL_SHOWN_TIME] = System.currentTimeMillis() }
    }

    suspend fun canShowTimedInterstitial(context: Context): Boolean {
        val lastShown = context.dataStore.data.map { it[KEY_LAST_TIMED_INTERSTITIAL_SHOWN_TIME] ?: 0L }.first()
        return (System.currentTimeMillis() - lastShown) >= TIMED_INTERSTITIAL_REPEAT_MS
    }

    suspend fun markTimedInterstitialShown(context: Context) {
        context.dataStore.edit { it[KEY_LAST_TIMED_INTERSTITIAL_SHOWN_TIME] = System.currentTimeMillis() }
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
