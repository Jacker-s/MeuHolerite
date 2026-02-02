package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
                "user_name" to (userPrefs.getString("user_name", "") ?: ""),
                "user_matricula" to (userPrefs.getString("user_matricula", "") ?: "")
            )

            val settingsData = hashMapOf(
                "dark_mode" to settingsPrefs.getBoolean("dark_mode", false),
                "hide_values_enabled" to settingsPrefs.getBoolean("hide_values_enabled", false),
                "app_lock_enabled" to settingsPrefs.getBoolean("app_lock_enabled", false),
                "app_lock_pin" to (settingsPrefs.getString("app_lock_pin", "") ?: ""),
                "has_dark_mode_set" to settingsPrefs.contains("dark_mode")
            )

            val backupMap = hashMapOf(
                "userData" to userData,
                "settings" to settingsData,
                "lastBackup" to System.currentTimeMillis(),
                "espelhos" to espelhos.map { it.toMap() },
                "recibos" to recibos.map { it.toMap() }
            )

            firestore.collection("backups").document(userId).set(backupMap, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro backup", e)
            Result.failure(e)
        }
    }

    suspend fun restoreData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val document = firestore.collection("backups").document(userId).get().await()
            if (!document.exists()) return@withContext Result.failure(Exception("Sem backup encontrado na nuvem."))

            // 1. Restaurar SharedPreferences (user_prefs)
            (document.get("userData") as? Map<*, *>)?.let { userData ->
                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().apply {
                    putString("user_name", userData["user_name"] as? String)
                    putString("user_matricula", userData["user_matricula"] as? String)
                    apply()
                }
            }

            // 2. Restaurar SharedPreferences (meu_holerite_prefs)
            (document.get("settings") as? Map<*, *>)?.let { settings ->
                context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE).edit().apply {
                    putBoolean("dark_mode", settings["dark_mode"] as? Boolean ?: false)
                    putBoolean("hide_values_enabled", settings["hide_values_enabled"] as? Boolean ?: false)
                    putBoolean("app_lock_enabled", settings["app_lock_enabled"] as? Boolean ?: false)
                    putString("app_lock_pin", settings["app_lock_pin"] as? String ?: "")
                    apply()
                }
            }

            // 3. Restaurar Banco de Dados (Espelhos)
            (document.get("espelhos") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.espelhoDao().insert(mapToEspelho(map))
                }
            }

            // 4. Restaurar Banco de Dados (Recibos)
            (document.get("recibos") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.reciboDao().insert(mapToRecibo(map))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro rest.", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid
        try {
            firestore.collection("backups").document(userId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao excluir backup", e)
            Result.failure(e)
        }
    }

    private fun EspelhoEntity.toMap() = hashMapOf(
        "funcionario" to funcionario,
        "empresa" to empresa,
        "periodo" to periodo,
        "jornada" to jornada,
        "jornadaRealizada" to jornadaRealizada,
        "resumoItensJson" to resumoItensJson,
        "saldoFinalBH" to saldoFinalBH,
        "saldoPeriodoBH" to saldoPeriodoBH,
        "detalhesSaldoBH" to detalhesSaldoBH,
        "hasAbsences" to hasAbsences,
        "diasFaltasJson" to diasFaltasJson,
        "timestamp" to timestamp,
        "pdfFilePath" to (pdfFilePath ?: "")
    )

    private fun ReciboEntity.toMap() = hashMapOf(
        "funcionario" to funcionario,
        "matricula" to matricula,
        "periodo" to periodo,
        "dataPagamento" to dataPagamento,
        "empresa" to empresa,
        "proventosJson" to proventosJson,
        "descontosJson" to descontosJson,
        "totalProventos" to totalProventos,
        "totalDescontos" to totalDescontos,
        "valorLiquido" to valorLiquido,
        "baseInss" to baseInss,
        "fgtsMes" to fgtsMes,
        "baseIrpf" to baseIrpf,
        "timestamp" to timestamp,
        "pdfFilePath" to (pdfFilePath ?: "")
    )

    private fun mapToEspelho(map: Map<String, Any>) = EspelhoEntity(
        funcionario = map["funcionario"] as? String ?: "",
        empresa = map["empresa"] as? String ?: "",
        periodo = map["periodo"] as? String ?: "",
        jornada = map["jornada"] as? String ?: "",
        jornadaRealizada = map["jornadaRealizada"] as? String ?: "",
        resumoItensJson = map["resumoItensJson"] as? String ?: "[]",
        saldoFinalBH = map["saldoFinalBH"] as? String ?: "0:00",
        saldoPeriodoBH = map["saldoPeriodoBH"] as? String ?: "0:00",
        detalhesSaldoBH = map["detalhesSaldoBH"] as? String ?: "",
        hasAbsences = map["hasAbsences"] as? Boolean ?: false,
        diasFaltasJson = map["diasFaltasJson"] as? String ?: "[]",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        pdfFilePath = map["pdfFilePath"] as? String
    )

    private fun mapToRecibo(map: Map<String, Any>) = ReciboEntity(
        funcionario = map["funcionario"] as? String ?: "",
        matricula = map["matricula"] as? String ?: "",
        periodo = map["periodo"] as? String ?: "",
        dataPagamento = map["dataPagamento"] as? String ?: "",
        empresa = map["empresa"] as? String ?: "",
        proventosJson = map["proventosJson"] as? String ?: "[]",
        descontosJson = map["descontosJson"] as? String ?: "[]",
        totalProventos = map["totalProventos"] as? String ?: "0,00",
        totalDescontos = map["totalDescontos"] as? String ?: "0,00",
        valorLiquido = map["valorLiquido"] as? String ?: "0,00",
        baseInss = map["baseInss"] as? String ?: "0,00",
        fgtsMes = map["fgtsMes"] as? String ?: "0,00",
        baseIrpf = map["baseIrpf"] as? String ?: "0,00",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        pdfFilePath = map["pdfFilePath"] as? String
    )
}
