package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.ui.AbsenceDetailCard
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.IosWidgetCardClickable
import com.jack.meuholerite.ui.IosWidgetSummaryLargeCard
import com.jack.meuholerite.ui.getIconForLabel
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PontoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageManager = StorageManager(this)

        setContent {
            val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = if (storageManager.hasDarkModeSet()) storageManager.isDarkMode() else systemInDarkTheme

            MeuHoleriteTheme(darkTheme = useDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PontoScreenContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PontoScreenContent() {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val gson = remember { Gson() }
        val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
        val scope = rememberCoroutineScope()
        val backupManager = remember { BackupManager(context) }

        var selectedEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
        var selectedPontoItemForPopup by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
        var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
        var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
        var showEditProfile by remember { mutableStateOf(false) }

        suspend fun refreshData() {
            withContext(Dispatchers.IO) {
                val newName = prefs.getString("user_name", "") ?: ""
                val newMatricula = prefs.getString("user_matricula", "") ?: ""
                withContext(Dispatchers.Main) {
                    userName = newName
                    userMatricula = newMatricula
                }
                val list = db.espelhoDao().getAll().map { it.toModel(gson) }
                if (list.isNotEmpty()) {
                    selectedEspelho = list.sortedByDescending { it.periodo.extractStartDate() }.first()
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshData()
        }

        if (showEditProfile) {
            EditProfileDialog(userName, userMatricula, { showEditProfile = false }) { name, matricula ->
                userName = name
                userMatricula = matricula
                prefs.edit().putString("user_name", name).putString("user_matricula", matricula).apply()
                showEditProfile = false
                scope.launch { backupManager.backupData() }
            }
        }

        if (selectedPontoItemForPopup != null) {
            PontoDetailDialog(
                label = selectedPontoItemForPopup!!.first,
                value = selectedPontoItemForPopup!!.second,
                isNegative = selectedPontoItemForPopup!!.third,
                onDismiss = { selectedPontoItemForPopup = null }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.timesheet)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                TimesheetScreen(
                    espelho = selectedEspelho,
                    db = db,
                    gson = gson,
                    userName = userName,
                    userMatricula = userMatricula,
                    onEditProfile = { showEditProfile = true },
                    onSelect = { selectedEspelho = it },
                    onOpen = { openPdf(it) },
                    onRefresh = { scope.launch { refreshData() } },
                    onSelectItem = { label, value, isNegative ->
                        selectedPontoItemForPopup = Triple(label, value, isNegative)
                    }
                )
            }
        }
    }

    private fun openPdf(filePath: String?) {
        if (filePath == null) return
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intentView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            startActivity(Intent.createChooser(intentView, "Abrir PDF com..."))
        } catch (_: Exception) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun TimesheetScreen(
    espelho: EspelhoPonto?,
    db: AppDatabase,
    gson: Gson,
    userName: String,
    userMatricula: String,
    onEditProfile: () -> Unit,
    onSelect: (EspelhoPonto) -> Unit,
    onOpen: (String?) -> Unit,
    onRefresh: () -> Unit,
    onSelectItem: (label: String, value: String, isNegative: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val historicoEntities by db.espelhoDao().getAllFlow().collectAsState(initial = emptyList())
    val historico = remember(historicoEntities) {
        historicoEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDate() }
    }
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        TimesheetHistoryDialog(
            historico = historico,
            onDismiss = { showHistory = false },
            onSelect = { onSelect(it); showHistory = false },
            onDelete = {
                scope.launch {
                    db.espelhoDao().deleteByPeriodo(it.periodo)
                    onRefresh()
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = espelho?.periodo ?: stringResource(R.string.select_pdf),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 17.sp
                )
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Default.History, null, tint = Color(0xFF007AFF))
                }
            }
        }
        if (espelho != null) {
            item {
                IosWidgetSummaryLargeCard(
                    espelho = espelho,
                    userName = userName,
                    matricula = userMatricula,
                    onEdit = onEditProfile,
                    onOpen = { onOpen(espelho.pdfFilePath) }
                )
            }
            if (espelho.hasAbsences) {
                item { AbsenceDetailCard(espelho) }
            }
            val proventos = espelho.resumoItens.filter { !it.isNegative }
            val descontos = espelho.resumoItens.filter { it.isNegative }

            if (proventos.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.earnings).uppercase(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        proventos.forEach { item ->
                            val label = if (item.label == "label_worked_hours") "Total de Horas Trabalhadas" else item.label
                            IosWidgetCardClickable(
                                title = label,
                                value = item.value,
                                color = Color(0xFF007AFF),
                                icon = getIconForLabel(item.label, item.isNegative),
                                onClick = { onSelectItem(label, item.value, item.isNegative) }
                            )
                        }
                    }
                }
            }
            if (descontos.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.deductions).uppercase(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        descontos.forEach { item ->
                            IosWidgetCardClickable(
                                title = item.label,
                                value = item.value,
                                color = Color(0xFFFF3B30),
                                icon = getIconForLabel(item.label, item.isNegative),
                                onClick = { onSelectItem(item.label, item.value, item.isNegative) }
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun TimesheetHistoryDialog(
    historico: List<EspelhoPonto>,
    onDismiss: () -> Unit,
    onSelect: (EspelhoPonto) -> Unit,
    onDelete: (EspelhoPonto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text("Histórico de Pontos", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(historico) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.periodo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Saldo: ${item.saldoFinalBH}",
                                color = if (item.saldoFinalBH.startsWith("-")) Color(0xFFFF3B30) else Color(0xFF34C759)
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun PontoDetailDialog(label: String, value: String, isNegative: Boolean, onDismiss: () -> Unit) {
    val color = if (isNegative) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val icon = if (isNegative) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingUp
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Valor: $value", fontWeight = FontWeight.SemiBold, color = color)
                HorizontalDivider()
                Text("O que este item significa?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    getDetalheParaResumoItem(label),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

fun getDetalheParaResumoItem(label: String): String {
    val key = label.uppercase().trim()
    return when {
        key == "SALDO ANTERIOR B.H." -> "O total de horas extras ou negativas acumuladas até o período anterior."
        key.contains("CRÉDITO H.E.") -> "Total de horas extras trabalhadas que foram adicionadas ao seu Banco de Horas."
        key == "TOTAL DE HORAS TRABALHADAS" -> "Soma de todas as horas registradas nas batidas do seu ponto."
        key == "HORAS FALTAS JUSTIFICADAS" -> "Horas de ausência que foram compensadas ou justificadas por atestado."
        key == "TOTAL DE DÉBITOS" -> "Soma de todas as horas negativas registradas (atrasos, faltas)."
        key == "SALDO FINAL B.H." -> "O saldo final consolidado do seu Banco de Horas no final do período."
        else -> "Esta é uma informação de resumo do seu espelho de ponto."
    }
}
