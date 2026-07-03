package com.jack.meuholerite.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout

data class Suggestion(
    val id: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val suggestion: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val timestamp: Long = 0,
    val status: String = "PENDENTE"
)

class SuggestionManager {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun sendSuggestion(
        suggestionText: String,
        contactEmail: String = "",
        contactPhone: String = ""
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            withTimeout(15000) { // 15 segundos de timeout
                val user = auth.currentUser
                val data = hashMapOf(
                    "userEmail" to (user?.email ?: "anônimo"),
                    "userName" to (user?.displayName ?: "Usuário"),
                    "suggestion" to suggestionText,
                    "contactEmail" to contactEmail,
                    "contactPhone" to contactPhone,
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "PENDENTE"
                )

                Log.d("SuggestionManager", "Enviando sugestão para o Firestore...")
                firestore.collection("suggestions")
                    .add(data)
                    .await()
                
                Log.d("SuggestionManager", "Sugestão enviada com sucesso!")
            }
            true to null
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is FirebaseFirestoreException -> "Erro no banco (${e.code})"
                is kotlinx.coroutines.TimeoutCancellationException -> "Tempo limite excedido"
                else -> e.message ?: "Erro desconhecido"
            }
            Log.e("SuggestionManager", "Erro ao enviar sugestão: $errorMsg", e)
            false to errorMsg
        }
    }

    suspend fun getSuggestions(): List<Suggestion> = withContext(Dispatchers.IO) {
        try {
            val fiveDaysAgo = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000L)
            val snapshot = firestore.collection("suggestions")
                .whereGreaterThan("timestamp", fiveDaysAgo)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.map { doc ->
                Suggestion(
                    id = doc.id,
                    userEmail = doc.getString("userEmail") ?: "",
                    userName = doc.getString("userName") ?: "",
                    suggestion = doc.getString("suggestion") ?: "",
                    contactEmail = doc.getString("contactEmail") ?: "",
                    contactPhone = doc.getString("contactPhone") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    status = doc.getString("status") ?: "PENDENTE"
                )
            }
        } catch (e: Exception) {
            Log.e("SuggestionManager", "Erro ao buscar sugestões: ${e.message}")
            emptyList()
        }
    }
}

