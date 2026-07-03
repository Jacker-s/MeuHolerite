package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale

data class GlobalMessage(
    val id: String = "", // Este será o messageId enviado pelo painel
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val buttonText: String? = null,
    val buttonUrl: String? = null,
    val timestamp: Long = 0
)

class GlobalMessageManager(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("global_messages_prefs", Context.MODE_PRIVATE)

    private var lastFetchTime = 0L

    suspend fun fetchLatestMessage(): GlobalMessage? {
        val savedMessage = getSavedMessage()
        val companyTopic = getCompanyTopicFromPrefs()
        val allowedTopics = mutableSetOf("global").apply {
            if (!companyTopic.isNullOrBlank()) add(companyTopic)
        }
        
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 30_000) {
            return savedMessage
        }
        
        return try {
            Log.d("GlobalMessage", "Buscando mensagem mais recente do Firestore...")
            val globalSnapshot = try {
                firestore.collection("global_messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(30)
                    .get()
                    .await()
            } catch (_: Exception) {
                null
            }
            val companySnapshot = try {
                firestore.collection("company_messages")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(30)
                    .get()
                    .await()
            } catch (_: Exception) {
                null
            }

            val latestGlobal = globalSnapshot?.documents?.firstOrNull { document ->
                val topic = document.getString("targetTopic")
                val resolvedTopic = if (topic.isNullOrBlank()) "global" else topic
                allowedTopics.contains(resolvedTopic)
            }?.let { document ->
                val mId = document.getString("messageId") ?: document.id
                GlobalMessage(
                    id = mId,
                    title = document.getString("title") ?: "",
                    content = document.getString("content") ?: "",
                    imageUrl = document.getString("imageUrl"),
                    videoUrl = document.getString("videoUrl"),
                    buttonText = document.getString("buttonText"),
                    buttonUrl = document.getString("buttonUrl"),
                    timestamp = document.readTimestampSafe()
                )
            }

            val latestCompany = companySnapshot?.documents?.firstOrNull { document ->
                val topic = document.getString("targetTopic")
                !topic.isNullOrBlank() && allowedTopics.contains(topic)
            }?.let { document ->
                val mId = document.getString("messageId") ?: document.id
                GlobalMessage(
                    id = mId,
                    title = document.getString("title") ?: "",
                    content = document.getString("content") ?: "",
                    imageUrl = document.getString("imageUrl"),
                    videoUrl = document.getString("videoUrl"),
                    buttonText = document.getString("buttonText"),
                    buttonUrl = document.getString("buttonUrl"),
                    timestamp = document.readTimestampSafe()
                )
            }

            val latestRemote = listOfNotNull(latestGlobal, latestCompany).maxByOrNull { it.timestamp }

            // Lógica de decisão:
            // 1. Priorizamos a mensagem com o timestamp mais recente entre a Global (Firestore) e a Individual (Recebida via FCM e salva localmente).
            val chosen = if (latestRemote != null && (savedMessage == null || latestRemote.timestamp >= savedMessage.timestamp)) {
                latestRemote
            } else {
                savedMessage
            }

            lastFetchTime = System.currentTimeMillis()

            // Sempre salvamos a escolha mais recente localmente
            if (chosen != null && chosen == latestRemote) {
                saveMessage(chosen)
            }

            chosen
        } catch (e: Exception) {
            Log.e("GlobalMessage", "Erro ao buscar: ${e.message}")
            savedMessage
        }
    }

    fun isMessageNew(messageId: String): Boolean {
        val lastId = prefs.getString("last_message_id", "")
        // Se messageId for "latest", sempre consideramos nova para fins de teste/notificação
        if (messageId == "latest") return true
        return lastId != messageId
    }

    fun markMessageAsSeen(messageId: String) {
        prefs.edit().putString("last_message_id", messageId).apply()
    }

    fun saveMessage(message: GlobalMessage) {
        prefs.edit().apply {
            putString("saved_msg_id", message.id)
            putString("saved_msg_title", message.title)
            putString("saved_msg_content", message.content)
            putString("saved_msg_image_url", message.imageUrl)
            putString("saved_msg_video_url", message.videoUrl)
            putString("saved_msg_button_text", message.buttonText)
            putString("saved_msg_button_url", message.buttonUrl)
            putLong("saved_msg_timestamp", message.timestamp)
            apply()
        }
    }

    fun getSavedMessage(): GlobalMessage? {
        val id = prefs.getString("saved_msg_id", null) ?: return null
        return GlobalMessage(
            id = id,
            title = prefs.getString("saved_msg_title", "") ?: "",
            content = prefs.getString("saved_msg_content", "") ?: "",
            imageUrl = prefs.getString("saved_msg_image_url", null),
            videoUrl = prefs.getString("saved_msg_video_url", null),
            buttonText = prefs.getString("saved_msg_button_text", null),
            buttonUrl = prefs.getString("saved_msg_button_url", null),
            timestamp = prefs.getLong("saved_msg_timestamp", 0L)
        )
    }

    private fun getCompanyTopicFromPrefs(): String? {
        val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val company = userPrefs.getString("user_company", null)?.trim().orEmpty()
        if (company.isBlank()) return null

        val noAccents = Normalizer.normalize(company, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        val normalized = noAccents
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
        if (normalized.isBlank()) return null

        return "empresa_$normalized".take(900)
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.readTimestampSafe(): Long {
    val any = get("timestamp")
    return when (any) {
        is Long -> any
        is Int -> any.toLong()
        is Double -> any.toLong()
        is Float -> any.toLong()
        is String -> any.toDoubleOrNull()?.toLong() ?: 0L
        else -> getLong("timestamp") ?: 0L
    }
}
