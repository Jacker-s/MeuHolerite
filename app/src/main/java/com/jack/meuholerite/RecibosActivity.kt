 package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.DeductionDetailDialog
import com.jack.meuholerite.ui.NativeAdSize
import com.jack.meuholerite.ui.NativeInlineAd
import com.jack.meuholerite.ui.PremiumEvolutionChart
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.formatBrMoney
import com.jack.meuholerite.utils.toMoneyDoubleOrZero
import com.jack.meuholerite.utils.extractStartDateForRecibo
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.utils.extractStartDateForRecibo
import com.jack.meuholerite.ui.LocalPrivacyActive
import com.jack.meuholerite.ui.PrivacyValueText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

 class RecibosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageManager = StorageManager(this)

        setContent {
            val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme =
                if (storageManager.hasDarkModeSet()) storageManager.isDarkMode() else systemInDarkTheme

            MeuHoleriteTheme(darkTheme = useDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecibosScreenContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RecibosScreenContent() {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val gson = remember { Gson() }
        val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
        val scope = rememberCoroutineScope()
        val backupManager = remember { BackupManager(context) }
        val storageManager = remember { StorageManager(context) }

        var isPrivacyActive by remember { mutableStateOf(storageManager.isHideValuesEnabled()) }

        @Suppress("DEPRECATION")
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isPrivacyActive = storageManager.isHideValuesEnabled()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
        var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
        var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }

        suspend fun refreshData() {
            withContext(Dispatchers.IO) {
                val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
                val newest = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.firstOrNull()
                withContext(Dispatchers.Main) { selectedRecibo = newest }
            }
        }

        LaunchedEffect(Unit) { refreshData() }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Meus Recibos",
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
                        if (selectedRecibo != null) {
                            IconButton(onClick = { sharePdf(selectedRecibo?.pdfFilePath) }) {
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
            CompositionLocalProvider(LocalPrivacyActive provides isPrivacyActive) {
                Box(modifier = Modifier.padding(innerPadding)) {
                    ReceiptsScreen(
                    recibo = selectedRecibo,
                    db = db,
                    gson = gson,
                    userName = userName,
                    userMatricula = userMatricula,
                    onEditProfile = { /* Not applicable here */ },
                    onOpen = { sharePdf(it) },
                    onSelect = { selectedRecibo = it },
                    onRefresh = { scope.launch { refreshData() } }
                )
            }
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
            startActivity(Intent.createChooser(intentShare, "Exportar Holerite"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ReceiptsScreen(
    recibo: ReciboPagamento?,
    db: AppDatabase,
    gson: Gson,
    userName: String,
    userMatricula: String,
    onEditProfile: () -> Unit,
    onOpen: (String?) -> Unit,
    onSelect: (ReciboPagamento) -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }
    val adsRemovedState by AdsDataStore.isAdsRemovedFlow(context).collectAsState(initial = false)

    val recibosEntities by db.reciboDao().getAllFlow().collectAsState(initial = emptyList())
    val recibos = remember(recibosEntities) {
        recibosEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDateForRecibo() }
    }
    val proventos = recibo?.proventos.orEmpty()
    val descontos = recibo?.descontos.orEmpty()
    val adInsertPositions = remember(proventos, descontos) {
        buildAdInsertPositions(
            totalItems = proventos.size + descontos.size,
            maxAds = 2
        )
    }

    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        ReceiptHistoryDialog(
            recibos = recibos,
            onDismiss = { showHistory = false },
            onSelect = { onSelect(it); showHistory = false },
            onDelete = {
                scope.launch {
                    db.reciboDao().deleteByPeriodo(it.periodo)
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
            ReceiptsHeaderCard(
                periodText = recibo?.periodo ?: stringResource(R.string.select_pdf),
                userName = userName,
                userMatricula = userMatricula,
                reciboCargo = recibo?.cargo,
                onOpenHistory = { showHistory = true }
            )
        }

        if (recibo == null) {
            item { ReceiptsEmptyStateCard() }
            return@LazyColumn
        }

        item { ReceiptHeroCard(recibo = recibo) }

        item { SalaryComparisonCard(current = recibo, all = recibos) }

        if (recibos.size > 1) {
            item {
                SectionHeaderRecibo("Evolução Salarial")
                SalaryEvolutionChart(history = recibos.reversed())
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            SectionHeaderColored(
                title = stringResource(R.string.earnings),
                color = Color(0xFF34C759)
            )
        }

        itemsIndexed(proventos) { index, item ->
            ReceiptItemCardRow(item = item, accentColor = Color(0xFF34C759))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            val globalPosition = index + 1
            if (!adsRemovedState && globalPosition in adInsertPositions) {
                NativeInlineAd(
                    adUnitId = "ca-app-pub-7931782163570852/1526069738",
                    size = NativeAdSize.Compact
                )
            }
        }

        item {
            SectionHeaderColored(
                title = stringResource(R.string.deductions),
                color = Color(0xFFFF3B30)
            )
        }

        itemsIndexed(descontos) { index, item ->
            ReceiptItemCardRow(item = item, accentColor = Color(0xFFFF3B30))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            val globalPosition = proventos.size + index + 1
            if (!adsRemovedState && globalPosition in adInsertPositions) {
                NativeInlineAd(
                    adUnitId = "ca-app-pub-7931782163570852/1526069738",
                    size = NativeAdSize.Compact
                )
            }
        }

        item {
            BasesAndTaxesCard(recibo = recibo)
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

private fun buildAdInsertPositions(totalItems: Int, maxAds: Int): Set<Int> {
    if (totalItems < 2 || maxAds <= 0) return emptySet()

    return (1..maxAds)
        .map { slot ->
            ((totalItems.toFloat() * slot) / (maxAds + 1)).toInt().coerceIn(1, totalItems - 1)
        }
        .toSet()
}

@Composable
private fun ReceiptsHeaderCard(
    periodText: String,
    userName: String,
    userMatricula: String,
    reciboCargo: String? = null,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
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
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = periodText,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOf(
                        userName.takeIf { it.isNotBlank() },
                        userMatricula.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" • ")

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!reciboCargo.isNullOrBlank()) {
                        Text(
                            text = reciboCargo.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "Histórico", tint = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val intent = Intent(context, InformesActivity::class.java)
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = "Informe de Rendimentos",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Informe de Rendimentos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                TextButton(
                    onClick = onOpenHistory,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Histórico", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ReceiptHeroCard(recibo: ReciboPagamento) {
    val proventosVal = recibo.totalProventos.toMoneyDoubleOrZero().coerceAtLeast(1.0)
    val descontosVal = recibo.totalDescontos.toMoneyDoubleOrZero().coerceAtLeast(0.0)
    val ratio = (descontosVal / proventosVal).coerceIn(0.0, 1.0).toFloat()

    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "VALOR LÍQUIDO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.1.sp
            )

            PrivacyValueText(
                value = "R$ ${recibo.valorLiquido}",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1).sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricChipMoney(
                    title = "PROVENTOS",
                    value = "R$ ${recibo.totalProventos}",
                    valueColor = Color(0xFF34C759),
                    modifier = Modifier.weight(1f)
                )
                MetricChipMoney(
                    title = "DESCONTOS",
                    value = "R$ ${recibo.totalDescontos}",
                    valueColor = Color(0xFFFF3B30),
                    modifier = Modifier.weight(1f)
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = Color(0xFFFF3B30),
                    trackColor = Color(0xFF34C759)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Retenção: ${(ratio * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Líquido: ${((1 - ratio) * 100).toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MetricChipMoney(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            PrivacyValueText(
                value = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SalaryComparisonCard(current: ReciboPagamento?, all: List<ReciboPagamento>) {
    if (current == null || all.size < 2) return

    val currentIndex = all.indexOf(current)
    if (currentIndex == -1 || currentIndex >= all.size - 1) return

    val previous = all[currentIndex + 1]

    val currentVal = current.valorLiquido.toMoneyDoubleOrZero()
    val previousVal = previous.valorLiquido.toMoneyDoubleOrZero()
    val diff = currentVal - previousVal

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (diff >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown
            val color = if (diff >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                PrivacyValueText(
                    value = if (diff >= 0) "Aumento de R$ ${diff.formatBrMoney()}"
                            else "Redução de R$ ${(-diff).formatBrMoney()}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Comparado ao mês anterior (${previous.periodo})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SalaryEvolutionChart(history: List<ReciboPagamento>) {
    PremiumEvolutionChart(
        history = history,
        showGross = false,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SectionHeaderColored(title: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            letterSpacing = 0.9.sp
        )
    }
}

@Composable
fun SectionHeaderRecibo(title: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
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
fun ReceiptItemRow(item: ReciboItem, color: Color, onClick: () -> Unit) {
    // Mantive sua função original (não removi), mas abaixo eu uso a versão "card" pra ficar premium.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.descricao, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (item.referencia.isNotEmpty()) {
                Text(
                    "Referência: ${item.referencia}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        PrivacyValueText(
            value = "R$ ${item.valor}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ReceiptItemCardRow(
    item: ReciboItem,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val isProvento = accentColor == Color(0xFF34C759)

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.5.dp,
        interactionSource = interaction,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = if (accentColor == Color(0xFFFF3B30)) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp
                    Icon(icon, contentDescription = null, tint = accentColor)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.descricao,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.referencia.isNotEmpty()) {
                        Text(
                            text = "Ref: ${item.referencia}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                PrivacyValueText(
                    value = "R$ ${item.valor}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accentColor.copy(alpha = 0.04f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (isProvento) "O que é este ganho?" else "O que é este desconto?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = com.jack.meuholerite.ui.getDetalheParaItem(item.descricao, isProvento),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BaseInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        PrivacyValueText(
            value = value,
            fontSize = 14.sp, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BasesAndTaxesCard(recibo: ReciboPagamento) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "BASES E TRIBUTOS",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))

            BaseInfoRow("Base INSS", "R$ ${recibo.baseInss.ifBlank { "0,00" }}")
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            BaseInfoRow("Base IRPF", "R$ ${recibo.baseIrpf.ifBlank { "0,00" }}")
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            BaseInfoRow("Base FGTS do Mês", "R$ ${recibo.fgtsMes.ifBlank { "0,00" }}")
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            BaseInfoRow("Valor FGTS (8%)", "R$ ${recibo.valorFgts.ifBlank { "0,00" }}")
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
        }
    }
}

@Composable
private fun ReceiptsEmptyStateCard() {
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
                text = "Nenhum holerite selecionado",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Importe um PDF para ver o resumo e os detalhes do holerite.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ReceiptHistoryDialog(
    recibos: List<ReciboPagamento>,
    onDismiss: () -> Unit,
    onSelect: (ReciboPagamento) -> Unit,
    onDelete: (ReciboPagamento) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
        text = {
            if (recibos.isEmpty()) {
                Text("Nenhum histórico disponível.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(recibos) { recibo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(recibo) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    recibo.periodo,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                PrivacyValueText(
                                    value = "Líquido: R$ ${recibo.valorLiquido}",
                                    color = Color(0xFF34C759),
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = { onDelete(recibo) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Excluir",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    }
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}


