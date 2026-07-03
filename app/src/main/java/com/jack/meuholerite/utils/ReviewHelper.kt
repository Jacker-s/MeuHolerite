package com.jack.meuholerite.utils

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reviewDataStore by preferencesDataStore(name = "review_prefs")

object ReviewHelper {
    private val KEY_HAS_REVIEWED = booleanPreferencesKey("has_reviewed")
    private val KEY_TOTAL_USAGE_TIME = androidx.datastore.preferences.core.longPreferencesKey("total_usage_time")

    suspend fun hasReviewed(context: Context): Boolean {
        return context.reviewDataStore.data.map { it[KEY_HAS_REVIEWED] ?: false }.first()
    }

    suspend fun markAsReviewed(context: Context) {
        context.reviewDataStore.edit { it[KEY_HAS_REVIEWED] = true }
    }

    suspend fun resetReviewStatus(context: Context) {
        context.reviewDataStore.edit { 
            it[KEY_HAS_REVIEWED] = false 
        }
    }

    suspend fun resetUsageTime(context: Context) {
        context.reviewDataStore.edit { 
            it[KEY_TOTAL_USAGE_TIME] = 0L
        }
    }

    suspend fun incrementUsageTime(context: Context, millis: Long) {
        context.reviewDataStore.edit { prefs ->
            val current = prefs[KEY_TOTAL_USAGE_TIME] ?: 0L
            prefs[KEY_TOTAL_USAGE_TIME] = current + millis
        }
    }

    suspend fun getTotalUsageTime(context: Context): Long {
        return context.reviewDataStore.data.map { it[KEY_TOTAL_USAGE_TIME] ?: 0L }.first()
    }

    fun requestReview(activity: Activity, onComplete: () -> Unit = {}) {
        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    android.util.Log.d("ReviewHelper", "Fluxo de avaliação finalizado.")
                    onComplete()
                }
            } else {
                android.util.Log.e("ReviewHelper", "Erro ao solicitar review: ${task.exception?.message}")
                // Mesmo com erro, opcionalmente chamamos o complete para não travar a lógica do app
                onComplete()
            }
        }
    }
}
