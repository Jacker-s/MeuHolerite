package com.jack.meuholerite

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import com.jack.meuholerite.ads.AppOpenAdManager

class MainApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Inicializa SDK do AdMob e pré-carrega o App Open Ad para uso futuro
        val testDeviceIds = listOf("75CC863ABE16F0E29F68051857DFB33D")
        val configuration = com.google.android.gms.ads.RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        com.google.android.gms.ads.MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(this) {}
        AppOpenAdManager.loadAd(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let {
            AppOpenAdManager.showAdIfAvailable(it)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
