package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoItem
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
                labelKey = selectedPontoItemForPopup!!.first,
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
                    actions = {
                        if (selectedEspelho != null) {
                            IconButton(onClick = { sharePdf(selectedEspelho?.pdfFilePath) }) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar PDF")
                            }
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
                    onOpen = { sharePdf(it) },
                    onRefresh = { scope.launch { refreshData() } },
                    onSelectItem = { label, value, isNegative ->
                        selectedPontoItemForPopup = Triple(label, value, isNegative)
                    }
                )
            }
        }
    }

    private fun sharePdf(filePath: String?) {
        if (filePath == null) {
            Toast.makeText(this, "Arquivo PDF não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "O arquivo físico não existe no armazenamento", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intentShare = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intentShare, "Exportar Espelho de Ponto"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun resolveLabel(label: String): String {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(label, "string", context.packageName)
    return if (resId != 0) stringResource(resId) else label
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

            // Banco de Horas
            item {
                Text(
                    text = stringResource(R.string.bank_hours).uppercase(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosWidgetCardClickable(
                        title = stringResource(R.string.label_period_balance),
                        value = espelho.saldoPeriodoBH,
                        color = if (espelho.saldoPeriodoBH.startsWith("-")) Color(0xFFFF3B30) else Color(0xFF34C759),
                        icon = Icons.Outlined.History,
                        onClick = { onSelectItem("label_period_balance", espelho.saldoPeriodoBH, espelho.saldoPeriodoBH.startsWith("-")) }
                    )
                }
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
                            val displayLabel = resolveLabel(item.label)
                            IosWidgetCardClickable(
                                title = displayLabel,
                                value = item.value,
                                color = Color(0xFF007AFF),
                                icon = getIconForLabel(item.label, item.isNegative),
                                onClick = { onSelectItem(item.label, item.value, item.isNegative) }
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
                            val displayLabel = resolveLabel(item.label)
                            IosWidgetCardClickable(
                                title = displayLabel,
                                value = item.value,
                                color = Color(0xFFFF3B30),
                                icon = getIconForLabel(item.label, item.isNegative),
                                onClick = { onSelectItem(item.label, item.value, item.isNegative) }
                            )
                        }
                    }
                }
            }

            // Jornada
            item {
                Text(
                    text = stringResource(R.string.work_schedule),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectItem("label_work_schedule", espelho.jornada, false) },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFF8E8E93).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Schedule, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.label_standard_schedule),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                espelho.jornada,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
fun PontoDetailDialog(labelKey: String, value: String, isNegative: Boolean, onDismiss: () -> Unit) {
    val displayLabel = resolveLabel(labelKey)
    val color = if (isNegative) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val icon = if (isNegative) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingUp
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(displayLabel, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Valor: $value", fontWeight = FontWeight.SemiBold, color = color)
                HorizontalDivider()
                Text("O que este item significa?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    getDetalheParaResumoItem(labelKey),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun getDetalheParaResumoItem(label: String): String {
    return when (label) {
        "label_worked_hours" -> stringResource(R.string.desc_ponto_total_trabalhadas)
        "label_night_allowance" -> stringResource(R.string.desc_ponto_horas_noturnas)
        "label_interval_delay" -> stringResource(R.string.desc_ponto_atraso_intervalo)
        "label_early_departure" -> stringResource(R.string.desc_ponto_saida_antecipada)
        "label_extra_hours_50" -> stringResource(R.string.desc_ponto_credito_he)
        "label_extra_hours_100" -> stringResource(R.string.desc_ponto_credito_he)
        "label_excused_absence" -> stringResource(R.string.desc_ponto_horas_abonadas)
        "label_absences" -> stringResource(R.string.desc_ponto_faltas)
        "label_period_balance" -> stringResource(R.string.desc_ponto_saldo_periodo)
        "label_previous_balance" -> stringResource(R.string.desc_ponto_saldo_anterior)
        "label_work_schedule" -> stringResource(R.string.desc_ponto_jornada)
        else -> {
            val key = label.uppercase().trim()
            when {
                key.contains("SALDO ANTERIOR") -> stringResource(R.string.desc_ponto_saldo_anterior)
                key.contains("CRÉDITO H.E.") -> stringResource(R.string.desc_ponto_credito_he)
                key == "TOTAL DE HORAS TRABALHADAS" -> stringResource(R.string.desc_ponto_total_trabalhadas)
                key == "HORAS FALTAS JUSTIFICADAS" -> stringResource(R.string.desc_ponto_horas_abonadas)
                key == "TOTAL DE DÉBITOS" -> stringResource(R.string.desc_ponto_total_debitos)
                key == "SALDO FINAL B.H." -> stringResource(R.string.desc_ponto_saldo_final)
                else -> stringResource(R.string.desc_ponto_default)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PontoScreenPreview() {
    val sampleEspelho = EspelhoPonto(
        funcionario = "Usuário de Teste",
        empresa = "Empresa Exemplo",
        periodo = "01/10/2023 a 31/10/2023",
        jornada = "08:00 12:00 13:00 17:00",
        resumoItens = listOf(
            EspelhoItem("label_worked_hours", "160:00", false),
            EspelhoItem("label_extra_hours_50", "10:00", false),
            EspelhoItem("label_absences", "02:00", true)
        ),
        saldoFinalBH = "08:00",
        saldoPeriodoBH = "05:00",
        detalhesSaldoBH = "Saldo anterior: 03:00"
    )

    MeuHoleriteTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TimesheetScreen(
                espelho = sampleEspelho,
                db = AppDatabase.getDatabase(LocalContext.current),
                gson = Gson(),
                userName = "Jackson",
                userMatricula = "12345",
                onEditProfile = {},
                onSelect = {},
                onOpen = {},
                onRefresh = {},
                onSelectItem = { _, _, _ -> }
            )
        }
    }
}
