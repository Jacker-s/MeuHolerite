package com.jack.meuholerite.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.EspelhoEntity
import com.jack.meuholerite.database.FinanceDebtEntity
import com.jack.meuholerite.database.FinanceExpenseEntity
import com.jack.meuholerite.database.FinanceGoalEntity
import com.jack.meuholerite.database.ReciboEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

data class SalaryRanking(
    val cargo: String, 
    val empresa: String, 
    val media: Double, 
    val count: Int,
    val minSalary: Double = 0.0,
    val maxSalary: Double = 0.0,
    val maxReportedSalary: Double = 0.0,
    val medianaSalary: Double = 0.0,
    val mediaLiquida: Double = 0.0,
    val percNoturno: Double = 0.0,
    val percInsalubridade: Double = 0.0,
    val percHoraExtra: Double = 0.0,
    val mediaINSS: Double = 0.0,
    val mediaIRRF: Double = 0.0,
    val inssSamples: Int = 0,
    val irrfSamples: Int = 0,
    val empresasCount: Int = 0,
    val empresasRelacionadas: List<String> = emptyList()
)

data class RawSalaryStat(
    val cargo: String,
    val empresa: String,
    val salary: Double,
    val liquido: Double,
    val hasNoturno: Boolean,
    val hasInsalubridade: Boolean,
    val hasHoraExtra: Boolean,
    val inss: Double = 0.0,
    val irrf: Double = 0.0
)

class BackupManager(private val context: Context) {
    companion object {
        private const val SALARY_RANKING_LIMIT = 100
        private const val SALARY_QUERY_SAMPLE_SIZE = 500
    }

    private val db by lazy { AppDatabase.getDatabase(context) }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    private var lastBackupTime = 0L

    suspend fun backupData(onProgress: (Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastBackupTime < 10_000) return@withContext Result.success(Unit)
        lastBackupTime = now

        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            onProgress(5)
            val espelhos = db.espelhoDao().getAll()
            val recibos = db.reciboDao().getAll()
            val expenses = db.financeExpenseDao().getAll()
            val goals = db.financeGoalDao().getAll()
            val debts = db.financeDebtDao().getAll()
            onProgress(15)

            val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)
            val epaysPrefs = context.getSharedPreferences("epays_cookies", Context.MODE_PRIVATE)

            val userData = hashMapOf(
                "user_name" to EncryptionHelper.encrypt(userPrefs.getString("user_name", "") ?: "", userId),
                "user_matricula" to EncryptionHelper.encrypt(userPrefs.getString("user_matricula", "") ?: "", userId)
            )

            val settingsData = hashMapOf(
                "dark_mode" to settingsPrefs.getBoolean("dark_mode", false),
                "hide_values_enabled" to settingsPrefs.getBoolean("hide_values_enabled", false),
                "app_lock_enabled" to settingsPrefs.getBoolean("app_lock_enabled", false),
                "app_lock_pin" to EncryptionHelper.encrypt(settingsPrefs.getString("app_lock_pin", "") ?: "", userId),
                "has_dark_mode_set" to settingsPrefs.contains("dark_mode")
            )

            val epaysData = hashMapOf(
                "cookie_header" to EncryptionHelper.encrypt(epaysPrefs.getString("cookie_header", "") ?: "", userId),
                "epays_login" to EncryptionHelper.encrypt(epaysPrefs.getString("epays_login", "") ?: "", userId),
                "epays_password" to EncryptionHelper.encrypt(epaysPrefs.getString("epays_password", "") ?: "", userId)
            )
            onProgress(25)

            val backupMap = hashMapOf(
                "userData" to userData,
                "settings" to settingsData,
                "epaysData" to epaysData,
                "lastBackup" to System.currentTimeMillis(),
                "espelhos" to espelhos.map { it.toMap(userId) },
                "recibos" to recibos.map { it.toMap(userId) },
                "financeExpenses" to expenses.map { it.toMap(userId) },
                "financeGoals" to goals.map { it.toMap(userId) },
                "financeDebts" to debts.map { it.toMap(userId) },
                "isEncrypted" to true
            )

