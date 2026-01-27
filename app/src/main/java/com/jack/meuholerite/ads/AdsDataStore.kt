package com.jack.meuholerite.ads

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ads_prefs")

object AdsDataStore {
    private val KEY_SHOWN_AFTER_IMPORT = booleanPreferencesKey("rewarded_interstitial_shown_after_import")
    private val KEY_SHOWN_HOME_TIMED = booleanPreferencesKey("rewarded_interstitial_shown_home_timed")

    suspend fun wasShownAfterImport(context: Context): Boolean {
        return context.dataStore.data
            .map { prefs -> prefs[KEY_SHOWN_AFTER_IMPORT] ?: false }
            .first()
    }

    suspend fun markShownAfterImport(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOWN_AFTER_IMPORT] = true
        }
    }

    suspend fun wasShownHomeTimed(context: Context): Boolean {
        return context.dataStore.data
            .map { prefs -> prefs[KEY_SHOWN_HOME_TIMED] ?: false }
            .first()
    }

    suspend fun markShownHomeTimed(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOWN_HOME_TIMED] = true
        }
    }
}
