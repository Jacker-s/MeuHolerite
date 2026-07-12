package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val SorteiosBg = Color(0xFFF7F4EC)
private val SorteiosInk = Color(0xFF1D2A3A)
private val SorteiosGold = Color(0xFFE2A63B)
private val SorteiosGreen = Color(0xFF1E8E5A)
private val SorteiosBlue = Color(0xFF1E5AA8)
private val SorteiosRose = Color(0xFFB94C63)
private const val ADMIN_EMAIL = "ssj53415170@gmail.com"

data class RaffleCampaign(
    val id: String = "",
    val title: String = "",
    val prizeTitle: String = "",
    val prizeValue: Double = 0.0,
    val regulation: String = "",
    val tasks: List<RaffleTask> = emptyList(),
    val isActive: Boolean = true,
    val isNumberBased: Boolean = false,
    val winningNumber: String = "",
    val winnerName: String = "",
    val winnerEmail: String = "",
    val winnerContactInfo: String = "",
    val winnerUserId: String = "",
    val winnerPhotoUrl: String = "",
    val winnerPrize: String = "",
    val winnerAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class RaffleTask(
    val title: String = "",
    val link: String = ""
)

data class RaffleEntry(
    val id: String = "",
    val raffleId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String = "",
    val contactInfo: String = "",
    val completedTasks: List<String> = emptyList(),
    val luckyNumbers: List<String> = emptyList(),
    val allTasksCompleted: Boolean = false,
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)

object SorteiosRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun currentUserEmail(): String = auth.currentUser?.email.orEmpty()

    fun isAdmin(): Boolean = currentUserEmail().equals(ADMIN_EMAIL, ignoreCase = true)

    fun observeActiveRaffle(onChange: (RaffleCampaign?) -> Unit): ListenerRegistration {
        return firestore.collection("raffles")
            .whereEqualTo("isActive", true)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                val raffle = snapshot?.documents?.firstOrNull()?.toRaffleCampaign()
                onChange(raffle)
            }
    }

    fun observeRaffleHistory(onChange: (List<RaffleCampaign>) -> Unit): ListenerRegistration {
        return firestore.collection("raffles")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                val history = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toRaffleCampaign() }
                    .filter { !it.isActive && it.winnerName.isNotBlank() }
                onChange(history)
            }
    }

    fun observeEntry(raffleId: String, userId: String, onChange: (RaffleEntry?) -> Unit): ListenerRegistration {
        return firestore.collection("raffle_entries")
            .document("${raffleId}_$userId")
            .addSnapshotListener { snapshot, _ ->
                onChange(snapshot?.toRaffleEntry())
            }
    }

    fun observeStats(raffleId: String, onChange: (Int, Int) -> Unit): ListenerRegistration {
        return firestore.collection("raffle_entries")
            .whereEqualTo("raffleId", raffleId)
            .addSnapshotListener { snapshot, _ ->
                val entries = snapshot?.documents.orEmpty().mapNotNull { it.toRaffleEntry() }
                onChange(entries.size, entries.count { it.allTasksCompleted })
            }
    }

    fun observeParticipants(raffleId: String, onChange: (List<RaffleEntry>) -> Unit): ListenerRegistration {
        return firestore.collection("raffle_entries")
            .whereEqualTo("raffleId", raffleId)
            .addSnapshotListener { snapshot, _ ->
                val entries = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toRaffleEntry() }
                    .sortedWith(
                        compareByDescending<RaffleEntry> { it.allTasksCompleted }
                            .thenBy { it.userName.lowercase() }
                    )
                onChange(entries)
            }
    }

    suspend fun saveRaffle(
        raffleId: String?,
        title: String,
        prizeTitle: String,
        prizeValue: Double,
        regulation: String,
        tasks: List<RaffleTask>,
        isNumberBased: Boolean
    ) {
        val cleanedTasks = tasks
            .map { it.copy(title = it.title.trim(), link = it.link.trim()) }
            .filter { it.title.isNotBlank() }
        require(title.isNotBlank()) { "Informe um título para o sorteio." }
        require(prizeTitle.isNotBlank()) { "Informe o prêmio." }
        if (!isNumberBased) {
            require(cleanedTasks.isNotEmpty()) { "Adicione pelo menos uma tarefa." }
        }

        if (raffleId.isNullOrBlank()) {
            val activeDocs = firestore.collection("raffles")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            activeDocs.documents.forEach { doc ->
                doc.reference.update("isActive", false, "updatedAt", System.currentTimeMillis()).await()
            }

            val newDoc = firestore.collection("raffles").document()
            val now = System.currentTimeMillis()
            newDoc.set(
                mapOf(
                    "title" to title.trim(),
                    "prizeTitle" to prizeTitle.trim(),
                    "prizeValue" to prizeValue,
                    "regulation" to regulation.trim(),
                    "tasks" to cleanedTasks.map { mapOf("title" to it.title, "link" to it.link) },
                    "isActive" to true,
                    "isNumberBased" to isNumberBased,
                    "winningNumber" to "",
                    "winnerName" to "",
                    "winnerEmail" to "",
                    "winnerContactInfo" to "",
                    "winnerUserId" to "",
                    "winnerPrize" to "",
                    "winnerAt" to 0L,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()
            return
        }

        firestore.collection("raffles").document(raffleId)
            .update(
                mapOf(
                    "title" to title.trim(),
                    "prizeTitle" to prizeTitle.trim(),
                    "prizeValue" to prizeValue,
                    "regulation" to regulation.trim(),
                    "tasks" to cleanedTasks.map { mapOf("title" to it.title, "link" to it.link) },
                    "isNumberBased" to isNumberBased,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
    }

    suspend fun submitEntry(raffle: RaffleCampaign, completedTasks: List<String>, contactInfo: String) {
        val user = auth.currentUser ?: error("Faça login para participar.")
        val appContext = FirebaseAuth.getInstance().app.applicationContext
        val sharedPrefs = appContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val savedName = sharedPrefs.getString("user_name", "")?.trim().orEmpty()
        val userName = savedName.ifBlank { user.displayName ?: "Usuário" }
        val cleanedTasks = completedTasks.map { it.trim() }.filter { it.isNotBlank() }
        require(raffle.tasks.isNotEmpty()) { "Esta campanha ainda não possui tarefas disponíveis." }
        val allCompleted = raffle.tasks.all { task -> cleanedTasks.contains(task.title) }
        require(allCompleted) { "Marque todas as tarefas como concluídas para salvar sua participação." }
        val now = System.currentTimeMillis()
        val docId = "${raffle.id}_${user.uid}"

        firestore.collection("raffle_entries").document(docId)
            .set(
                mapOf(
                    "raffleId" to raffle.id,
                    "userId" to user.uid,
                    "userName" to userName,
                    "userEmail" to (user.email ?: ""),
                    "userPhotoUrl" to (user.photoUrl?.toString() ?: ""),
                    "contactInfo" to contactInfo.trim(),
                    "completedTasks" to cleanedTasks,
                    "allTasksCompleted" to allCompleted,
                    "updatedAt" to now,
                    "createdAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun assignLuckyNumber(raffle: RaffleCampaign, contactInfo: String) {
        val user = auth.currentUser ?: error("Faça login para participar.")
        val appContext = FirebaseAuth.getInstance().app.applicationContext
        val sharedPrefs = appContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val savedName = sharedPrefs.getString("user_name", "")?.trim().orEmpty()
        val userName = savedName.ifBlank { user.displayName ?: "Usuário" }
        
        val docId = "${raffle.id}_${user.uid}"
        val now = System.currentTimeMillis()
        
        val newNumber = (100000..999999).random().toString()
        
        val docRef = firestore.collection("raffle_entries").document(docId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.exists()) {
                val currentNumbers = (snapshot.get("luckyNumbers") as? List<String>) ?: emptyList()
                val updatedNumbers = currentNumbers + newNumber
                transaction.update(
                    docRef,
                    "luckyNumbers", updatedNumbers,
                    "contactInfo", contactInfo.trim(),
                    "userPhotoUrl", (user.photoUrl?.toString() ?: ""),
                    "allTasksCompleted", true,
                    "updatedAt", now
                )
            } else {
                transaction.set(docRef, mapOf(
                    "raffleId" to raffle.id,
                    "userId" to user.uid,
                    "userName" to userName,
                    "userEmail" to (user.email ?: ""),
                    "userPhotoUrl" to (user.photoUrl?.toString() ?: ""),
                    "contactInfo" to contactInfo.trim(),
                    "completedTasks" to emptyList<String>(),
                    "luckyNumbers" to listOf(newNumber),
                    "allTasksCompleted" to true,
                    "updatedAt" to now,
                    "createdAt" to now
                ))
            }
        }.await()
    }

    suspend fun drawWinner(raffle: RaffleCampaign): Pair<RaffleEntry, String> {
        require(raffle.id.isNotBlank()) { "Nenhum sorteio ativo." }

        val eligible = firestore.collection("raffle_entries")
            .whereEqualTo("raffleId", raffle.id)
            .whereEqualTo("allTasksCompleted", true)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRaffleEntry() }

        require(eligible.isNotEmpty()) { "Nenhum usuário elegível." }

        val winner: RaffleEntry
        var winningNumber = ""

        if (raffle.isNumberBased) {
            val allNumbers = eligible.flatMap { entry -> entry.luckyNumbers.map { it to entry } }
            require(allNumbers.isNotEmpty()) { "Nenhum número da sorte gerado." }
            val chosen = allNumbers.random()
            winningNumber = chosen.first
            winner = chosen.second
        } else {
            winner = eligible.random()
        }

        val prizeLabel = buildPrizeLabel(raffle.prizeTitle, raffle.prizeValue)
        val now = System.currentTimeMillis()

        firestore.collection("raffles").document(raffle.id)
            .update(
                mapOf(
                    "winningNumber" to winningNumber,
                    "winnerName" to winner.userName,
                    "winnerEmail" to winner.userEmail,
                    "winnerContactInfo" to winner.contactInfo,
                    "winnerUserId" to winner.userId,
                    "winnerPhotoUrl" to winner.userPhotoUrl,
                    "winnerPrize" to prizeLabel,
                    "winnerAt" to now,
                    "updatedAt" to now,
                    "isActive" to false
                )
            )
            .await()

        return winner to winningNumber
    }

    suspend fun closeRaffle(raffleId: String) {
        require(raffleId.isNotBlank()) { "Sorteio inválido." }
        firestore.collection("raffles").document(raffleId)
            .update(
                mapOf(
                    "isActive" to false,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun clearWinner(raffleId: String) {
        require(raffleId.isNotBlank()) { "Sorteio inválido." }
        firestore.collection("raffles").document(raffleId)
            .update(
                mapOf(
                    "winnerName" to "",
                    "winnerEmail" to "",
                    "winnerContactInfo" to "",
                    "winnerUserId" to "",
                    "winnerPhotoUrl" to "",
                    "winnerPrize" to "",
                    "winningNumber" to "",
                    "winnerAt" to 0L,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    private fun buildPrizeLabel(prizeTitle: String, prizeValue: Double): String {
        val formatted = "R$ %.2f".format(prizeValue).replace(".", ",")
        return if (prizeValue > 0.0) "$prizeTitle - $formatted" else prizeTitle
    }
}

private fun DocumentSnapshot.toRaffleCampaign(): RaffleCampaign? {
    val data = data ?: return null
    val parsedTasks = (data["tasks"] as? List<*>)?.mapNotNull { raw ->
        when (raw) {
            is String -> {
                val title = raw.trim()
                if (title.isBlank()) null else RaffleTask(title = title)
            }
            is Map<*, *> -> {
                val title = (raw["title"] as? String ?: "").trim()
                if (title.isBlank()) null else RaffleTask(
                    title = title,
                    link = (raw["link"] as? String ?: "").trim()
                )
            }
            else -> null
        }
    } ?: emptyList()
    return RaffleCampaign(
        id = id,
        title = data["title"] as? String ?: "",
        prizeTitle = data["prizeTitle"] as? String ?: "",
        prizeValue = (data["prizeValue"] as? Number)?.toDouble() ?: 0.0,
        regulation = data["regulation"] as? String ?: "",
        tasks = parsedTasks,
        isActive = data["isActive"] as? Boolean ?: false,
        isNumberBased = data["isNumberBased"] as? Boolean ?: false,
        winningNumber = data["winningNumber"] as? String ?: "",
        winnerName = data["winnerName"] as? String ?: "",
        winnerEmail = data["winnerEmail"] as? String ?: "",
        winnerContactInfo = data["winnerContactInfo"] as? String ?: "",
        winnerUserId = data["winnerUserId"] as? String ?: "",
        winnerPhotoUrl = data["winnerPhotoUrl"] as? String ?: "",
        winnerPrize = data["winnerPrize"] as? String ?: "",
        winnerAt = (data["winnerAt"] as? Number)?.toLong() ?: 0L,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
    )
}

private fun DocumentSnapshot.toRaffleEntry(): RaffleEntry? {
    val data = data ?: return null
    return RaffleEntry(
        id = id,
        raffleId = data["raffleId"] as? String ?: "",
        userId = data["userId"] as? String ?: "",
        userName = data["userName"] as? String ?: "",
        userEmail = data["userEmail"] as? String ?: "",
        userPhotoUrl = data["userPhotoUrl"] as? String ?: "",
        contactInfo = data["contactInfo"] as? String ?: "",
        completedTasks = (data["completedTasks"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        luckyNumbers = (data["luckyNumbers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        allTasksCompleted = data["allTasksCompleted"] as? Boolean ?: false,
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
    )
}

private fun parseAdminTasks(lines: List<String>): List<RaffleTask> {
    return lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val parts = trimmed.split("|", limit = 2)
        val title = parts[0].trim()
        if (title.isBlank()) return@mapNotNull null
        val link = parts.getOrNull(1)?.trim().orEmpty()
        RaffleTask(title = title, link = link)
    }
}

private fun openExternalLink(context: Context, rawLink: String) {
    val link = rawLink.trim()
    if (link.isBlank()) return
    val normalized = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://$link"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir o link.", Toast.LENGTH_SHORT).show()
    }
}

private fun openWinnerContact(context: Context, email: String, contactInfo: String) {
    val trimmedContact = contactInfo.trim()
    val digits = trimmedContact.filter { it.isDigit() }
    val intent = when {
        digits.length >= 10 -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/55$digits"))
        email.isNotBlank() -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        trimmedContact.isNotBlank() -> Intent(Intent.ACTION_VIEW, Uri.parse(trimmedContact))
        else -> null
    }

    if (intent == null) {
        Toast.makeText(context, "Contato do ganhador não disponível.", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Não foi possível abrir o contato.", Toast.LENGTH_SHORT).show() }
}

class SorteiosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.jack.meuholerite.ads.RewardedInterstitialAdManager.loadAd(this, ignorePremium = true)
        setContent {
            MeuHoleriteTheme {
                SorteiosScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SorteiosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser
    val isAdmin = remember(currentUser?.email) {
        currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true)
    }

    var activeRaffle by remember { mutableStateOf<RaffleCampaign?>(null) }
    var history by remember { mutableStateOf<List<RaffleCampaign>>(emptyList()) }
    var userEntry by remember { mutableStateOf<RaffleEntry?>(null) }
    var raffleParticipants by remember { mutableStateOf<List<RaffleEntry>>(emptyList()) }
    var totalParticipants by remember { mutableIntStateOf(0) }
    var eligibleParticipants by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isDrawing by remember { mutableStateOf(false) }
    var isSavingAdmin by remember { mutableStateOf(false) }
    var isClosingRaffle by remember { mutableStateOf(false) }
    var deletingRaffleId by remember { mutableStateOf<String?>(null) }

    var adminRaffleId by remember { mutableStateOf<String?>(null) }
    var adminTitle by remember { mutableStateOf("") }
    var adminPrizeTitle by remember { mutableStateOf("") }
    var adminPrizeValue by remember { mutableStateOf("") }
    var adminTasksText by remember { mutableStateOf("") }
    var adminRegulation by remember { mutableStateOf("") }
    var adminIsNumberBased by remember { mutableStateOf(false) }
    var userContactInfo by remember { mutableStateOf("") }
    var showParticipantsDialog by remember { mutableStateOf(false) }

    val selectedTasks = remember { mutableStateMapOf<String, Boolean>() }

    DisposableEffect(Unit) {
        val activeListener = SorteiosRepository.observeActiveRaffle { raffle ->
            activeRaffle = raffle
        }
        val historyListener = SorteiosRepository.observeRaffleHistory { history = it }
        onDispose {
            activeListener.remove()
            historyListener.remove()
        }
    }

    DisposableEffect(activeRaffle?.id, currentUser?.uid) {
        var entryListener: ListenerRegistration? = null
        var statsListener: ListenerRegistration? = null
        var participantsListener: ListenerRegistration? = null

        val raffleId = activeRaffle?.id
        val uid = currentUser?.uid

        if (!raffleId.isNullOrBlank() && !uid.isNullOrBlank()) {
            entryListener = SorteiosRepository.observeEntry(raffleId, uid) { entry ->
                userEntry = entry
            }
        } else {
            userEntry = null
        }

        if (!raffleId.isNullOrBlank()) {
            statsListener = SorteiosRepository.observeStats(raffleId) { total, eligible ->
                totalParticipants = total
                eligibleParticipants = eligible
            }
            participantsListener = SorteiosRepository.observeParticipants(raffleId) { entries ->
                raffleParticipants = entries
            }
        } else {
            totalParticipants = 0
            eligibleParticipants = 0
            raffleParticipants = emptyList()
        }

        onDispose {
            entryListener?.remove()
            statsListener?.remove()
            participantsListener?.remove()
        }
    }

    LaunchedEffect(activeRaffle?.id, userEntry?.completedTasks) {
        selectedTasks.clear()
        activeRaffle?.tasks.orEmpty().forEach { task ->
            selectedTasks[task.title] = userEntry?.completedTasks?.contains(task.title) == true
        }
        userContactInfo = userEntry?.contactInfo.orEmpty()
    }

    LaunchedEffect(activeRaffle?.id, isAdmin) {
        if (isAdmin) {
            val raffle = activeRaffle
            if (raffle != null) {
                adminRaffleId = raffle.id
                adminTitle = raffle.title
                adminPrizeTitle = raffle.prizeTitle
                adminPrizeValue = if (raffle.prizeValue > 0.0) raffle.prizeValue.toString() else ""
                adminTasksText = raffle.tasks.joinToString("\n") { task ->
                    if (task.link.isBlank()) task.title else "${task.title} | ${task.link}"
                }
                adminRegulation = raffle.regulation
                adminIsNumberBased = raffle.isNumberBased
            } else if (adminRaffleId == null) {
                adminTitle = ""
                adminPrizeTitle = ""
                adminPrizeValue = ""
                adminTasksText = ""
                adminRegulation = ""
                adminIsNumberBased = false
            }
        }
    }

    val regras = listOf(
        "Só entra no sorteio quem marcar todas as tarefas da campanha atual.",
        "Quando o sorteio é realizado, a campanha ativa é encerrada e vai para o histórico."
    )

    Scaffold(
        containerColor = SorteiosBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sorteios", fontWeight = FontWeight.Black, color = SorteiosInk) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = SorteiosInk)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SorteiosBg)
            )
        }
    ) { padding ->
        if (showParticipantsDialog) {
            ParticipantsDialog(
                participants = raffleParticipants,
                showPrivateDetails = isAdmin,
                onDismiss = { showParticipantsDialog = false }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SorteiosBg)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(30.dp), color = Color.Transparent) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF13212E), Color(0xFF22455D), Color(0xFF2D6B4A))
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.14f)) {
                            Text(
                                "DINHEIRO E PRÊMIOS",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Participe dos sorteios do app e acompanhe tudo em um só lugar.",
                            color = Color.White,
                            fontSize = 26.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Confira a campanha atual, cumpra as tarefas, acompanhe os ganhadores anteriores e participe dos próximos prêmios.",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item { SectionTitle("Campanha atual", "Rodada ativa") }

            if (activeRaffle != null) {
                item {
                    CampaignShowcaseCard(
                        raffle = activeRaffle!!
                    )
                }

                item {
                    ParticipantsSummaryCard(
                        totalParticipants = totalParticipants,
                        eligibleParticipants = eligibleParticipants,
                        isAdmin = isAdmin,
                        onClick = { showParticipantsDialog = true }
                    )
                }

                item {
                    RegulationCard(
                        regulation = activeRaffle!!.regulation.ifBlank {
                            "Sem regulamento detalhado ainda."
                        }
                    )
                }

                if (!activeRaffle!!.isNumberBased) {
                    item { SectionTitle("Tarefas para participar", "Complete tudo para ficar elegível") }

                    items(activeRaffle!!.tasks) { task ->
                        TaskCheckboxCard(
                            task = task,
                            checked = selectedTasks[task.title] == true,
                            onCheckedChange = { selectedTasks[task.title] = it }
                        )
                    }
                }

                item {
                    ParticipantCard(
                        entry = userEntry,
                        totalTasks = activeRaffle!!.tasks.size,
                        completedTasks = selectedTasks.count { it.value },
                        contactInfo = userContactInfo,
                        onContactInfoChange = { userContactInfo = it },
                        isLoggedIn = currentUser != null,
                        isSubmitting = isSubmitting,
                        isNumberBased = activeRaffle!!.isNumberBased,
                        onSubmit = {
                            val raffle = activeRaffle ?: return@ParticipantCard
                            val completed = selectedTasks.filterValues { it }.keys.toList()
                            scope.launch {
                                isSubmitting = true
                                try {
                                    SorteiosRepository.submitEntry(raffle, completed, userContactInfo)
                                    Toast.makeText(context, "Participação salva com sucesso.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Não foi possível salvar sua participação.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        onWatchAd = {
                            val raffle = activeRaffle ?: return@ParticipantCard
                            if (userContactInfo.isBlank()) {
                                Toast.makeText(context, "Preencha seu contato antes de assistir ao anúncio.", Toast.LENGTH_SHORT).show()
                                return@ParticipantCard
                            }
                            com.jack.meuholerite.ads.RewardedInterstitialAdManager.showAd(context as android.app.Activity, ignorePremium = true) { earned ->
                                if (earned) {
                                    scope.launch {
                                        isSubmitting = true
                                        try {
                                            SorteiosRepository.assignLuckyNumber(raffle, userContactInfo)
                                            Toast.makeText(context, "Número da sorte gerado!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Erro ao gerar número.", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Você precisa assistir ao anúncio completo para obter o número.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            } else {
                item {
                    EmptyStateCard("Nenhum sorteio ativo no momento.", "Assim que o admin publicar uma nova campanha, ela aparecerá aqui.")
                }
            }

            if (isAdmin) {
                item { SectionTitle("Painel admin", "Controle da campanha") }
                item {
                    AdminPanelCard(
                        title = adminTitle,
                        onTitleChange = { adminTitle = it },
                        prizeTitle = adminPrizeTitle,
                        onPrizeTitleChange = { adminPrizeTitle = it },
                        prizeValue = adminPrizeValue,
                        onPrizeValueChange = { adminPrizeValue = it },
                        tasksText = adminTasksText,
                        onTasksTextChange = { adminTasksText = it },
                        regulation = adminRegulation,
                        onRegulationChange = { adminRegulation = it },
                        isNumberBased = adminIsNumberBased,
                        onIsNumberBasedChange = { adminIsNumberBased = it },
                        totalParticipants = totalParticipants,
                        eligibleParticipants = eligibleParticipants,
                        isSaving = isSavingAdmin,
                        isDrawing = isDrawing,
                        isClosing = isClosingRaffle,
                        hasActiveRaffle = activeRaffle != null,
                        onSave = {
                            scope.launch {
                                isSavingAdmin = true
                                try {
                                    SorteiosRepository.saveRaffle(
                                        raffleId = adminRaffleId,
                                        title = adminTitle,
                                        prizeTitle = adminPrizeTitle,
                                        prizeValue = adminPrizeValue.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                        regulation = adminRegulation,
                                        tasks = parseAdminTasks(adminTasksText.lines()),
                                        isNumberBased = adminIsNumberBased
                                    )
                                    Toast.makeText(context, "Campanha salva com sucesso.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Não foi possível salvar a campanha.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSavingAdmin = false
                                }
                            }
                        },
                        onCreateNew = {
                            adminRaffleId = null
                            adminTitle = ""
                            adminPrizeTitle = ""
                            adminPrizeValue = ""
                            adminTasksText = ""
                            adminRegulation = ""
                            adminIsNumberBased = false
                        },
                        onDraw = {
                            val raffle = activeRaffle ?: return@AdminPanelCard
                            scope.launch {
                                isDrawing = true
                                try {
                                    val (winner, winningNumber) = SorteiosRepository.drawWinner(raffle)
                                    val msg = if (winningNumber.isNotBlank()) "Sorteado: ${winner.userName} (Nº $winningNumber)" else "Sorteado: ${winner.userName}"
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    adminRaffleId = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Não foi possível realizar o sorteio.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isDrawing = false
                                }
                            }
                        },
                        onClose = {
                            val raffle = activeRaffle ?: return@AdminPanelCard
                            scope.launch {
                                isClosingRaffle = true
                                try {
                                    SorteiosRepository.closeRaffle(raffle.id)
                                    Toast.makeText(context, "Campanha encerrada com sucesso.", Toast.LENGTH_SHORT).show()
                                    adminRaffleId = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Não foi possível encerrar a campanha.", Toast.LENGTH_LONG).show()
                                } finally {
                                    isClosingRaffle = false
                                }
                            }
                        }
                    )
                }
            }

            item { SectionTitle("Ganhadores anteriores", "Histórico recente") }

            if (history.isEmpty()) {
                item {
                    EmptyStateCard("Sem histórico ainda.", "Os sorteios encerrados com vencedor aparecerão aqui.")
                }
            } else {
                items(history) { raffle ->
                    WinnerCard(
                        isAdmin = isAdmin,
                        isDeleting = deletingRaffleId == raffle.id,
                        winnerName = raffle.winnerName.ifBlank { "Campanha encerrada" },
                        winnerEmail = raffle.winnerEmail,
                        winnerContactInfo = raffle.winnerContactInfo,
                        winningNumber = raffle.winningNumber,
                        prize = raffle.winnerPrize.ifBlank {
                            buildString {
                                append(raffle.prizeTitle)
                                if (raffle.prizeValue > 0.0) {
                                    append(" - R$ ")
                                    append("%.2f".format(raffle.prizeValue).replace(".", ","))
                                }
                            }
                        },
                        subtitle = raffle.title,
                        winnerPhotoUrl = raffle.winnerPhotoUrl,
                        onDelete = if (isAdmin) {
                            {
                                scope.launch {
                                    deletingRaffleId = raffle.id
                                    try {
                                        SorteiosRepository.clearWinner(raffle.id)
                                        Toast.makeText(context, "Ganhador removido com sucesso.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Não foi possível apagar o ganhador.", Toast.LENGTH_LONG).show()
                                    } finally {
                                        deletingRaffleId = null
                                    }
                                }
                            }
                        } else null
                    )
                }
            }

            item { SectionTitle("Regulamento geral", "Como funciona") }

            items(regras) { regra ->
                InfoRowCard(
                    icon = Icons.Filled.Gavel,
                    iconTint = SorteiosRose,
                    title = "Regra",
                    description = regra
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SorteiosGreen)
                            Spacer(Modifier.size(10.dp))
                            Text("Transparência do sorteio", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SorteiosInk)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "O sorteio usa apenas usuários que marcaram todas as tarefas da campanha ativa. O vencedor e o prêmio ficam salvos no histórico.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = SorteiosBlue,
                letterSpacing = 1.sp
            )
        }
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = SorteiosInk)
    }
}

@Composable
private fun CampaignShowcaseCard(
    raffle: RaffleCampaign
) {
    val prizeLine = buildString {
        append(raffle.prizeTitle)
        if (raffle.prizeValue > 0.0) {
            append(" - R$ ")
            append("%.2f".format(raffle.prizeValue).replace(".", ","))
        }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0F1C28),
                            Color(0xFF183551),
                            Color(0xFF24566E),
                            Color(0xFF2E724E)
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = 0.14f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Casino,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (raffle.winnerName.isBlank()) "CAMPANHA ATIVA" else "ATIVA COM GANHADOR",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = SorteiosGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = raffle.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Conclua as tarefas e participe do prêmio da rodada.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.11f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.AttachMoney, contentDescription = null, tint = SorteiosGold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prêmio da rodada",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = prizeLine,
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CardGiftcard,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (raffle.winnerName.isBlank()) {
                                        "Rodada aberta para participação"
                                    } else {
                                        "Rodada com vencedor definido"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (raffle.isActive) "Ao vivo" else "Finalizada",
                                color = SorteiosGold,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroMetricChip("Tarefas", raffle.tasks.size.toString(), Modifier.weight(1f))
                HeroMetricChip("Status", if (raffle.isActive) "Ativa" else "Encerrada", Modifier.weight(1f))
                HeroMetricChip("Prêmio", if (raffle.prizeValue > 0.0) "R$ %.0f".format(raffle.prizeValue) else "Especial", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ParticipantsSummaryCard(
    totalParticipants: Int,
    eligibleParticipants: Int,
    isAdmin: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(SorteiosBlue.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PeopleAlt, contentDescription = null, tint = SorteiosBlue)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Participantes", fontWeight = FontWeight.Black, fontSize = 17.sp, color = SorteiosInk)
                Text(
                    if (isAdmin) {
                        "$eligibleParticipants elegíveis de $totalParticipants participantes"
                    } else {
                        "$totalParticipants participantes nesta campanha"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SorteiosBlue.copy(alpha = 0.10f)
            ) {
                Text(
                    "Ver lista",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = SorteiosBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun HeroMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.66f),
                letterSpacing = 0.9.sp
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.14f),
            onClick = onClick,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.12f),
            modifier = modifier
        ) {
            content()
        }
    }
}

@Composable
private fun ParticipantsDialog(
    participants: List<RaffleEntry>,
    showPrivateDetails: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Participantes", fontWeight = FontWeight.Black, color = SorteiosInk)
                Text(
                    "${participants.count { it.allTasksCompleted }} elegíveis de ${participants.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (participants.isEmpty()) {
                Text("Nenhum participante inscrito nesta campanha.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(participants) { participant ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (participant.allTasksCompleted) {
                                SorteiosGreen.copy(alpha = 0.10f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        participant.userName.ifBlank { "Usuário" },
                                        fontWeight = FontWeight.Bold,
                                        color = SorteiosInk
                                    )
                                    Text(
                                        if (participant.allTasksCompleted) "Elegível" else "Pendente",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (participant.allTasksCompleted) SorteiosGreen else SorteiosRose
                                    )
                                }
                                if (showPrivateDetails && participant.userEmail.isNotBlank()) {
                                    Text(participant.userEmail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (showPrivateDetails && participant.contactInfo.isNotBlank()) {
                                    Text(participant.contactInfo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (showPrivateDetails) {
                                    Text(
                                        "${participant.completedTasks.size} tarefa(s) marcadas",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun RegulationCard(regulation: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFFF6E7),
                            Color(0xFFFBE9C7),
                            Color(0xFFF7F2E7)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = SorteiosGold)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Regulamento", fontWeight = FontWeight.Black, fontSize = 18.sp, color = SorteiosInk)
                    Text("Importante antes de participar", fontSize = 12.sp, color = SorteiosInk.copy(alpha = 0.68f))
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.72f)
            ) {
                Text(
                    text = regulation,
                    modifier = Modifier.padding(16.dp),
                    color = SorteiosInk.copy(alpha = 0.82f),
                    lineHeight = 22.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiniInfoPill("Leitura rápida", Modifier.weight(1f))
                MiniInfoPill("Antes do sorteio", Modifier.weight(1f))
                MiniInfoPill("Prêmio e contato", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniInfoPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.66f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SorteiosInk,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TaskStatusBadge(
    checked: Boolean
) {
    val bg = if (checked) SorteiosGreen.copy(alpha = 0.14f) else SorteiosBlue.copy(alpha = 0.10f)
    val fg = if (checked) SorteiosGreen else SorteiosBlue

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = if (checked) "Concluída" else "Pendente",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = fg
        )
    }
}

@Composable
private fun InfoRowCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SorteiosInk)
                Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun TaskCheckboxCard(
    task: RaffleTask,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (checked) SorteiosGreen.copy(alpha = 0.12f) else SorteiosBlue.copy(alpha = 0.10f),
                            RoundedCornerShape(15.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        task.title,
                        color = SorteiosInk,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (task.link.isBlank()) "Marque quando concluir esta etapa." else "Essa tarefa possui um link de apoio.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                TaskStatusBadge(checked = checked)
            }

            if (task.link.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SorteiosBlue.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, tint = SorteiosBlue, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Link de apoio", fontWeight = FontWeight.Bold, color = SorteiosInk, fontSize = 13.sp)
                            Text(
                                task.link,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        FilledTonalButton(
                            onClick = { openExternalLink(context, task.link) },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Abrir", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantCard(
    entry: RaffleEntry?,
    totalTasks: Int,
    completedTasks: Int,
    contactInfo: String,
    onContactInfoChange: (String) -> Unit,
    isLoggedIn: Boolean,
    isSubmitting: Boolean,
    isNumberBased: Boolean,
    onSubmit: () -> Unit,
    onWatchAd: () -> Unit
) {
    val allTasksChecked = totalTasks > 0 && completedTasks >= totalTasks
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SorteiosBlue.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isNumberBased) Icons.Filled.ConfirmationNumber else Icons.Filled.TaskAlt, contentDescription = null, tint = SorteiosBlue)
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Sua participação", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SorteiosInk)
                    Text(
                        if (isNumberBased) "Assista a um anúncio para obter números da sorte e deixe seu contato." 
                        else "Confirme as tarefas e deixe seu contato.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = contactInfo,
                onValueChange = onContactInfoChange,
                label = { Text("WhatsApp ou telefone para contato") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isLoggedIn && !isSubmitting,
                shape = RoundedCornerShape(16.dp)
            )

            if (isNumberBased) {
                if (entry != null && entry.luckyNumbers.isNotEmpty()) {
                    var showCodesDialog by remember { mutableStateOf(false) }

                    Text(
                        "Você está participando! 🎊",
                        fontWeight = FontWeight.Bold,
                        color = SorteiosGreen,
                        fontSize = 15.sp
                    )
                    
                    OutlinedButton(
                        onClick = { showCodesDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Meus códigos", fontWeight = FontWeight.Bold, color = SorteiosBlue)
                    }

                    if (showCodesDialog) {
                        MyCodesDialog(
                            luckyNumbers = entry.luckyNumbers,
                            onDismiss = { showCodesDialog = false }
                        )
                    }

                    Text(
                        "Assista mais anúncios para obter mais números e aumentar suas chances de ganhar!",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onWatchAd,
                    enabled = isLoggedIn && !isSubmitting && contactInfo.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (entry != null && entry.luckyNumbers.isNotEmpty()) "Obter mais números" else "Participar")
                }
            } else {
                Text(
                    when {
                        !isLoggedIn -> "Faça login para marcar as tarefas e entrar no sorteio."
                        entry?.allTasksCompleted == true -> "Você já cumpriu todas as tarefas e está elegível para o sorteio."
                        entry != null -> "Sua participação foi salva, mas ainda faltam tarefas para ficar elegível."
                        else -> "Marque todas as tarefas concluídas para confirmar sua participação."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Text(
                    "$completedTasks de $totalTasks tarefas concluídas",
                    color = if (allTasksChecked) SorteiosGreen else SorteiosRose,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SorteiosGreen.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (entry?.allTasksCompleted == true) {
                            "Seu cadastro já está pronto para entrar no sorteio desta rodada."
                        } else {
                            "A participação só é salva quando todas as tarefas da campanha estiverem marcadas como concluídas."
                        },
                        modifier = Modifier.padding(14.dp),
                        color = SorteiosInk,
                        lineHeight = 19.sp,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = isLoggedIn && !isSubmitting && allTasksChecked && contactInfo.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Salvar participação")
                }
            }
        }
    }
}

@Composable
private fun AdminPanelCard(
    title: String,
    onTitleChange: (String) -> Unit,
    prizeTitle: String,
    onPrizeTitleChange: (String) -> Unit,
    prizeValue: String,
    onPrizeValueChange: (String) -> Unit,
    tasksText: String,
    onTasksTextChange: (String) -> Unit,
    regulation: String,
    onRegulationChange: (String) -> Unit,
    isNumberBased: Boolean,
    onIsNumberBasedChange: (Boolean) -> Unit,
    totalParticipants: Int,
    eligibleParticipants: Int,
    isSaving: Boolean,
    isDrawing: Boolean,
    isClosing: Boolean,
    hasActiveRaffle: Boolean,
    onSave: () -> Unit,
    onCreateNew: () -> Unit,
    onDraw: () -> Unit,
    onClose: () -> Unit
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SorteiosRose.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = SorteiosRose)
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Painel do administrador", fontWeight = FontWeight.Black, fontSize = 18.sp, color = SorteiosInk)
                    Text("Configure a rodada sem alterar a estrutura dos dados.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMiniCard("Participantes", totalParticipants.toString(), Icons.Filled.PeopleAlt, Modifier.weight(1f))
                StatMiniCard("Elegíveis", eligibleParticipants.toString(), Icons.Filled.CheckCircle, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Título da campanha") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = prizeTitle,
                onValueChange = onPrizeTitleChange,
                label = { Text("Prêmio") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = prizeValue,
                onValueChange = onPrizeValueChange,
                label = { Text("Valor do prêmio") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(checked = isNumberBased, onCheckedChange = onIsNumberBasedChange)
                Text(
                    "Sorteio por Números da Sorte (Anúncios)",
                    color = SorteiosInk,
                    fontSize = 14.sp
                )
            }

            if (!isNumberBased) {
                OutlinedTextField(
                    value = tasksText,
                    onValueChange = onTasksTextChange,
                    label = { Text("Tarefas, uma por linha") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    "Formato com link: Tarefa | https://seulink.com",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            OutlinedTextField(
                value = regulation,
                onValueChange = onRegulationChange,
                label = { Text("Regulamento") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(16.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onCreateNew,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Nova campanha")
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (hasActiveRaffle) "Atualizar" else "Publicar")
                }
            }

            OutlinedButton(
                onClick = onDraw,
                enabled = hasActiveRaffle && !isDrawing,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                if (isDrawing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text("Realizar sorteio aleatório")
            }

            OutlinedButton(
                onClick = onClose,
                enabled = hasActiveRaffle && !isClosing,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isClosing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text("Encerrar campanha")
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f), modifier = modifier) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = SorteiosBlue)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = SorteiosInk)
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, description: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SorteiosInk)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun WinnerCard(
    isAdmin: Boolean,
    isDeleting: Boolean,
    winnerName: String,
    winnerEmail: String,
    winnerContactInfo: String,
    winningNumber: String,
    prize: String,
    subtitle: String,
    winnerPhotoUrl: String = "",
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (winnerPhotoUrl.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        AsyncImage(
                            model = winnerPhotoUrl,
                            contentDescription = "Foto do vencedor",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(SorteiosGold.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = SorteiosGold)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GANHADOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = SorteiosBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = winnerName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SorteiosInk)
                    if (winningNumber.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = "Nº Sorteado: $winningNumber", color = SorteiosBlue, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = prize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(text = subtitle, color = SorteiosBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = SorteiosBlue.copy(alpha = 0.10f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CardGiftcard, contentDescription = null, tint = SorteiosBlue, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (isAdmin && (winnerEmail.isNotBlank() || winnerContactInfo.isNotBlank())) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { openWinnerContact(context, winnerEmail, winnerContactInfo) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (winnerContactInfo.filter { it.isDigit() }.length >= 10) Icons.Outlined.Phone else Icons.Outlined.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Contatar ganhador")
                    }
                    if (winnerEmail.isNotBlank()) {
                        OutlinedButton(
                            onClick = { openWinnerContact(context, winnerEmail, "") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Abrir e-mail")
                        }
                    }
                }
            }

            if (isAdmin && onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Apagar ganhador")
                }
            }
        }
    }
}

@Composable
private fun MyCodesDialog(
    luckyNumbers: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold, color = SorteiosBlue)
            }
        },
        title = {
            Text("Meus Códigos da Sorte", fontWeight = FontWeight.Black, color = SorteiosInk)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(luckyNumbers) { number ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SorteiosBlue.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ConfirmationNumber,
                                contentDescription = null,
                                tint = SorteiosBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = number,
                                color = SorteiosInk,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    )
}