            firestore.collection("backups").document(userId).set(backupMap, SetOptions.merge()).await()
            onProgress(100)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro backup Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun restoreData(onProgress: (Int) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Não autenticado"))
        val userId = user.uid

        try {
            onProgress(5)
            val document = firestore.collection("backups").document(userId).get().await()
            if (!document.exists()) return@withContext Result.failure(Exception("Sem backup encontrado."))
            onProgress(20)

            val isEncrypted = document.getBoolean("isEncrypted") ?: false

            // Restaurar Prefs
            (document.get("userData") as? Map<*, *>)?.let { userData ->
                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().apply {
                    val name = userData["user_name"] as? String ?: ""
                    val matricula = userData["user_matricula"] as? String ?: ""
                    putString("user_name", if (isEncrypted) EncryptionHelper.decrypt(name, userId) else name)
                    putString("user_matricula", if (isEncrypted) EncryptionHelper.decrypt(matricula, userId) else matricula)
                    apply()
                }
            }
            (document.get("epaysData") as? Map<*, *>)?.let { epaysData ->
                context.getSharedPreferences("epays_cookies", Context.MODE_PRIVATE).edit().apply {
                    val cookie = epaysData["cookie_header"] as? String ?: ""
                    val login = epaysData["epays_login"] as? String ?: ""
                    val pass = epaysData["epays_password"] as? String ?: ""
                    
                    putString("cookie_header", if (isEncrypted) EncryptionHelper.decrypt(cookie, userId) else cookie)
                    putString("epays_login", if (isEncrypted) EncryptionHelper.decrypt(login, userId) else login)
                    putString("epays_password", if (isEncrypted) EncryptionHelper.decrypt(pass, userId) else pass)
                    apply()
                }
            }
            onProgress(45)

            (document.get("settings") as? Map<*, *>)?.let { settings ->
                context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE).edit().apply {
                    putBoolean("dark_mode", settings["dark_mode"] as? Boolean ?: false)
                    putBoolean("hide_values_enabled", settings["hide_values_enabled"] as? Boolean ?: false)
                    putBoolean("app_lock_enabled", settings["app_lock_enabled"] as? Boolean ?: false)
                    val pin = settings["app_lock_pin"] as? String ?: ""
                    putString("app_lock_pin", if (isEncrypted) EncryptionHelper.decrypt(pin, userId) else pin)
                    apply()
                }
            }
            onProgress(50)

            // Limpar Tabelas Atuais antes de restaurar (Evita Duplicatas)
            db.espelhoDao().deleteAll()
            db.reciboDao().deleteAll()
            db.financeExpenseDao().deleteAll()
            db.financeGoalDao().deleteAll()
            db.financeDebtDao().deleteAll()

            // Restaurar Tabelas
            (document.get("espelhos") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.espelhoDao().insert(mapToEspelho(map, isEncrypted, userId))
                }
            }
            onProgress(70)

