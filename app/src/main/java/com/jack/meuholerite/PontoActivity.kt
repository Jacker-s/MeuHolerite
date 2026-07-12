package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoItem
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.ui.AbsenceDetailCard
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.PontoDetailDialog
import com.jack.meuholerite.ui.NativeAdSize
import com.jack.meuholerite.ui.NativeInlineAd
import com.jack.meuholerite.ui.getIconForLabel
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.utils.extractStartDate
import com.jack.meuholerite.utils.timeToMinutes
import com.jack.meuholerite.utils.minutesToTime
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
            val useDarkTheme =
                if (storageManager.hasDarkModeSet()) storageManager.isDarkMode() else systemInDarkTheme
            val themeAccent = storageManager.getThemeAccent()

            MeuHoleriteTheme(darkTheme = useDarkTheme, themeAccent = themeAccent) {
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
        var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
        var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
        var userCargo by remember { mutableStateOf(prefs.getString("user_cargo", "") ?: "") }
        var showEditProfile by remember { mutableStateOf(false) }

        suspend fun refreshData() {
            withContext(Dispatchers.IO) {
                val newName = prefs.getString("user_name", "") ?: ""
                val newMatricula = prefs.getString("user_matricula", "") ?: ""
                val list = db.espelhoDao().getAll().map { it.toModel(gson) }

                withContext(Dispatchers.Main) {
                    userName = newName
                    userMatricula = newMatricula
                    userCargo = prefs.getString("user_cargo", "") ?: ""
                    if (list.isNotEmpty()) {
                        selectedEspelho = list.sortedByDescending { it.periodo.extractStartDate() }.first()
                    }
                }
            }
        }

        LaunchedEffect(Unit) { refreshData() }

        if (showEditProfile) {
            EditProfileDialog(
                userName,
                userMatricula,
                userCargo,
                { showEditProfile = false }
            ) { name, matricula, cargo ->
                userName = name
                userMatricula = matricula
                userCargo = cargo
                prefs.edit()
                    .putString("user_name", name)
                    .putString("user_matricula", matricula)
                    .putString("user_cargo", cargo)
                    .apply()
                showEditProfile = false
                scope.launch { backupManager.backupData() }
            }
        }

        // Dialog logic removed since Expansion is inline now

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Espelho de Ponto",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        if (selectedEspelho != null) {
                            IconButton(onClick = { sharePdf(selectedEspelho?.pdfFilePath) }) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                TimesheetScreen(
                    espelho = selectedEspelho,
                    db = db,
                    gson = gson,
                    userName = selectedEspelho?.let { if (it.funcionario.isNotBlank() && it.funcionario != "Não encontrado") it.funcionario else userName } ?: userName,
                    userMatricula = selectedEspelho?.let { if (it.matricula.isNotBlank()) it.matricula else userMatricula } ?: userMatricula,
                    userCargo = selectedEspelho?.let { if (it.cargo.isNotBlank()) it.cargo else userCargo } ?: userCargo,
                    onEditProfile = { showEditProfile = true },
                    onSelect = { selectedEspelho = it },
                    onOpen = { sharePdf(it) },
                    onRefresh = { scope.launch { refreshData() } }
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
    userCargo: String,
    onEditProfile: () -> Unit,
    onSelect: (EspelhoPonto) -> Unit,
    onOpen: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }
    val adsRemovedState by AdsDataStore.isAdsRemovedFlow(context).collectAsState(initial = false)

    val historicoEntities by db.espelhoDao().getAllFlow().collectAsState(initial = emptyList())
    val historico = remember(historicoEntities) {
        historicoEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDate() }
    }
    val proventos = remember(espelho) {
        espelho?.resumoItens?.filter { !it.isNegative && it.label != "label_worked_hours" }.orEmpty()
    }
    val descontos = remember(espelho) {
        espelho?.resumoItens?.filter { it.isNegative }.orEmpty()
    }

    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        TimesheetHistoryDialog(
            historico = historico,
            onDismiss = { showHistory = false },
            onSelect = { onSelect(it); showHistory = false },
            onDelete = { item ->
                scope.launch {
                    db.espelhoDao().deleteByPeriodo(item.periodo)
                    backupManager.backupData()
                    onRefresh()
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            TimesheetHeaderCard(
                periodText = espelho?.periodo ?: "Selecionar PDF",
                userName = userName,
                userMatricula = userMatricula,
                userCargo = userCargo,
                onEditProfile = onEditProfile,
                onOpenHistory = { showHistory = true }
            )
        }

        if (espelho == null) {
            item { EmptyStateCard() }
            return@LazyColumn
        }

        item { SummaryHeroCard(espelho = espelho) }

        if (!adsRemovedState && (proventos.isNotEmpty() || descontos.isNotEmpty())) {
            item {
                NativeInlineAd(
                    adUnitId = "ca-app-pub-7931782163570852/1526069738",
                    size = NativeAdSize.Regular
                )
            }
        }


        if (espelho.detalhesSaldoBH.isNotEmpty()) {
            item {
                SectionHeaderPonto("Resumo do Banco de Horas")
                BankHoursCard(text = espelho.detalhesSaldoBH)
            }
        }

        if (espelho.hasAbsences) {
            item { AbsenceDetailCard(espelho) }
        }

        if (proventos.isNotEmpty()) {
            item { SectionHeaderPonto("Proventos") }
            itemsIndexed(proventos) { index, item ->
                PontoItemRow(
                    title = resolveLabel(item.label),
                    value = item.value,
                    color = Color(0xFF007AFF),
                    icon = getIconForLabel(item.label, item.isNegative),
                    labelKey = item.label,
                    isNegative = false
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }

        if (descontos.isNotEmpty()) {
            item { SectionHeaderPonto("Descontos / Débitos") }
            itemsIndexed(descontos) { index, item ->
                PontoItemRow(
                    title = resolveLabel(item.label),
                    value = item.value,
                    color = Color(0xFFFF3B30),
                    icon = getIconForLabel(item.label, item.isNegative),
                    labelKey = item.label,
                    isNegative = true
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }

        item {
            SectionHeaderPonto("Jornada de Trabalho")
            PontoItemRow(
                title = "Jornada Padrão",
                value = espelho.jornada,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Outlined.Schedule,
                labelKey = "label_work_schedule",
                isNegative = false
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

private fun formatPeriodoMiniCard(periodo: String): String {
    val matches = """\d{2}/\d{2}/\d{4}""".toRegex().findAll(periodo).toList()
    if (matches.size >= 2) {
        return "${matches[0].value.take(5)} a ${matches[1].value.take(5)}"
    }
    return periodo
}

@Composable
private fun TimesheetHeaderCard(
    periodText: String,
    userName: String,
    userMatricula: String,
    userCargo: String,
    onEditProfile: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Color(0xFFFF9500),
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "PERÍODO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF9500)
                            )
                            Text(
                                text = formatPeriodoMiniCard(periodText),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Surface(
                    onClick = onOpenHistory,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF5856D6).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFF5856D6).copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Histórico",
                            tint = Color(0xFF5856D6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimesheetInfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SummaryHeroCard(espelho: EspelhoPonto) {
    val workedHours = espelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00"
    val extraHours = espelho.resumoItens.find { it.label.contains("extra", true) }?.value ?: "0:00"
    val nightAllowance = espelho.resumoItens.find { it.label.contains("night", true) }?.value ?: "0:00"

    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            text = "HORAS TRABALHADAS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = workedHours,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-1.5).sp
                            )
                            Text(
                                text = " total",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricChip(
                            title = "H. EXTRAS",
                            value = extraHours,
                            valueColor = Color(0xFF34C759),
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            title = "ADIC. NOTURNO",
                            value = nightAllowance,
                            valueColor = Color(0xFF007AFF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



@Composable
private fun BankHoursCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun EmptyStateCard() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Nenhum espelho selecionado",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Importe um PDF para visualizar o resumo e os detalhes do ponto.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PontoItemRow(
    title: String,
    value: String,
    color: Color,
    icon: ImageVector,
    labelKey: String = "",
    isNegative: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        tint = color, 
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isNegative) "Débito / Desconto" else "Crédito / Ganho",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
            
            AnimatedVisibility(visible = expanded && labelKey.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.what_item_means),
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = getDetalheParaResumoItem(labelKey),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeaderPonto(title: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            letterSpacing = 0.9.sp
        )
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
        confirmButton = {},
        title = null,
        text = {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Histórico de Pontos",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${historico.size} registro(s) disponível(is)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (historico.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhum histórico disponível.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 460.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(historico) { item ->
                                Surface(
                                    onClick = { onSelect(item) },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 1.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
                                                        )
                                                    ),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.periodo,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            ) {
                                                Text(
                                                    text = "Total: ${item.jornadaRealizada}",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDelete(item) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Excluir",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.78f),
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(30.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp
    )
}

@Composable
fun PontoDetailDialog(labelKey: String, value: String, isNegative: Boolean, onDismiss: () -> Unit) {
    val displayLabel = resolveLabel(labelKey)
    val color = if (isNegative) MaterialTheme.colorScheme.error else Color(0xFF34C759)
    val icon = if (isNegative) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { 
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.close), fontWeight = FontWeight.ExtraBold) 
            } 
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = displayLabel, 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = color.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        color.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "VALOR REGISTRADO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = color.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = value,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = color
                        )
                    }
                }
                
                HorizontalDivider(
                    thickness = 1.dp, 
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Column {
                    Text(
                        text = stringResource(R.string.what_item_means),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = getDetalheParaResumoItem(labelKey),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
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
        "label_bh_uniform" -> stringResource(R.string.desc_ponto_bh_uniform)
        "label_bh_uniform_off" -> stringResource(R.string.desc_ponto_bh_uniform_off)
        "label_period_balance" -> stringResource(R.string.desc_ponto_saldo_periodo)
        "label_previous_balance" -> stringResource(R.string.desc_ponto_saldo_anterior)
        "label_work_schedule" -> stringResource(R.string.desc_ponto_jornada)
        else -> {
            val key = label.uppercase().trim()
            when {
                key.contains("SALDO ANTERIOR") -> stringResource(R.string.desc_ponto_saldo_anterior)
                key.contains("CRÉDITO H.E.") -> stringResource(R.string.desc_ponto_credito_he)
                key.contains("SALDO FINAL B.H.") -> stringResource(R.string.desc_ponto_saldo_final)
                key.contains("TOTAL DE DÉBITOS") -> stringResource(R.string.desc_ponto_total_debitos)
                key == "TOTAL DE HORAS TRABALHADAS" -> stringResource(R.string.desc_ponto_total_trabalhadas)
                key == "HORAS FALTAS JUSTIFICADAS" -> stringResource(R.string.desc_ponto_horas_abonadas)
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
                userCargo = "Desenvolvedor",
                onEditProfile = {},
                onSelect = {},
                onOpen = {},
                onRefresh = {}
            )
        }
    }
}
