package com.jack.meuholerite.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jack.meuholerite.model.Candidatura
import kotlinx.coroutines.tasks.await

class CandidaturaRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun saveCandidatura(candidatura: Candidatura): String {
        val docRef = firestore.collection("candidaturas").document()
        val data = candidatura.copy(id = docRef.id)
        docRef.set(data).await()
        return docRef.id
    }

    suspend fun isAdmin(): Boolean {
        val user = auth.currentUser
        return user?.email == "ssj53415170@gmail.com"
    }

    fun getCandidaturasQuery(): Query {
        return firestore.collection("candidaturas")
            .orderBy("createdAt", Query.Direction.DESCENDING)
    }

    suspend fun updateExportado(id: String, exportado: Boolean) {
        firestore.collection("candidaturas").document(id)
            .update("exportado", exportado).await()
    }

    suspend fun deleteCandidatura(id: String) {
        firestore.collection("candidaturas").document(id).delete().await()
    }

    suspend fun getCandidaturaById(id: String): Candidatura? {
        return firestore.collection("candidaturas").document(id).get().await()
            .toObject(Candidatura::class.java)
    }
}