            (document.get("recibos") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.reciboDao().insert(mapToRecibo(map, isEncrypted, userId))
                }
            }
            onProgress(85)

            (document.get("financeExpenses") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.financeExpenseDao().insert(mapToFinanceExpense(map, isEncrypted, userId))
                }
            }

            (document.get("financeGoals") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.financeGoalDao().insert(mapToFinanceGoal(map, isEncrypted, userId))
                }
            }

            (document.get("financeDebts") as? List<*>)?.let { list ->
                list.filterIsInstance<Map<String, Any>>().forEach { map ->
                    db.financeDebtDao().insert(mapToFinanceDebt(map, isEncrypted, userId))
                }
            }

            onProgress(100)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro rest. Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun checkAndRestoreIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext
            val recibos = db.reciboDao().getAll()
            val espelhos = db.espelhoDao().getAll()
            val expenses = db.financeExpenseDao().getAll()
            val goals = db.financeGoalDao().getAll()
            val debts = db.financeDebtDao().getAll()
            
            if (recibos.isEmpty() && espelhos.isEmpty() && expenses.isEmpty() && goals.isEmpty() && debts.isEmpty()) {
                Log.d("BackupManager", "Banco local vazio, tentando restauração automática...")
                restoreData()
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro na restauração automática", e)
        }
    }

    suspend fun deleteBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Não autenticado"))
        try {
            firestore.collection("backups").document(user.uid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ======================
    // 📊 SALARY STATISTICS (ANONYMOUS)
    // ======================
    suspend fun saveAnonymousSalaryStat(recibo: com.jack.meuholerite.model.ReciboPagamento) = withContext(Dispatchers.IO) {
        val totalProventos = parseMoney(recibo.totalProventos)
        val totalDescontos = parseMoney(recibo.totalDescontos)
        val baseSalary = sanitizeBaseSalary(parseMoney(recibo.salarioBase), totalProventos)
        val cargo = normalizeCargo(recibo.cargo)
        val empresa = recibo.empresa
        val referenceTimestamp = extractSalaryReferenceTimestamp(recibo)

        if (!isValidCargo(cargo) || baseSalary < 500.0) return@withContext
        var safeEmpresa = normalizeCompany(empresa)
        if (safeEmpresa.contains("RECIBO DE PAGAMENTO") || safeEmpresa.contains("DEMONSTRATIVO") || safeEmpresa.contains("HOLERITE")) {
            safeEmpresa = "NÃO INFORMADA"
        }
        
        val liquido = sanitizeNetSalary(parseMoney(recibo.valorLiquido), baseSalary, totalProventos, totalDescontos)
        val inss = extractDiscountValue(
            items = recibo.descontos,
            textCandidates = emptyList(),
            descriptionKeywords = listOf("INSS", "PREVID")
        )
        val irrf = extractDiscountValue(
            items = recibo.descontos,
            textCandidates = emptyList(),
            descriptionKeywords = listOf("IRRF", "IMPOSTO DE RENDA", "IRPF")
        )
        
        val hasNoturno = recibo.proventos.any { it.descricao.contains("NOTURNO", true) }
        val hasInsalubridade = recibo.proventos.any { it.descricao.contains("INSALUBRIDADE", true) || it.descricao.contains("PERICULOSIDADE", true) }
        val hasHoraExtra = recibo.proventos.any { it.descricao.contains("HORA EXTRA", true) || it.descricao.contains("EXTRAS", true) }

        if (isSuspiciousSalaryStat(baseSalary, liquido, inss, irrf)) return@withContext

        // Apenas recibos mensais para estatísticas de salário real e evitar duplicidade de adiantamentos
        if (recibo.tipo != com.jack.meuholerite.model.ReciboTipo.MENSAL) return@withContext

        try {
            val userId = auth.currentUser?.uid ?: return@withContext
            val statId = generateStatId(userId, recibo)
            val docRef = firestore.collection("salary_stats").document(statId)
            val existingDoc = docRef.get().await()
            val existingReferenceTimestamp = (existingDoc.get("referenceTimestamp") as? Number)?.toLong()
                ?: Long.MIN_VALUE
            val existingReferenceReliable = existingDoc.getBoolean("referenceTimestampReliable") ?: false
            val newReferenceReliable = referenceTimestamp != null

            if (existingDoc.exists()) {
                when {
                    existingReferenceReliable && !newReferenceReliable -> return@withContext
                    existingReferenceReliable && newReferenceReliable && existingReferenceTimestamp > referenceTimestamp!! -> return@withContext
                    !existingReferenceReliable && !newReferenceReliable -> return@withContext
                }
            }
            
            val stat = hashMapOf(
                "cargo" to cargo,
                "empresa" to safeEmpresa,
                "salary" to baseSalary,
                "liquido" to liquido,
                "inss" to inss,
                "irrf" to irrf,
                "hasNoturno" to hasNoturno,
                "hasInsalubridade" to hasInsalubridade,
                "hasHoraExtra" to hasHoraExtra,
                "timestamp" to System.currentTimeMillis(),
                "referenceTimestamp" to (referenceTimestamp ?: 0L),
                "referenceTimestampReliable" to newReferenceReliable,
                "uHash" to statId,
                "v" to 2
            )
            
            docRef.set(stat).await()
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao salvar estatística salarial", e)
        }
    }

    private fun generateStatId(userId: String, recibo: com.jack.meuholerite.model.ReciboPagamento): String {
        // ID único baseado no usuário, empresa e cargo para manter sempre o mais recente
        val rawId = "${userId}_${normalizeCompany(recibo.empresa)}_${normalizeCargo(recibo.cargo)}"
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(rawId.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            rawId.filter { it.isLetterOrDigit() }.take(32)
        }
    }

    suspend fun getTopSalaries(): Result<List<SalaryRanking>> = withContext(Dispatchers.IO) {
        try {
            val documents = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var pageCount = 0

            while (pageCount < 5) {
                pageCount++
                var query = firestore.collection("salary_stats")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(SALARY_QUERY_SAMPLE_SIZE.toLong())
                if (lastDoc != null) query = query.startAfter(lastDoc)

                val page = query.get().await()
                if (page.isEmpty) break
                documents.addAll(page.documents)
                lastDoc = page.documents.last()
            }

            val rawStats = documents.mapNotNull { doc ->
                val cargo = doc.getString("cargo")?.let(::normalizeCargo)
                val empresa = normalizeCompany(doc.getString("empresa") ?: "")
                val salary = (doc.get("salary") as? Number)?.toDouble()
                val liquido = (doc.get("liquido") as? Number)?.toDouble() ?: salary ?: 0.0
                val hasNoturno = doc.getBoolean("hasNoturno") ?: false
                val hasInsalubridade = doc.getBoolean("hasInsalubridade") ?: false
                val hasHoraExtra = doc.getBoolean("hasHoraExtra") ?: false
                val inssVal = (doc.get("inss") as? Number)?.toDouble() ?: 0.0
                val irrfVal = (doc.get("irrf") as? Number)?.toDouble() ?: 0.0

                if (cargo != null && salary != null && isValidCompany(empresa) && isValidCargo(cargo)) {
                    RawSalaryStat(cargo, empresa, salary, liquido, hasNoturno, hasInsalubridade, hasHoraExtra, inssVal, irrfVal)
                } else null
            }

            // Agrupa por cargo já normalizado para consolidar pequenas variações de OCR e abreviações.
            val rankingList = rawStats.groupBy { it.cargo }.mapNotNull { (cargo, list) ->
                val filteredList = filterSalaryOutliers(list)
                val best = filteredList.maxByOrNull { it.salary } ?: return@mapNotNull null
                val sortedSalaries = filteredList.map { it.salary }.sorted()
                val uniqueCompanies = mergeCompanyAliases(
                    filteredList.map { it.empresa }.filter { it != "NÃO INFORMADA" }
                )
                val empresa = uniqueCompanies.firstOrNull()?.first ?: "NÃO INFORMADA"

                val percNoturno = filteredList.count { it.hasNoturno }.toDouble() / filteredList.size
                val percIns = filteredList.count { it.hasInsalubridade }.toDouble() / filteredList.size
                val percHe = filteredList.count { it.hasHoraExtra }.toDouble() / filteredList.size
                val mediaSalary = filteredList.map { it.salary }.average()
                val medianSalary = median(sortedSalaries)
                val minSalary = sortedSalaries.firstOrNull() ?: 0.0
                val maxSalary = sortedSalaries.lastOrNull() ?: 0.0

                SalaryRanking(
                    cargo = cargo,
                    empresa = empresa,
                    media = mediaSalary,
                    count = filteredList.size,
                    minSalary = minSalary,
                    maxSalary = maxSalary,
                    maxReportedSalary = best.salary,
                    medianaSalary = medianSalary,
                    mediaLiquida = filteredList.map { it.liquido }.average(),
                    percNoturno = percNoturno,
                    percInsalubridade = percIns,
                    percHoraExtra = percHe,
                    mediaINSS = filteredList.mapNotNull { it.inss.takeIf { value -> value > 0.0 } }.averageOrZero(),
                    mediaIRRF = filteredList.mapNotNull { it.irrf.takeIf { value -> value > 0.0 } }.averageOrZero(),
                    inssSamples = filteredList.count { it.inss > 0.0 },
                    irrfSamples = filteredList.count { it.irrf > 0.0 },
                    empresasCount = uniqueCompanies.size,
                    empresasRelacionadas = uniqueCompanies.take(3).map { it.first }
                )
            }
                .distinctBy { normalizeCargo(it.cargo) }
                .sortedWith(
                    compareByDescending<SalaryRanking> { it.maxReportedSalary }
                        .thenByDescending { it.media }
                        .thenByDescending { it.count }
                )
                .take(SALARY_RANKING_LIMIT)

            Result.success(rankingList)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao buscar ranking salarial", e)
            Result.failure(e)
        }
    }

    suspend fun performGlobalCleanup() = withContext(Dispatchers.IO) {
        try {
            val allDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var pageCount = 0

            while (pageCount < 5) {
                pageCount++
                var query = firestore.collection("salary_stats")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(500)
                if (lastDoc != null) query = query.startAfter(lastDoc)

                val page = query.get().await()
                if (page.isEmpty) break
                allDocs.addAll(page.documents)
                lastDoc = page.documents.last()
            }

            val keepByHash = mutableMapOf<String, MutableList<com.google.firebase.firestore.DocumentSnapshot>>()
            val toDelete = mutableSetOf<String>()

            allDocs.forEach { doc ->
                val cargo = normalizeCargo(doc.getString("cargo").orEmpty())
                val empresa = normalizeCompany(doc.getString("empresa").orEmpty())
                val salary = (doc.get("salary") as? Number)?.toDouble() ?: 0.0
                val uHash = doc.getString("uHash").orEmpty()

                if (!isValidCargo(cargo) || salary < 500.0 || !isValidCompany(empresa)) {
                    toDelete.add(doc.id)
                    return@forEach
                }

                if (uHash.isNotBlank()) {
                    val currentList = keepByHash.getOrPut(uHash) { mutableListOf() }
                    currentList.add(doc)
                }
            }

            keepByHash.values.forEach { duplicateDocs ->
                val sorted = duplicateDocs.sortedWith(
                    compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                        it.getBoolean("referenceTimestampReliable") ?: false
                    }.thenByDescending {
                        (it.get("referenceTimestamp") as? Number)?.toLong() ?: Long.MIN_VALUE
                    }.thenByDescending {
                        (it.get("timestamp") as? Number)?.toLong() ?: Long.MIN_VALUE
                    }
                )
                sorted.drop(1).forEach { extraDoc -> toDelete.add(extraDoc.id) }
            }

            var totalDeleted = 0
            toDelete.chunked(400).forEach { ids ->
                val batch = firestore.batch()
                ids.forEach { id ->
                    batch.delete(firestore.collection("salary_stats").document(id))
                }
                batch.commit().await()
                totalDeleted += ids.size
            }

            if (totalDeleted > 0) {
                Log.d("BackupManager", "Cleanup concluído: $totalDeleted registros removidos")
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro no cleanup", e)
        }
    }

    private fun isValidCompany(empresaRaw: String): Boolean {
        val empresa = normalizeCompany(empresaRaw)
        if (empresa.isBlank() || empresa == "NÃO INFORMADA") return false
        if (empresa.contains("RECIBO DE PAGAMENTO")) return false
        if (empresa.contains("DEMONSTRATIVO")) return false
        if (empresa.contains("HOLERITE")) return false
        return true
    }

    private fun isValidCargo(cargoRaw: String): Boolean {
        val cargo = normalizeCargo(cargoRaw)
        if (cargo.length < 3) return false
        val invalidTerms = listOf(
            "AFASTADO",
            "ATESTADO",
            "FERIAS",
            "LICENCA",
            "RESCISAO",
            "DEMITIDO",
            "DESLIGADO",
            "INSS"
        )
        return invalidTerms.none { cargo.contains(it) }
    }

    private fun isSuspiciousSalaryStat(
        baseSalary: Double,
        liquido: Double,
        inss: Double,
        irrf: Double
    ): Boolean {
        if (baseSalary <= 0.0) return true
        if (baseSalary >= 10000.0 && inss == 0.0 && irrf == 0.0) return true
        if (baseSalary >= 8000.0 && liquido >= (baseSalary * 1.2)) return true
        if (baseSalary >= 15000.0) return true
        return false
    }

    private fun parseMoney(value: String): Double {
        return value.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    private fun extractSalaryReferenceTimestamp(recibo: com.jack.meuholerite.model.ReciboPagamento): Long? {
        parseDateTimestamp(recibo.dataPagamento)?.let { return it }

        val periodoMatch = Regex("(\\d{2})\\s*[/.-]\\s*(\\d{4})").find(recibo.periodo)
        if (periodoMatch != null) {
            val month = periodoMatch.groupValues[1].toIntOrNull()
            val year = periodoMatch.groupValues[2].toIntOrNull()
            if (month != null && year != null && month in 1..12) {
                return Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        return null
    }

    private fun parseDateTimestamp(value: String): Long? {
        if (value.isBlank()) return null
        val parser = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        parser.isLenient = false
        return runCatching { parser.parse(value)?.time }.getOrNull()
    }

    private fun sanitizeBaseSalary(baseSalary: Double, totalProventos: Double): Double {
        if (baseSalary <= 0.0) return 0.0
        if (totalProventos <= 0.0) return baseSalary

        val candidates = listOf(baseSalary, baseSalary / 10.0, baseSalary / 100.0)
        return candidates.firstOrNull { candidate ->
            candidate > 0.0 && candidate <= totalProventos * 1.35
        } ?: baseSalary
    }

    private fun sanitizeNetSalary(
        liquido: Double,
        baseSalary: Double,
        totalProventos: Double,
        totalDescontos: Double
    ): Double {
        val calculatedNet = (totalProventos - totalDescontos).takeIf { it > 0.0 } ?: liquido
        if (liquido <= 0.0) return calculatedNet
        if (baseSalary > 0.0 && liquido >= baseSalary * 2) return calculatedNet
        return liquido
    }

    private fun extractDiscountValue(
        items: List<com.jack.meuholerite.model.ReciboItem>,
        textCandidates: List<String>,
        descriptionKeywords: List<String>
    ): Double {
        val fromItems = items.firstOrNull { item ->
            descriptionKeywords.any { keyword -> item.descricao.contains(keyword, ignoreCase = true) }
        }?.valor?.let(::parseMoney) ?: 0.0
        if (fromItems > 0.0) return fromItems

        return textCandidates
            .map(::parseMoney)
            .firstOrNull { it > 0.0 }
            ?: 0.0
    }

    private fun normalizeCargo(raw: String): String {
        return raw
            .normalizeForComparison()
            .replace(Regex("^\\d+\\s*[-/]?\\s*"), "")
            .replace(Regex("\\(\\s*\\)"), " ")
            .replace(Regex("\\bAUX\\b"), "AUXILIAR")
            .replace(Regex("\\bAJ\\b"), "AJUDANTE")
            .replace(Regex("\\bTEC\\b"), "TECNICO")
            .replace(Regex("\\bTECNICO\\s+A\\b"), "TECNICO")
            .replace(Regex("\\bTECNICO\\(A\\)\\b"), "TECNICO")
            .replace(Regex("\\bDE\\s+RESGATISTA\\b"), "RESGATISTA")
            .replace(Regex("\\bAUXILIAR\\s+DE\\s+SERVICOS\\s+GERAIS\\b"), "AUXILIAR SERVICOS GERAIS")
            .replace(Regex("\\bAUXILIAR\\s+SERVICOS\\s+GERAIS\\b"), "AUXILIAR SERVICOS GERAIS")
            .replace(Regex("\\bAUXILIAR\\s+SERVICOS\\s+GER\\b"), "AUXILIAR SERVICOS GERAIS")
            .replace(Regex("\\bAUXILIAR\\s+SERVICOS\\s+GERIAIS\\b"), "AUXILIAR SERVICOS GERAIS")
            .replace(Regex("\\bAUXILIAR\\s+DE\\s+PRODUCAO\\b"), "AUXILIAR PRODUCAO")
            .replace(Regex("\\bAUXILIAR\\s+PRODUCAO\\b"), "AUXILIAR PRODUCAO")
            .replace(Regex("\\bAUXILIAR\\s+OPERACIONAL\\s+[IVX]+\\b"), "AUXILIAR OPERACIONAL")
            .replace(Regex("\\bOPERADORA\\b"), "OPERADOR")
            .replace(Regex("\\bCHEFE\\b"), "CHEF")
            .replace(Regex("\\bPATISSERIE\\s+JUNIOR\\b"), "PATISSERIE JR")
            .replace(" DE DE ", " DE ")
            .replace(Regex("\\b[IVX]{1,4}\\b"), " ")
            .replace(Regex("\\b[RS]\\b$"), "")
            .replace(Regex("\\bSERVICO\\b"), "SERVICOS")
            .replace(Regex("\\bSERVICOS\\s+GERAI[S5]\\b"), "SERVICOS GERAIS")
            .replace(Regex("\\b[A-Z]\\b$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun filterSalaryOutliers(stats: List<RawSalaryStat>): List<RawSalaryStat> {
        if (stats.size < 4) return stats.filterNot { isSuspiciousSalaryStat(it.salary, it.liquido, it.inss, it.irrf) }

        val sortedSalaries = stats.map { it.salary }.sorted()
        val groupMedian = median(sortedSalaries)
        val deviations = stats.map { kotlin.math.abs(it.salary - groupMedian) }.sorted()
        val mad = median(deviations)

        val filtered = stats.filter { stat ->
            val suspiciousRecord = isSuspiciousSalaryStat(stat.salary, stat.liquido, stat.inss, stat.irrf)
            val tooFarFromMedian = when {
                mad > 0.0 -> (0.6745 * kotlin.math.abs(stat.salary - groupMedian) / mad) <= 3.5
                else -> stat.salary <= (groupMedian * 2.5)
            }
            !suspiciousRecord && tooFarFromMedian
        }

        return filtered.ifEmpty { stats.filterNot { isSuspiciousSalaryStat(it.salary, it.liquido, it.inss, it.irrf) } }
            .ifEmpty { stats }
    }

    private fun normalizeCompany(raw: String): String {
        val normalized = raw
            .normalizeForComparison()
            .replace(Regex("^\\d+\\s*[-/]?\\s*"), "")
            .replace(Regex("\\bSEM TOMADOR\\b"), " ")
            .replace(Regex("\\bTOMADOR\\b.*$"), " ")
            .replace(Regex("\\bCPF\\b.*$"), " ")
            .replace(Regex("\\bCNPJ\\b.*$"), " ")
            .replace(Regex("\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b"), " ")
            .replace(Regex("\\b\\d+\\b"), " ")
            .replace(Regex("\\b(S A|SA|LTDA|LIMITADA|EIRELI|EPP|ME|SLU|MATRIZ|FILIAL)\\b"), " ")
            .replace(Regex("\\b(FILIIAL|FILIAL|UNIDADE)\\b"), " ")
            .replace(Regex("\\b(GERA3I5S|L19T|T35D5A|A61O4|L7T6D0A|E0P1|P03)\\b"), " ")
            .replace(Regex("(\\b[A-Z]\\b\\s+){2,}$"), " ")
            .replace(Regex("\\bSEM\\b"), " ")
            .replace(Regex("\\b([A-Z]{2,}) \\1\\b"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.contains("PARAGRAFO TERCEIRO") || normalized.contains("HIPOTESE DE RESCISAO")) {
            return "NÃO INFORMADA"
        }
        return normalized.ifBlank { "NÃO INFORMADA" }
    }

    private fun String.normalizeForComparison(): String {
        val noAccents = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noAccents
            .uppercase()
            .replace("&", " E ")
            .replace(Regex("([A-Z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[^A-Z0-9 ]"), " ")
    }

    private fun mergeCompanyAliases(companies: List<String>): List<Pair<String, Int>> {
        val merged = linkedMapOf<String, Int>()

        companies
            .groupingBy { normalizeCompany(it) }
            .eachCount()
            .toList()
            .sortedWith(compareBy<Pair<String, Int>> { it.first.length }.thenBy { it.first })
            .forEach { (company, count) ->
                val alias = merged.keys.firstOrNull { shouldMergeCompanies(it, company) || shouldMergeCompanies(company, it) }
                if (alias != null) {
                    merged[alias] = (merged[alias] ?: 0) + count
                } else {
                    merged[company] = count
                }
            }

        return merged.toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }

    private fun shouldMergeCompanies(base: String, candidate: String): Boolean {
        if (base == candidate) return true
        val baseTokens = base.split(" ").filter { it.isNotBlank() }
        val candidateTokens = candidate.split(" ").filter { it.isNotBlank() }
        if (baseTokens.isEmpty() || candidateTokens.isEmpty()) return false

        if (candidate.startsWith("$base ") && candidateTokens.size - baseTokens.size <= 2) return true
        if (base.startsWith("$candidate ") && baseTokens.size - candidateTokens.size <= 2) return true
        if (baseTokens.size != candidateTokens.size) return false

        var mismatches = 0
        for (i in baseTokens.indices) {
            if (baseTokens[i] == candidateTokens[i]) continue
            val distance = levenshtein(baseTokens[i], candidateTokens[i])
            if (distance <= 1 && minOf(baseTokens[i].length, candidateTokens[i].length) >= 5) {
                mismatches++
            } else {
                return false
            }
        }
        return mismatches in 1..2
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val current = costs[j]
                val substitution = if (a[i - 1] == b[j - 1]) previous else previous + 1
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    substitution
                )
                previous = current
            }
        }
        return costs[b.length]
    }

    private fun median(sortedValues: List<Double>): Double {
        if (sortedValues.isEmpty()) return 0.0
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 0) {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2.0
        } else {
            sortedValues[middle]
        }
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    suspend fun getBackupSize(): Result<Long> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Não autenticado"))
        try {
            var size = 0L
            val doc = firestore.collection("backups").document(user.uid).get().await()
            if (doc.exists()) size += doc.data.toString().toByteArray().size.toLong()
            Result.success(size)
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun EspelhoEntity.toMap(userId: String) = hashMapOf(
        "funcionario" to EncryptionHelper.encrypt(funcionario, userId),
        "empresa" to EncryptionHelper.encrypt(empresa, userId),
        "periodo" to periodo, "jornada" to jornada, "jornadaRealizada" to jornadaRealizada,
        "resumoItensJson" to EncryptionHelper.encrypt(resumoItensJson, userId),
        "saldoFinalBH" to saldoFinalBH, "saldoPeriodoBH" to saldoPeriodoBH,
        "detalhesSaldoBH" to EncryptionHelper.encrypt(detalhesSaldoBH, userId),
        "hasAbsences" to hasAbsences, "diasFaltasJson" to EncryptionHelper.encrypt(diasFaltasJson, userId),
        "timestamp" to timestamp, "pdfFilePath" to (pdfFilePath?.let { File(it).name } ?: "")
    )

    private fun ReciboEntity.toMap(userId: String) = hashMapOf(
        "funcionario" to EncryptionHelper.encrypt(funcionario, userId),
        "matricula" to EncryptionHelper.encrypt(matricula, userId),
        "periodo" to periodo, "dataPagamento" to dataPagamento,
        "empresa" to EncryptionHelper.encrypt(empresa, userId),
        "proventosJson" to EncryptionHelper.encrypt(proventosJson, userId),
        "descontosJson" to EncryptionHelper.encrypt(descontosJson, userId),
        "totalProventos" to totalProventos, "totalDescontos" to totalDescontos,
        "valorLiquido" to valorLiquido, "baseInss" to baseInss, "fgtsMes" to fgtsMes,
        "baseIrpf" to baseIrpf, "timestamp" to timestamp, "pdfFilePath" to (pdfFilePath?.let { File(it).name } ?: "")
    )

    private fun FinanceExpenseEntity.toMap(userId: String) = hashMapOf(
        "description" to EncryptionHelper.encrypt(description, userId),
        "value" to value, "category" to category, "isFixed" to isFixed, "timestamp" to timestamp
    )

    private fun FinanceGoalEntity.toMap(userId: String) = hashMapOf(
        "title" to EncryptionHelper.encrypt(title, userId),
        "description" to EncryptionHelper.encrypt(description, userId),
        "targetAmount" to targetAmount, "currentAmount" to currentAmount, "colorHex" to colorHex
    )

    private fun FinanceDebtEntity.toMap(userId: String) = hashMapOf(
        "description" to EncryptionHelper.encrypt(description, userId),
        "totalAmount" to totalAmount, "remainingAmount" to remainingAmount,
        "totalInstallments" to totalInstallments, "paidInstallments" to paidInstallments,
        "monthlyValue" to monthlyValue, "interestRate" to interestRate, "dueDate" to dueDate,
        "timestamp" to timestamp
    )

    private fun mapToEspelho(map: Map<String, Any>, isEncrypted: Boolean, userId: String): EspelhoEntity {
        val fileName = map["pdfFilePath"] as? String
        var path: String? = null
        
        if (!fileName.isNullOrEmpty()) {
            val pdfDir = File(context.filesDir, "pdfs")
            val rootFile = File(pdfDir, fileName)
            if (rootFile.exists()) {
                path = rootFile.absolutePath
            } else {
                // Tenta encontrar em subpastas recursivamente
                val found = pdfDir.walkTopDown().find { it.name == fileName }
                path = found?.absolutePath ?: rootFile.absolutePath
            }
        }

        return EspelhoEntity(
            funcionario = decryptIfEncrypted(map["funcionario"] as? String ?: "", isEncrypted, userId),
            empresa = decryptIfEncrypted(map["empresa"] as? String ?: "", isEncrypted, userId),
            periodo = map["periodo"] as? String ?: "", jornada = map["jornada"] as? String ?: "",
            jornadaRealizada = map["jornadaRealizada"] as? String ?: "",
            resumoItensJson = decryptIfEncrypted(map["resumoItensJson"] as? String ?: "[]", isEncrypted, userId),
            saldoFinalBH = map["saldoFinalBH"] as? String ?: "0:00", saldoPeriodoBH = map["saldoPeriodoBH"] as? String ?: "0:00",
            detalhesSaldoBH = decryptIfEncrypted(map["detalhesSaldoBH"] as? String ?: "", isEncrypted, userId),
            hasAbsences = map["hasAbsences"] as? Boolean ?: false,
            diasFaltasJson = decryptIfEncrypted(map["diasFaltasJson"] as? String ?: "[]", isEncrypted, userId),
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(), pdfFilePath = path
        )
    }

    private fun mapToRecibo(map: Map<String, Any>, isEncrypted: Boolean, userId: String): ReciboEntity {
        val fileName = map["pdfFilePath"] as? String
        var path: String? = null
        
        if (!fileName.isNullOrEmpty()) {
            val pdfDir = File(context.filesDir, "pdfs")
            val rootFile = File(pdfDir, fileName)
            if (rootFile.exists()) {
                path = rootFile.absolutePath
            } else {
                // Tenta encontrar em subpastas recursivamente
                val found = pdfDir.walkTopDown().find { it.name == fileName }
                path = found?.absolutePath ?: rootFile.absolutePath
            }
        }

        return ReciboEntity(
            funcionario = decryptIfEncrypted(map["funcionario"] as? String ?: "", isEncrypted, userId),
            matricula = decryptIfEncrypted(map["matricula"] as? String ?: "", isEncrypted, userId),
            periodo = map["periodo"] as? String ?: "", dataPagamento = map["dataPagamento"] as? String ?: "",
            empresa = decryptIfEncrypted(map["empresa"] as? String ?: "", isEncrypted, userId),
            proventosJson = decryptIfEncrypted(map["proventosJson"] as? String ?: "[]", isEncrypted, userId),
            descontosJson = decryptIfEncrypted(map["descontosJson"] as? String ?: "[]", isEncrypted, userId),
            totalProventos = map["totalProventos"] as? String ?: "0,00", totalDescontos = map["totalDescontos"] as? String ?: "0,00",
            valorLiquido = map["valorLiquido"] as? String ?: "0,00", baseInss = map["baseInss"] as? String ?: "0,00",
            fgtsMes = map["fgtsMes"] as? String ?: "0,00", baseIrpf = map["baseIrpf"] as? String ?: "0,00",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(), pdfFilePath = path
        )
    }

    private fun mapToFinanceExpense(map: Map<String, Any>, isEncrypted: Boolean, userId: String) = FinanceExpenseEntity(
        description = decryptIfEncrypted(map["description"] as? String ?: "", isEncrypted, userId),
        value = (map["value"] as? Number)?.toDouble() ?: 0.0, category = map["category"] as? String ?: "OTHERS",
        isFixed = map["isFixed"] as? Boolean ?: false, timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    private fun mapToFinanceGoal(map: Map<String, Any>, isEncrypted: Boolean, userId: String) = FinanceGoalEntity(
        title = decryptIfEncrypted(map["title"] as? String ?: "", isEncrypted, userId),
        description = decryptIfEncrypted(map["description"] as? String ?: "", isEncrypted, userId),
        targetAmount = (map["targetAmount"] as? Number)?.toDouble() ?: 0.0,
        currentAmount = (map["currentAmount"] as? Number)?.toDouble() ?: 0.0,
        colorHex = map["colorHex"] as? String ?: "#10B981"
    )

    private fun mapToFinanceDebt(map: Map<String, Any>, isEncrypted: Boolean, userId: String) = FinanceDebtEntity(
        description = decryptIfEncrypted(map["description"] as? String ?: "", isEncrypted, userId),
        totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0,
        remainingAmount = (map["remainingAmount"] as? Number)?.toDouble() ?: 0.0,
        totalInstallments = (map["totalInstallments"] as? Number)?.toInt() ?: 0,
        paidInstallments = (map["paidInstallments"] as? Number)?.toInt() ?: 0,
        monthlyValue = (map["monthlyValue"] as? Number)?.toDouble() ?: 0.0,
        interestRate = (map["interestRate"] as? Number)?.toDouble() ?: 0.0,
        dueDate = map["dueDate"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    private fun decryptIfEncrypted(data: String, isEncrypted: Boolean, userId: String): String {
        return if (isEncrypted) EncryptionHelper.decrypt(data, userId) ?: data else data
    }
}
