package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class GlobalMessage(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val timestamp: Long = 0
)

class GlobalMessageManager(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("global_messages_prefs", Context.MODE_PRIVATE)

    suspend fun fetchLatestMessage(): GlobalMessage? {
        return try {
            val snapshot = firestore.collection("global_messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val document = snapshot.documents[0]
                GlobalMessage(
                    id = document.id,
                    title = document.getString("title") ?: "",
                    content = document.getString("content") ?: "",
                    timestamp = document.getLong("timestamp") ?: 0
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GlobalMessageManager", "Erro ao buscar mensagem global", e)
            null
        }
    }

    fun isMessageNew(messageId: String): Boolean {
        val lastId = prefs.getString("last_message_id", "")
        return lastId != messageId
    }

    fun markMessageAsSeen(messageId: String) {
        prefs.edit().putString("last_message_id", messageId).apply()
    }
}
