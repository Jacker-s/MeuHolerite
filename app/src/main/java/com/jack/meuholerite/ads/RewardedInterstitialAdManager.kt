package com.jack.meuholerite.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

object RewardedInterstitialAdManager {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private const val AD_UNIT_ID = "ca-app-pub-7931782163570852/3279696925"
    private const val TAG = "RewardedAdManager"

    fun loadAd(context: Context) {
        if (rewardedInterstitialAd != null) return

        val adRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(context, AD_UNIT_ID, adRequest, object : RewardedInterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedInterstitialAd) {
                rewardedInterstitialAd = ad
                Log.d(TAG, "Ad was loaded.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedInterstitialAd = null
                Log.d(TAG, adError.message)
            }
        })
    }

    fun showAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (rewardedInterstitialAd != null) {
            rewardedInterstitialAd?.show(activity) { rewardItem ->
                // User earned reward
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            }
            rewardedInterstitialAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedInterstitialAd = null
                    loadAd(activity) // Load next ad
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    rewardedInterstitialAd = null
                    onAdDismissed()
                }
            }
        } else {
            Log.d(TAG, "Ad wasn't ready.")
            onAdDismissed()
            loadAd(activity)
        }
    }
}
