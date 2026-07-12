package com.jack.meuholerite.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

private val Context.appOpenDataStore by preferencesDataStore(name = "app_open_prefs")

object AppOpenAdManager {
    private const val OPEN_ADS_ENABLED = true
    private const val AD_UNIT_ID = "ca-app-pub-7931782163570852/8376762581"
    private const val TAG = "AppOpenAdManager"

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var loadTime: Long = 0

    private val KEY_LAST_SHOWN_DATE = longPreferencesKey("last_shown_date")

    fun loadAd(context: Context) {
        if (!OPEN_ADS_ENABLED) return
        if (isAdAvailable()) {
            return
        }

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = System.currentTimeMillis()
                    Log.d(TAG, "App Open Ad Loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d(TAG, "App Open Ad Failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity) {
        if (!OPEN_ADS_ENABLED) return
        if (isShowingAd) {
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            if (hasShownToday(activity)) {
                Log.d(TAG, "App Open Ad j foi exibido hoje. Pulando.")
                return@launch
            }
            
            if (AdsDataStore.isAdsRemoved(activity)) {
                return@launch
            }

            activity.runOnUiThread {
                if (!isAdAvailable()) {
                    Log.d(TAG, "App Open Ad no est disponvel. Carregando novo...")
                    loadAd(activity)
                    return@runOnUiThread
                }

                appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        appOpenAd = null
                        isShowingAd = false
                        loadAd(activity)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        appOpenAd = null
                        isShowingAd = false
                        loadAd(activity)
                    }

                    override fun onAdShowedFullScreenContent() {
                        isShowingAd = true
                        CoroutineScope(Dispatchers.IO).launch {
                            markShownToday(activity)
                        }
                    }
                }

                appOpenAd?.show(activity)
            }
        }
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = System.currentTimeMillis() - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    private suspend fun hasShownToday(context: Context): Boolean {
        val lastShownMs = context.appOpenDataStore.data.map { it[KEY_LAST_SHOWN_DATE] ?: 0L }.first()
        if (lastShownMs == 0L) return false

        val lastCal = Calendar.getInstance().apply { timeInMillis = lastShownMs }
        val nowCal = Calendar.getInstance()

        return lastCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
               lastCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    }

    private suspend fun markShownToday(context: Context) {
        context.appOpenDataStore.edit { prefs ->
            prefs[KEY_LAST_SHOWN_DATE] = System.currentTimeMillis()
        }
    }
}
