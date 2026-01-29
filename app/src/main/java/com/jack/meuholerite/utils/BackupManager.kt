package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
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
    private val epaysUrl = "https://app.epays.com.br/"

    suspend fun backupData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val espelhos = db.espelhoDao().getAll()
            val recibos = db.reciboDao().getAll()

            val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)

            val userData = hashMapOf(
                "user_name" to userPrefs.getString("user_name", ""),
                "user_matricula" to userPrefs.getString("user_matricula", "")
            )

            val settingsData = hashMapOf(
                "dark_mode" to settingsPrefs.getBoolean("dark_mode", false),
                "hide_values_enabled" to settingsPrefs.getBoolean("hide_values_enabled", false),
                "app_lock_enabled" to settingsPrefs.getBoolean("app_lock_enabled", false),
                "app_lock_pin" to settingsPrefs.getString("app_lock_pin", ""),
                "has_dark_mode_set" to settingsPrefs.contains("dark_mode")
            )

            val cookies = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(epaysUrl) ?: ""
            }

            val backupMap = hashMapOf(
                "espelhos" to espelhos.map { entityToMap(it) },
                "recibos" to recibos.map { entityToMap(it) },
                "userData" to userData,
                "settings" to settingsData,
                "epays_cookies" to cookies,
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
                return@withContext Result.failure(Exception("Nenhum backup encontrado"))
            }

            // 1. Restaurar Espelhos
            try {
                val espelhosList = document.get("espelhos") as? List<Map<String, Any>> ?: emptyList()
                espelhosList.forEach { map ->
                    try { db.espelhoDao().insert(mapToEspelhoEntity(map)) } catch (e: Exception) { Log.e("BackupManager", "Erro item espelho", e) }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro lista espelhos", e) }

            // 2. Restaurar Recibos
            try {
                val recibosList = document.get("recibos") as? List<Map<String, Any>> ?: emptyList()
                recibosList.forEach { map ->
                    try { db.reciboDao().insert(mapToReciboEntity(map)) } catch (e: Exception) { Log.e("BackupManager", "Erro item recibo", e) }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro lista recibos", e) }

            // 3. Restaurar User Data
            try {
                val userData = document.get("userData") as? Map<*, *>
                if (userData != null) {
                    val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    userPrefs.edit().apply {
                        putString("user_name", userData["user_name"]?.toString())
                        putString("user_matricula", userData["user_matricula"]?.toString())
                        apply()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro userData", e) }

            // 4. Restaurar Configurações
            try {
                val settings = document.get("settings") as? Map<*, *>
                if (settings != null) {
                    val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)
                    settingsPrefs.edit().apply {
                        putBoolean("dark_mode", settings["dark_mode"] as? Boolean ?: false)
                        putBoolean("hide_values_enabled", settings["hide_values_enabled"] as? Boolean ?: false)
                        putBoolean("app_lock_enabled", settings["app_lock_enabled"] as? Boolean ?: false)
                        putString("app_lock_pin", settings["app_lock_pin"]?.toString())
                        apply()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro settings", e) }

            // 5. Restaurar Cookies
            try {
                val cookies = document.getString("epays_cookies")
                if (!cookies.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(null, true)
                        
                        // Limpa cookies antigos para evitar conflitos
                        cookieManager.removeAllCookies(null)
                        
                        cookies.split(";").forEach { cookie ->
                            val cleanCookie = cookie.trim()
                            if (cleanCookie.isNotEmpty()) {
                                cookieManager.setCookie(epaysUrl, cleanCookie)
                            }
                        }
                        cookieManager.flush()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro cookies", e) }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro crítico na restauração", e)
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
