package com.jack.meuholerite.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RewardedInterstitialAdManager {
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false
    
    private const val AD_UNIT_ID = "ca-app-pub-7931782163570852/3279696925"
    
    private const val TAG = "RewardedAdManager"

    fun loadAd(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            if (AdsDataStore.isAdsRemoved(context)) {
                Log.d(TAG, "Anúncios removidos pelo usuário. Pulando carregamento.")
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                if (rewardedInterstitialAd != null || isLoading) return@withContext
                
                isLoading = true
                val adRequest = AdRequest.Builder().build()
                val currentAdUnitId = AD_UNIT_ID
                
                Log.d(TAG, "Solicitando anúncio... ID: $currentAdUnitId")

                RewardedInterstitialAd.load(context, currentAdUnitId, adRequest, object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        rewardedInterstitialAd = ad
                        isLoading = false
                        Log.d(TAG, "Anúncio carregado. ID: $currentAdUnitId")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        rewardedInterstitialAd = null
                        isLoading = false
                        Log.e(
                            TAG,
                            "Falha ao carregar anúncio. " +
                                "Código=${adError.code}, Domínio=${adError.domain}, " +
                                "Mensagem=${adError.message}, ResponseInfo=${adError.responseInfo}"
                        )
                        
                        // Tenta carregar novamente em 45 segundos
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(45000)
                            loadAd(context)
                        }
                    }
                })
            }
        }
    }

    fun showAd(activity: Activity, onAdDismissed: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            if (AdsDataStore.isAdsRemoved(activity)) {
                withContext(Dispatchers.Main) { onAdDismissed() }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                val ad = rewardedInterstitialAd
                if (ad != null) {
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Anúncio fechado.")
                            rewardedInterstitialAd = null
                            loadAd(activity) 
                            onAdDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                            Log.e(TAG, "Erro ao exibir: ${adError.message}")
                            rewardedInterstitialAd = null
                            onAdDismissed()
                        }
                    }
                    
                    ad.show(activity) { rewardItem ->
                        Log.d(TAG, "Usuário ganhou recompensa: ${rewardItem.amount}")
                    }
                } else {
                    Log.d(TAG, "Aviso: Anúncio não disponível. Tentando carregar...")
                    onAdDismissed()
                    loadAd(activity)
                }
            }
        }
    }
}
