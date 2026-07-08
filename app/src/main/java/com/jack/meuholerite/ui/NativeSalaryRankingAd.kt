package com.jack.meuholerite.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.jack.meuholerite.R

enum class NativeAdSize {
    Regular,
    Compact
}

private object NativeAdPool {
    private val availableAds = mutableMapOf<String, MutableList<NativeAd>>()
    private val activeAds = mutableSetOf<NativeAd>()
    private val seenAds = mutableSetOf<NativeAd>()

    private fun poolKey(adUnitId: String, size: NativeAdSize): String = "$adUnitId:$size"

    fun markSeen(ad: NativeAd) {
        synchronized(this) {
            seenAds.add(ad)
        }
    }

    fun acquire(adUnitId: String, size: NativeAdSize): NativeAd? {
        synchronized(this) {
            val key = poolKey(adUnitId, size)
            val list = availableAds[key] ?: return null
            while (list.isNotEmpty()) {
                val ad = list.removeAt(0)
                if (ad !in activeAds) {
                    activeAds.add(ad)
                    return ad
                }
            }
            return null
        }
    }

    fun release(adUnitId: String, size: NativeAdSize, ad: NativeAd) {
        synchronized(this) {
            activeAds.remove(ad)
            val wasSeen = seenAds.remove(ad)
            if (wasSeen) {
                ad.destroy()
                return
            }

            val key = poolKey(adUnitId, size)
            val list = availableAds.getOrPut(key) { mutableListOf() }
            if (list.size < 4) {
                list.add(ad)
            } else {
                ad.destroy()
            }
        }
    }
}

private class NativeInlineAdHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var currentAdUnitId: String? = null
    private var currentSize: NativeAdSize? = null
    private var currentNativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null
    private var onVisibilityChanged: ((Boolean) -> Unit)? = null

    fun bind(adUnitId: String, size: NativeAdSize, onVisibilityChanged: (Boolean) -> Unit) {
        this.onVisibilityChanged = onVisibilityChanged

        if (currentAdUnitId == adUnitId && currentSize == size && nativeAdView != null) return

        currentAdUnitId = adUnitId
        currentSize = size
        onVisibilityChanged(false)

        val cachedAd = NativeAdPool.acquire(adUnitId, size)
        if (cachedAd != null) {
            displayAd(cachedAd, size)
        } else {
            loadAd(adUnitId, size)
        }
    }

    fun release() {
        val ad = currentNativeAd
        val adUnitId = currentAdUnitId
        val size = currentSize
        if (ad != null && adUnitId != null && size != null) {
            NativeAdPool.release(adUnitId, size, ad)
        }
        currentNativeAd = null
        nativeAdView = null
        removeAllViews()
        onVisibilityChanged?.invoke(false)
    }

    private fun displayAd(nativeAd: NativeAd, size: NativeAdSize) {
        currentNativeAd = nativeAd
        val adView = createNativeAdView(context, size)
        bindNativeAd(adView, nativeAd, size)
        removeAllViews()
        addView(adView)
        nativeAdView = adView
        onVisibilityChanged?.invoke(true)
    }

    private fun loadAd(adUnitId: String, size: NativeAdSize) {
        removeAllViews()
        nativeAdView = null

        var loadedAd: NativeAd? = null

        AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                loadedAd = nativeAd
                if (currentAdUnitId == adUnitId && currentSize == size) {
                    displayAd(nativeAd, size)
                } else {
                    NativeAdPool.release(adUnitId, size, nativeAd)
                }
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder().build()
            )
            .withAdListener(
                object : AdListener() {
                    override fun onAdClicked() {
                        Log.d("NativeInlineAd", "onAdClicked: $adUnitId")
                    }

                    override fun onAdImpression() {
                        Log.d("NativeInlineAd", "onAdImpression: $adUnitId")
                        loadedAd?.let {
                            NativeAdPool.markSeen(it)
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("NativeInlineAd", "onAdFailedToLoad: $adUnitId - ${error.message}")
                        if (currentAdUnitId == adUnitId && currentSize == size) {
                            removeAllViews()
                            nativeAdView = null
                            onVisibilityChanged?.invoke(false)
                        }
                    }
                }
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }
}

@Composable
fun NativeSalaryRankingAd(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    NativeInlineAd(
        adUnitId = adUnitId,
        modifier = modifier
    )
}

@Composable
fun NativeInlineAd(
    adUnitId: String,
    size: NativeAdSize = NativeAdSize.Regular,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostView = remember(adUnitId, size) { NativeInlineAdHostView(context) }
    var isAdVisible by remember(adUnitId, size) { mutableStateOf(false) }

    DisposableEffect(hostView) {
        onDispose { hostView.release() }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isAdVisible) {
                    Modifier
                        .padding(bottom = 10.dp)
                        .heightIn(min = if (size == NativeAdSize.Compact) 72.dp else 124.dp)
                } else {
                    Modifier
                }
            ),
        factory = {
            hostView.apply {
                bind(adUnitId, size) { visible ->
                    isAdVisible = visible
                }
            }
        },
        update = {
            it.bind(adUnitId, size) { visible ->
                isAdVisible = visible
            }
        }
    )
}

@Composable
fun PrimeNativeInlineAds(
    adUnitId: String,
    size: NativeAdSize,
    count: Int
) {
    // No-op: the native host view now owns loading for stability in Compose.
}

private fun createNativeAdView(context: Context, size: NativeAdSize): NativeAdView {
    val layoutRes =
        if (size == NativeAdSize.Compact) R.layout.native_ad_compact else R.layout.native_ad_regular
    return LayoutInflater.from(context).inflate(layoutRes, null, false) as NativeAdView
}

private fun bindNativeAd(
    adView: NativeAdView,
    nativeAd: NativeAd,
    size: NativeAdSize
) {
    val contentView = adView.findViewById<View>(R.id.ad_content)
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val ctaView = adView.findViewById<TextView>(R.id.ad_call_to_action)
    val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
    val imageView = adView.findViewById<ImageView>(R.id.ad_image)
    val mediaView = adView.findViewById<MediaView?>(R.id.ad_media)

    contentView.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            Log.d("NativeInlineAd", "content touch down")
        }
        false
    }

    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.callToActionView = contentView
    adView.iconView = iconView
    adView.imageView = imageView
    adView.mediaView = mediaView

    headlineView.text = nativeAd.headline

    bodyView.apply {
        text = nativeAd.body
        visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    ctaView.apply {
        text = nativeAd.callToAction ?: "Abrir"
        visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        isClickable = false
        isFocusable = false
    }

    iconView.apply {
        val icon = nativeAd.icon?.drawable
        if (icon != null) {
            setImageDrawable(icon)
            visibility = View.VISIBLE
        } else {
            visibility = View.GONE
        }
    }

    val imageDrawable = nativeAd.images.firstOrNull()?.drawable
    if (nativeAd.mediaContent != null) {
        mediaView?.apply {
            visibility = View.VISIBLE
            setMediaContent(nativeAd.mediaContent)
        }
        imageView.visibility = View.GONE
    } else if (imageDrawable != null) {
        mediaView?.visibility = View.GONE
        imageView.apply {
            setImageDrawable(imageDrawable)
            visibility = View.VISIBLE
        }
    } else {
        mediaView?.visibility = View.GONE
        imageView.visibility = View.GONE
    }

    adView.setNativeAd(nativeAd)
}
