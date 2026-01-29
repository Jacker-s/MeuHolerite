package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.EspelhoEntity
import com.jack.meuholerite.database.ReciboEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BackupManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()

    suspend fun backupData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val espelhos = db.espelhoDao().getAll()
            val recibos = db.reciboDao().getAll()

            val backupMap = hashMapOf(
                "espelhos" to espelhos.map { entityToMap(it) },
                "recibos" to recibos.map { entityToMap(it) },
                "lastBackup" to System.currentTimeMillis()
            )

            firestore.collection("backups").document(userId)
                .set(backupMap, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao fazer backup", e)
            Result.failure(e)
        }
    }

    suspend fun restoreData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val document = firestore.collection("backups").document(userId).get().await()
            if (!document.exists()) {
                return@withContext Result.failure(Exception("Nenhum backup encontrado para este usuário"))
            }

            val espelhosList = document.get("espelhos") as? List<Map<String, Any>> ?: emptyList()
            val recibosList = document.get("recibos") as? List<Map<String, Any>> ?: emptyList()

            // Restaurar Espelhos
            espelhosList.forEach { map ->
                val entity = mapToEspelhoEntity(map)
                db.espelhoDao().insert(entity)
            }

            // Restaurar Recibos
            recibosList.forEach { map ->
                val entity = mapToReciboEntity(map)
                db.reciboDao().insert(entity)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao restaurar dados", e)
            Result.failure(e)
        }
    }

    private fun entityToMap(entity: Any): Map<String, Any?> {
        val json = gson.toJson(entity)
        return gson.fromJson(json, Map::class.java) as Map<String, Any?>
    }

    private fun mapToEspelhoEntity(map: Map<String, Any>): EspelhoEntity {
        val json = gson.toJson(map)
        return gson.fromJson(json, EspelhoEntity::class.java)
    }

    private fun mapToReciboEntity(map: Map<String, Any>): ReciboEntity {
        val json = gson.toJson(map)
        return gson.fromJson(json, ReciboEntity::class.java)
    }
}
