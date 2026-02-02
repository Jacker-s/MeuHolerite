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
            Log.d("GlobalMessage", "Buscando mensagem mais recente...")
            // Voltando com a ordenação por timestamp para pegar sempre a última postada
            val snapshot = firestore.collection("global_messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val document = snapshot.documents[0]
                Log.d("GlobalMessage", "Documento encontrado: ${document.id}")
                
                val title = document.getString("title") ?: ""
                val content = document.getString("content") ?: ""
                val timestamp = document.getLong("timestamp") ?: 0L

                // Se vier vazio, avisar no log qual o problema
                if (title.isEmpty() && content.isEmpty()) {
                    Log.e("GlobalMessage", "ERRO: O documento existe mas os campos 'title' ou 'content' não foram encontrados. Verifique os nomes no Firebase!")
                }

                GlobalMessage(id = document.id, title = title, content = content, timestamp = timestamp)
            } else {
                Log.d("GlobalMessage", "Nenhuma mensagem encontrada na coleção.")
                null
            }
        } catch (e: Exception) {
            Log.e("GlobalMessageManager", "Erro ao buscar mensagem: ${e.message}")
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
