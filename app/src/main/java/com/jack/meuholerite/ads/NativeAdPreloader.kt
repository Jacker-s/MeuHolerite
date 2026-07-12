package com.jack.meuholerite.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.jack.meuholerite.ui.NativeAdPool
import com.jack.meuholerite.ui.NativeAdSize

object NativeAdPreloader {
    fun preload(context: Context, adUnitId: String, size: NativeAdSize, count: Int = 1) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                NativeAdPool.addToPool(adUnitId, size, nativeAd)
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("NativeAdPreloader", "Failed to load: ${error.message}")
                }
            })
            .build()
        
        for (i in 0 until count) {
            adLoader.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        }
    }
}
