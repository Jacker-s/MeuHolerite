package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.model.InformeRendimento
import com.jack.meuholerite.parser.InformeParser
import com.jack.meuholerite.ui.FullscreenPdfViewerDialog
import com.jack.meuholerite.ui.LocalPrivacyActive
import com.jack.meuholerite.ui.PrivacyValueText
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.utils.toMoneyDoubleOrZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class InformeInsight(
    val title: String,
    val message: String,
    val legalBasis: String,
    val legalUrl: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private data class InformeSummary(
    val annualTaxable: Double,
    val annualOfficialPrevidencia: Double,
    val annualIrrf: Double,
    val annualThirteenth: Double,
    val annualThirteenthIrrf: Double,
    val annualPlr: Double
)

class InformesActivity : ComponentActivity() {
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
                    InformesScreenContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InformesScreenContent() {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val scope = rememberCoroutineScope()
        val backupManager = remember { BackupManager(context) }
        val storageManager = remember { StorageManager(context) }
        val pdfReader = remember { PdfReader(context) }
        val informeParser = remember { InformeParser() }

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

        val informesEntities by db.informeDao().getAllFlow().collectAsState(initial = emptyList())
        val informes = remember(informesEntities) {
            informesEntities.map { it.toModel() }
        }
        var hasAttemptedRepair by remember { mutableStateOf(false) }

        var pdfToView by remember { mutableStateOf<InformeRendimento?>(null) }
        var informeToDelete by remember { mutableStateOf<InformeRendimento?>(null) }

        LaunchedEffect(informesEntities) {
            if (hasAttemptedRepair) return@LaunchedEffect
            hasAttemptedRepair = true

            withContext(Dispatchers.IO) {
                informesEntities.map { it.toModel() }
                    .filter { shouldRepairInforme(it) }
                    .forEach { informe ->
                        val path = informe.pdfFilePath ?: return@forEach
                        val file = File(path)
                        if (!file.exists()) return@forEach

                        val text = pdfReader.extractTextFromUri(Uri.fromFile(file)) ?: return@forEach
                        val reparsed = informeParser.parse(text).copy(pdfFilePath = path)
                        if (isBetterInformeData(original = informe, candidate = reparsed)) {
                            db.informeDao().insert(reparsed.toEntity(path))
                        }
                    }
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Informes de Rendimentos",
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
                        IconButton(onClick = {
                            val intent = Intent(context, EpaysActivity::class.java).apply {
                                putExtra("startUrl", "https://app.epays.com.br/trabalhador/informes-rendimento")
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Importar do ePays",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    )
                )
            }
        ) { innerPadding ->
            CompositionLocalProvider(LocalPrivacyActive provides isPrivacyActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (informes.isEmpty()) {
                        EmptyInformesState(
                            onImport = {
                                val intent = Intent(context, EpaysActivity::class.java).apply {
                                    putExtra("startUrl", "https://app.epays.com.br/trabalhador/informes-rendimento")
                                }
                                context.startActivity(intent)
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
                        ) {
                            items(informes, key = { it.anoCalendario }) { informe ->
                                InformeItemCard(
                                    informe = informe,
                                    onViewPdf = { pdfToView = informe },
                                    onSharePdf = { sharePdf(informe.pdfFilePath) },
                                    onDelete = { informeToDelete = informe }
                                )
                            }
                        }
                    }

                    // PDF Viewer dialog
                    pdfToView?.let { doc ->
                        doc.pdfFilePath?.let { path ->
                            FullscreenPdfViewerDialog(
                                type = "INFORME",
                                filePath = path,
                                onConfirm = { pdfToView = null },
                                onDismiss = { pdfToView = null }
                            )
                        } ?: run {
                            Toast.makeText(context, "Arquivo PDF não disponível", Toast.LENGTH_SHORT).show()
                            pdfToView = null
                        }
                    }

                    // Delete confirmation dialog
                    informeToDelete?.let { doc ->
                        AlertDialog(
                            onDismissRequest = { informeToDelete = null },
                            title = { Text("Excluir Informe?", fontWeight = FontWeight.Bold) },
                            text = { Text("Deseja realmente excluir o Informe de Rendimentos do ano-calendário ${doc.anoCalendario}?") },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        val toDel = informeToDelete
                                        informeToDelete = null
                                        if (toDel != null) {
                                            scope.launch(Dispatchers.IO) {
                                                db.informeDao().deleteByAno(toDel.anoCalendario)
                                                toDel.pdfFilePath?.let { File(it).delete() }
                                                backupManager.backupData()
                                                
                                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                                                if (account != null) {
                                                    val googleDriveBackupManager = com.jack.meuholerite.utils.GoogleDriveBackupManager(context)
                                                    googleDriveBackupManager.backupNow(account)
                                                }

                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Informe excluído com sucesso", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Excluir", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { informeToDelete = null }) {
                                    Text("Cancelar")
                                }
                            },
                            shape = RoundedCornerShape(22.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyInformesState(onImport: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Nenhum Informe Importado",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Importe seus informes de rendimentos diretamente do portal ePays para ter tudo organizado em um único lugar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onImport,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Importar do ePays", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun InformeItemCard(
        informe: InformeRendimento,
        onViewPdf: () -> Unit,
        onSharePdf: () -> Unit,
        onDelete: () -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val summary = remember(informe) {
            InformeSummary(
                annualTaxable = informe.rendimentosTributaveis.toMoneyDoubleOrZero(),
                annualOfficialPrevidencia = informe.previdenciaOficial.toMoneyDoubleOrZero(),
                annualIrrf = informe.impostoRetido.toMoneyDoubleOrZero(),
                annualThirteenth = informe.decimoTerceiro.toMoneyDoubleOrZero(),
                annualThirteenthIrrf = informe.impostoDecimoTerceiro.toMoneyDoubleOrZero(),
                annualPlr = informe.plr.toMoneyDoubleOrZero()
            )
        }
        val insights = remember(informe) { buildInformeInsights() }

        Surface(
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Assignment, null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ano-Calendário ${informe.anoCalendario}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = informe.nomeFontePagadora.ifEmpty { "Fonte Pagadora não identificada" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (informe.cnpjFontePagadora.isNotEmpty()) {
                            Text(
                                text = "CNPJ: ${informe.cnpjFontePagadora}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InformeMetricCard(
                        label = "TRIBUTÁVEIS",
                        value = "R$ ${informe.rendimentosTributaveis}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    InformeMetricCard(
                        label = "IRRF",
                        value = "R$ ${informe.impostoRetido}",
                        valueColor = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                AssistInfoCard(
                    icon = Icons.Outlined.Info,
                    color = MaterialTheme.colorScheme.primary,
                    title = "Leitura rápida",
                    text = buildQuickSummaryText(summary)
                )

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        Spacer(Modifier.height(16.dp))

                        SectionPill("Dados complementares", MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InformeMetricCard(
                                label = "MÉDIA MENSAL",
                                value = "R$ ${formatBrMoney(summary.annualTaxable / 12.0)}",
                                valueColor = Color(0xFF007AFF),
                                modifier = Modifier.weight(1f)
                            )
                            InformeMetricCard(
                                label = "ALÍQ. EFETIVA IRRF",
                                value = formatPercent(summary.annualIrrf, summary.annualTaxable),
                                valueColor = Color(0xFFFF9500),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InformeMetricCard(
                                label = "13º",
                                value = "R$ ${informe.decimoTerceiro}",
                                valueColor = Color(0xFF34C759),
                                modifier = Modifier.weight(1f)
                            )
                            InformeMetricCard(
                                label = "PLR",
                                value = "R$ ${informe.plr}",
                                valueColor = Color(0xFF5856D6),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        DetailRow("Exercício", informe.exercicio.ifBlank { "Não identificado" })
                        DetailRow("Beneficiário", informe.nomeBeneficiario.ifBlank { "Não identificado" })
                        DetailRow("CPF do beneficiário", informe.cpfBeneficiario.ifBlank { "Não identificado" })
                        DetailRow("CNPJ da fonte pagadora", informe.cnpjFontePagadora.ifBlank { "Não identificado" })
                        DetailRow("Previdência Oficial", "R$ ${informe.previdenciaOficial}")
                        DetailRow("13º Salário", "R$ ${informe.decimoTerceiro}")
                        DetailRow("Imposto 13º Salário", "R$ ${informe.impostoDecimoTerceiro}")
                        DetailRow("PLR", "R$ ${informe.plr}")

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        Spacer(Modifier.height(14.dp))

                        SectionPill("Sugestões com base legal", Color(0xFF34C759))
                        Spacer(Modifier.height(10.dp))

                        insights.forEach { insight ->
                            LegalInsightCard(insight = insight) {
                                openUrl(context, insight.legalUrl)
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                onClick = onViewPdf,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Visualizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = onSharePdf,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Compartilhar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = onDelete,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Excluir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun InformeMetricCard(
        label: String,
        value: String,
        valueColor: Color,
        modifier: Modifier = Modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(4.dp))
                PrivacyValueText(
                    value = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = valueColor
                    )
                )
            }
        }
    }

    @Composable
    fun SectionPill(text: String, color: Color) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = color.copy(alpha = 0.10f)
        ) {
            Text(
                text = text.uppercase(),
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }

    @Composable
    fun AssistInfoCard(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        color: Color,
        title: String,
        text: String
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun LegalInsightCard(
        insight: InformeInsight,
        onOpenSource: () -> Unit
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = insight.color.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, insight.color.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(insight.color.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(insight.icon, null, tint = insight.color, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(insight.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(8.dp))
                Text(insight.message, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Base legal: ${insight.legalBasis}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = insight.color)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onOpenSource, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir fonte oficial", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun DetailRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrivacyValueText(
                value = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }

    private fun buildQuickSummaryText(summary: InformeSummary): String {
        val monthlyAverage = summary.annualTaxable / 12.0
        val effectiveRate = formatPercent(summary.annualIrrf, summary.annualTaxable)
        return "Média mensal tributável de R$ ${formatBrMoney(monthlyAverage)} com IRRF efetivo de $effectiveRate no ano. " +
            "Use este informe para conferir a declaração do IR e comparar 13º, INSS oficial e eventual PLR."
    }

    private fun buildInformeInsights(): List<InformeInsight> {
        return listOf(
            InformeInsight(
                title = "Confira a fonte pagadora e o arquivo",
                message = "A fonte pagadora deve fornecer o comprovante de rendimentos ao beneficiário. Guarde este PDF para conferência e eventual comprovação.",
                legalBasis = "Lei 7.713/1988, art. 7º, § 3º; IN RFB 1.215/2011",
                legalUrl = "https://www.planalto.gov.br/ccivil_03/LEIS/L7713.htm",
                color = Color(0xFF007AFF),
                icon = Icons.Outlined.VerifiedUser
            ),
            InformeInsight(
                title = "IRRF pode abater no ajuste anual",
                message = "O IRRF sobre rendimentos do trabalho normalmente funciona como antecipação do imposto apurado na declaração anual. Vale conferir se os valores batem com o programa da Receita.",
                legalBasis = "Lei 7.713/1988, art. 12-A, § 6º; Manual IRPF da Receita",
                legalUrl = "https://www.gov.br/receitafederal/pt-br/assuntos/meu-imposto-de-renda/preenchimento/manual-mir/rendimentos/rendimentos-do-trabalho",
                color = Color(0xFF007AFF),
                icon = Icons.Outlined.AccountBalance
            ),
            InformeInsight(
                title = "Previdência oficial entra nas deduções legais",
                message = "Contribuições à previdência oficial podem ser consideradas nas deduções legais da declaração. Se você usar o modelo simplificado, essa dedução é substituída pelo desconto simplificado.",
                legalBasis = "Receita Federal, \"Descontos na declaração\"",
                legalUrl = "https://www.gov.br/receitafederal/pt-br/assuntos/meu-imposto-de-renda/preenchimento/regimes",
                color = Color(0xFF34C759),
                icon = Icons.Outlined.HealthAndSafety
            ),
            InformeInsight(
                title = "13º é tributado em separado",
                message = "O 13º salário e o respectivo IRRF têm tratamento próprio e tributação exclusiva na fonte, em separado dos demais rendimentos do mês.",
                legalBasis = "IN RFB 1.500/2014, subseção da Gratificação Natalina",
                legalUrl = "https://normas.receita.fazenda.gov.br/sijut2consulta/link.action?idAto=57670&visao=anotado",
                color = Color(0xFFFF9500),
                icon = Icons.Outlined.EventAvailable
            ),
            InformeInsight(
                title = "PLR não se mistura ao salário comum",
                message = "A participação nos lucros ou resultados paga por pessoa jurídica é, em regra, rendimento sujeito à tributação exclusiva/definitiva, separado dos rendimentos tributáveis comuns.",
                legalBasis = "Lei 10.101/2000, art. 3º, §§ 6º a 8º; Manual IRPF da Receita",
                legalUrl = "https://www.planalto.gov.br/ccivil_03/leis/l10101.htm",
                color = Color(0xFF5856D6),
                icon = Icons.Outlined.Workspaces
            )
        )
    }

    private fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir a fonte oficial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatBrMoney(value: Double): String {
        return String.format(java.util.Locale("pt", "BR"), "%,.2f", value)
    }

    private fun formatPercent(part: Double, total: Double): String {
        if (total <= 0.0) return "0%"
        return "${((part / total) * 100).toInt()}%"
    }

    private fun shouldRepairInforme(informe: InformeRendimento): Boolean {
        val hasMissingIdentity = informe.cpfBeneficiario.isBlank() ||
            informe.cnpjFontePagadora.isBlank() ||
            informe.nomeFontePagadora.isBlank() ||
            informe.nomeBeneficiario.isBlank() ||
            informe.nomeBeneficiario.contains("Imposto sobre a Renda da Pessoa Física", true)

        val suspiciousThirteenth = informe.decimoTerceiro.toMoneyDoubleOrZero() <= 0.0 &&
            informe.impostoDecimoTerceiro.toMoneyDoubleOrZero() > 0.0

        val suspiciousIrrf = informe.impostoRetido.toMoneyDoubleOrZero() <= 0.0 &&
            informe.rendimentosTributaveis.toMoneyDoubleOrZero() > 0.0

        return hasMissingIdentity || suspiciousThirteenth || suspiciousIrrf
    }

    private fun isBetterInformeData(original: InformeRendimento, candidate: InformeRendimento): Boolean {
        val originalScore = informeQualityScore(original)
        val candidateScore = informeQualityScore(candidate)
        return candidateScore > originalScore
    }

    private fun informeQualityScore(informe: InformeRendimento): Int {
        var score = 0
        if (informe.nomeFontePagadora.isNotBlank()) score += 2
        if (informe.cnpjFontePagadora.isNotBlank()) score += 2
        if (informe.nomeBeneficiario.isNotBlank() && !informe.nomeBeneficiario.contains("Imposto sobre a Renda da Pessoa Física", true)) score += 2
        if (informe.cpfBeneficiario.isNotBlank()) score += 2
        if (informe.decimoTerceiro.toMoneyDoubleOrZero() > 0.0) score += 1
        if (informe.impostoRetido.toMoneyDoubleOrZero() > 0.0) score += 1
        return score
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
            startActivity(Intent.createChooser(intentShare, "Exportar Informe"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
