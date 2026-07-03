package com.jack.meuholerite

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.FinanceExpenseEntity
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.FinancePdfGenerator
import com.jack.meuholerite.utils.formatBrMoney
import com.jack.meuholerite.utils.extractStartDateForRecibo
import com.jack.meuholerite.utils.extractStartDate
import com.jack.meuholerite.utils.calcularProximoPagamento
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.utils.toMoneyDoubleOrZero
import androidx.core.content.FileProvider
import android.net.Uri
import com.jack.meuholerite.ui.LocalPrivacyActive
import com.jack.meuholerite.ui.PrivacyValueText
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ExpenseCategory(val id: String, val labelRes: Int, val icon: ImageVector, val color: Color) {
    HOUSING("HOUSING", R.string.cat_housing, Icons.Outlined.Home, Color(0xFF007AFF)),
    FOOD("FOOD", R.string.cat_food, Icons.Outlined.Restaurant, Color(0xFFFF9500)),
    TRANSPORT("TRANSPORT", R.string.cat_transport, Icons.Outlined.DirectionsCar, Color(0xFF34C759)),
    LEISURE("LEISURE", R.string.cat_leisure, Icons.Outlined.Celebration, Color(0xFF5856D6)),
    HEALTH("HEALTH", R.string.cat_health, Icons.Outlined.MedicalServices, Color(0xFFFF3B30)),
    EDUCATION("EDUCATION", R.string.cat_education, Icons.Outlined.School, Color(0xFF5AC8FA)),
    OTHERS("OTHERS", R.string.cat_others, Icons.Outlined.Category, Color(0xFF8E8E93))
}

class FinanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                FinanceScreenContent { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreenContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    val storage = remember { StorageManager(context) }
    val activity = context as? android.app.Activity

    var latestRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    var latestEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
    val expenses by db.financeExpenseDao().getAllFlow().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showLoanSimulator by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf<com.jack.meuholerite.database.FinanceGoalEntity?>(null) }
    var isPrivacyActive by remember { mutableStateOf(storage.isHideValuesEnabled()) }
    
    val goals by db.financeGoalDao().getAllFlow().collectAsState(initial = emptyList())
    val debts by db.financeDebtDao().getAllFlow().collectAsState(initial = emptyList())


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPrivacyActive = storage.isHideValuesEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val list = db.reciboDao().getAll().map { it.toModel(gson) }
                if (list.isNotEmpty()) {
                    latestRecibo = list.sortedByDescending { it.periodo.extractStartDateForRecibo() }.firstOrNull()
                }
                val pontos = db.espelhoDao().getAll().map { it.toModel(gson) }
                if (pontos.isNotEmpty()) {
                    latestEspelho = pontos.sortedByDescending { it.periodo.extractStartDate() }.firstOrNull()
                }
            }
        }
    }

    val netSalary = latestRecibo?.valorLiquido?.toAmountDouble() ?: 0.0
    val totalExpenses = expenses.sumOf { it.value }
    val totalDebtsMonthly = debts.filter { it.paidInstallments < it.totalInstallments }.sumOf { it.monthlyValue }
    val totalDeductions = totalExpenses + totalDebtsMonthly
    val remaining = netSalary - totalDeductions
    
    // Cálculo de Gasto Diário Disponível
    val calendar = java.util.Calendar.getInstance()
    val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val daysLeft = (daysInMonth - dayOfMonth).coerceAtLeast(1)
    val dailyBudget = if (remaining > 0) remaining / daysLeft else 0.0

    val worked = remember(latestEspelho) {
        latestEspelho?.resumoItens?.find { it.label == "label_worked_hours" }?.value ?: "0:00"
    }
    val hasAbsences = latestEspelho?.hasAbsences == true
    val absCount = latestEspelho?.diasFaltas?.size ?: 0

    val proventosVal = remember(latestRecibo) { latestRecibo?.totalProventos?.toMoneyDoubleOrZero() ?: 0.0 }
    val descontosVal = remember(latestRecibo) { latestRecibo?.totalDescontos?.toMoneyDoubleOrZero() ?: 0.0 }
    val retentionRatio = remember(proventosVal, descontosVal) {
        val base = if (proventosVal <= 0.0) 1.0 else proventosVal
        (descontosVal / base).coerceIn(0.0, 1.0).toFloat()
    }
    val proximoPagamento = remember { com.jack.meuholerite.utils.calcularProximoPagamento() }

    val smartAlerts = remember(
        hasAbsences, absCount, totalDeductions, netSalary, retentionRatio, remaining, proximoPagamento.diasRestantes
    ) {
        buildList {
            if (hasAbsences) {
                add(SmartAlert(
                    title = "Faltas detectadas",
                    message = "$absCount registro(s) podem impactar o próximo fechamento do ponto.",
                    color = Color(0xFFFF3B30),
                    icon = Icons.Filled.Warning
                ))
            }
            if (netSalary > 0.0 && totalDeductions >= netSalary * 0.30) {
                add(SmartAlert(
                    title = "Salário comprometido",
                    message = "Seus gastos fixos já consomem ${((totalDeductions / netSalary) * 100).toInt()}% do líquido.",
                    color = Color(0xFFFF9500),
                    icon = Icons.Outlined.AccountBalanceWallet
                ))
            }
            if (retentionRatio >= 0.35f) {
                add(SmartAlert(
                    title = "Descontos acima do normal",
                    message = "Os descontos deste holerite estão em ${(retentionRatio * 100).toInt()}% do bruto.",
                    color = Color(0xFF5856D6),
                    icon = Icons.Outlined.TrendingDown
                ))
            }
            if (proximoPagamento.diasRestantes in 0..3) {
                add(SmartAlert(
                    title = "Pagamento próximo",
                    message = "Seu próximo pagamento está previsto para ${proximoPagamento.dataFormatada}.",
                    color = Color(0xFF34C759),
                    icon = Icons.Outlined.DateRange
                ))
            }
            if (remaining < 0) {
                add(SmartAlert(
                    title = "Saldo projetado negativo",
                    message = "Pelas contas atuais, faltariam R$ ${kotlin.math.abs(remaining).formatBrMoney()} para fechar o mês.",
                    color = Color(0xFFFF3B30),
                    icon = Icons.Outlined.ErrorOutline
                ))
            }
        }.take(3)
    }

    fun exportFinanceReport() {
        val pdfGenerator = FinancePdfGenerator(context)
        val file = pdfGenerator.generateFinanceReport(netSalary, expenses, goals, debts)
        if (file != null) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório"))
        } else {
            android.widget.Toast.makeText(context, "Erro ao gerar PDF", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun showAdThenExportFinanceReport() {
        scope.launch {
            val activity = context as? android.app.Activity
            if (activity != null && AdsDataStore.canShowIntervalAd(context)) {
                RewardedInterstitialAdManager.showAd(activity) {
                    scope.launch {
                        AdsDataStore.incrementAdsShown(context)
                        AdsDataStore.markIntervalAdShown(context)
                        exportFinanceReport()
                    }
                }
            } else {
                exportFinanceReport()
            }
        }
    }

    fun exitScreen() {
        if (activity == null) {
            onBack()
            return
        }

        scope.launch {
            if (AdsDataStore.canShowIntervalAd(activity)) {
                RewardedInterstitialAdManager.showAd(activity) {
                    scope.launch {
                        AdsDataStore.incrementAdsShown(activity)
                        AdsDataStore.markIntervalAdShown(activity)
                        onBack()
                    }
                }
            } else {
                onBack()
            }
        }
    }

    BackHandler(onBack = ::exitScreen)

    CompositionLocalProvider(LocalPrivacyActive provides isPrivacyActive) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "FINANÇAS", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 14.sp, 
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = ::exitScreen) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAdThenExportFinanceReport() }) {
                            Icon(Icons.Outlined.PictureAsPdf, "Exportar PDF", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(onClick = { 
                            isPrivacyActive = !isPrivacyActive 
                            storage.setHideValues(isPrivacyActive)
                        }) {
                            Icon(
                                if (isPrivacyActive) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Outlined.Add, modifier = Modifier.size(24.dp), contentDescription = null)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->


            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    PremiumBalanceCard(remaining, netSalary, totalDeductions, dailyBudget)
                }

                item {
                    val remainingFixed = expenses.filter { it.isFixed }.sumOf { it.value }
                    val projection = netSalary - totalDeductions
                    
                    Spacer(Modifier.height(24.dp))
                    BalanceProjectionCard(projection, netSalary)
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    SmartTipCard(expenses)
                }

                if (smartAlerts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        SectionHeader("Alertas inteligentes")
                    }
                    items(smartAlerts) { alert ->
                        SmartAlertCard(alert = alert)
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    SavingsGoalsSection(
                        goals = goals, 
                        onAdd = { showAddGoalDialog = true },
                        onDelete = { goal ->
                            scope.launch { 
                                db.financeGoalDao().delete(goal)
                                backupManager.backupData()
                            }
                        },
                        onAdjust = { goal -> showAdjustDialog = goal }
                    )
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    DebtsSection(
                        debts = debts,
                        onAdd = { showAddDebtDialog = true },
                        onSimulate = { showLoanSimulator = true },
                        onDelete = { debt: com.jack.meuholerite.database.FinanceDebtEntity ->
                            scope.launch { 
                                db.financeDebtDao().delete(debt)
                                backupManager.backupData()
                            }
                        },
                        onPayInstallment = { debt: com.jack.meuholerite.database.FinanceDebtEntity ->
                            if (debt.paidInstallments < debt.totalInstallments) {
                                val updated = debt.copy(
                                    paidInstallments = debt.paidInstallments + 1,
                                    remainingAmount = (debt.remainingAmount - debt.monthlyValue).coerceAtLeast(0.0)
                                )
                                scope.launch { 
                                    db.financeDebtDao().update(updated)
                                    backupManager.backupData()
                                }
                            }
                        }
                    )
                }

                item {
                    Spacer(Modifier.height(32.dp))
                    CategoryInsightsSection(expenses)
                }




                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader("MINHAS DESPESAS")

                    }
                }

                if (expenses.isEmpty()) {
                    item {
                        EmptyStateView()
                    }
                } else {
                    items(expenses) { expense ->
                        ExpenseItemCardPremium(expense) {
                            scope.launch {
                                db.financeExpenseDao().delete(expense)
                                backupManager.backupData()
                                context.sendBroadcast(Intent("com.jack.meuholerite.UPDATE_WIDGET"))
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onSave = { desc, value, category, isFixed ->
                    scope.launch {
                        db.financeExpenseDao().insert(
                            FinanceExpenseEntity(
                                description = desc,
                                value = value,
                                category = category,
                                isFixed = isFixed
                            )
                        )
                        backupManager.backupData()
                        context.sendBroadcast(Intent("com.jack.meuholerite.UPDATE_WIDGET"))
                    }
                    showAddDialog = false
                }
            )
        }

        if (showAddGoalDialog) {
            AddGoalDialog(
                onDismiss = { showAddGoalDialog = false },
                onSave = { title, desc, target, current ->
                    scope.launch {
                        db.financeGoalDao().insert(
                            com.jack.meuholerite.database.FinanceGoalEntity(
                                title = title,
                                description = desc,
                                targetAmount = target,
                                currentAmount = current
                            )
                        )
                        backupManager.backupData()
                    }
                    showAddGoalDialog = false
                }
            )
        }

        if (showAdjustDialog != null) {
            AdjustGoalDialog(
                goal = showAdjustDialog!!,
                onDismiss = { showAdjustDialog = null },
                onSave = { updatedGoal ->
                    scope.launch {
                        db.financeGoalDao().update(updatedGoal)
                        backupManager.backupData()
                        showAdjustDialog = null
                    }
                }
            )
        }

        if (showAddDebtDialog) {
            AddDebtDialog(
                onDismiss = { showAddDebtDialog = false },
                onSave = { description, total, installments, paid, monthly, interest, dueDate ->
                    scope.launch {
                        val remaining = (installments - paid) * monthly
                        db.financeDebtDao().insert(
                            com.jack.meuholerite.database.FinanceDebtEntity(
                                description = description,
                                totalAmount = total,
                                remainingAmount = remaining,
                                totalInstallments = installments,
                                paidInstallments = paid,
                                monthlyValue = monthly,
                                interestRate = interest,
                                dueDate = dueDate
                            )
                        )
                        backupManager.backupData()
                    }
                    showAddDebtDialog = false
                }
            )
        }

        if (showLoanSimulator) {
            LoanSimulatorDialog(onDismiss = { showLoanSimulator = false })
        }


    }
}

@Composable
fun PremiumBalanceCard(remaining: Double, income: Double, spent: Double, daily: Double) {
    val progress = if (income > 0) (spent / income).coerceIn(0.0, 1.0).toFloat() else 0f
    val healthColor = when {
        remaining < 0 -> Color(0xFFFF3B30)
        progress < 0.5f -> Color(0xFF34C759)
        progress < 0.8f -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Blur/Glow effect simulation
        Box(modifier = Modifier.fillMaxWidth()) {
            // Glow layer
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 20.dp)
                    .offset(y = 10.dp),
                shape = RoundedCornerShape(32.dp),
                color = healthColor.copy(alpha = 0.25f),
                shadowElevation = 0.dp
            ) {}

            // Main Card (Glassmorphism inspired)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, healthColor.copy(alpha = 0.15f)),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    healthColor.copy(alpha = 0.08f),
                                    healthColor.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                "SALDO DISPONÍVEL", 
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            PrivacyValueText(
                                value = "R$ ${String.format("%,.2f", remaining)}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.5).sp
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BalanceMiniInfo("Recebido", income, Color(0xFF10B981).copy(alpha = 0.1f), Color(0xFF10B981))
                        BalanceMiniInfo("Gastos", spent, Color(0xFFEF4444).copy(alpha = 0.1f), Color(0xFFEF4444))
                    }

                    Spacer(Modifier.height(24.dp))
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LIMITE DE GASTO", 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                "${(progress * 100).roundToInt()}%", 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Black,
                                color = healthColor
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = healthColor,
                            trackColor = healthColor.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.BalanceMiniInfo(label: String, value: Double, bgColor: Color, tint: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(0.5.dp, tint.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = tint.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
            PrivacyValueText(value = "R$ ${String.format("%,.2f", value)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryInsightsSection(expenses: List<FinanceExpenseEntity>) {
    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { exp -> exp.value } }
        .toList()
        .sortedByDescending { it.second }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader("ONDE VOCÊ GASTA")
        if (categoryTotals.isEmpty()) {
            Text("Adicione despesas para ver insights.", fontSize = 13.sp, color = Color.Gray)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(categoryTotals) { (catId, total) ->
                    val category = ExpenseCategory.entries.find { it.id == catId } ?: ExpenseCategory.OTHERS
                    CategoryInsightCard(category, total)
                }
            }
        }
    }
}

@Composable
fun CategoryInsightCard(category: ExpenseCategory, total: Double) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, category.color.copy(alpha = 0.08f)),
        modifier = Modifier
            .width(130.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = {}),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(category.color.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(category.color.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, null, tint = category.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(category.labelRes), 
                fontWeight = FontWeight.Bold, 
                fontSize = 11.sp, 
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                letterSpacing = 0.5.sp
            )
            PrivacyValueText(
                value = "R$ ${String.format("%,.0f", total)}", 
                fontWeight = FontWeight.Black, 
                fontSize = 17.sp, 
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ExpenseItemCardPremium(expense: FinanceExpenseEntity, onDelete: () -> Unit) {
    val category = ExpenseCategory.entries.find { it.id == expense.category } ?: ExpenseCategory.OTHERS
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(category.color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, null, tint = category.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(category.labelRes).uppercase(), 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Black,
                    color = category.color.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                PrivacyValueText(
                    value = "R$ ${String.format("%,.2f", expense.value)}",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    letterSpacing = (-0.5).sp
                )
                if (expense.isFixed) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "FIXA", 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Black, 
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, null, tint = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(40.dp), tint = Color.LightGray)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_expenses), fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("Toque no + para organizar suas finanças.", fontSize = 12.sp, color = Color.LightGray)
    }
}


@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (String, Double, String, Boolean) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var valueStr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.OTHERS) }
    var isFixed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Despesa", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("O que você comprou?") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = valueStr,
                    onValueChange = { valueStr = it.replace(",", ".") },
                    label = { Text("Quanto custou?") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    prefix = { Text("R$ ", fontWeight = FontWeight.Bold) }
                )
                
                Text("Escolha a Categoria", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ExpenseCategory.entries) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) category.color else category.color.copy(alpha = 0.1f),
                            contentColor = if (isSelected) Color.White else category.color
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(category.icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(category.labelRes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Surface(
                    onClick = { isFixed = !isFixed },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFixed) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isFixed) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Checkbox(checked = isFixed, onCheckedChange = { isFixed = it })
                        Text("Esta é uma despesa fixa mensal", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = valueStr.toDoubleOrNull() ?: 0.0
                    if (desc.isNotBlank() && v > 0) {
                        onSave(desc, v, selectedCategory.id, isFixed)
                    }
                },
                enabled = desc.isNotBlank() && valueStr.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ADICIONAR DESPESA", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}



@Composable
fun BalanceProjectionCard(projectedAmount: Double, netSalary: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoGraph, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "PREVISÃO FIM DO MÊS", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                PrivacyValueText(
                    value = "R$ ${String.format("%,.2f", projectedAmount)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Baseado em suas despesas fixas e ganhos", 
                    fontSize = 10.sp, 
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SavingsGoalsSection(
    goals: List<com.jack.meuholerite.database.FinanceGoalEntity>,
    onAdd: () -> Unit,
    onDelete: (com.jack.meuholerite.database.FinanceGoalEntity) -> Unit,
    onAdjust: (com.jack.meuholerite.database.FinanceGoalEntity) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("MINHAS METAS")
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nova Meta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (goals.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Text(
                    "Nenhuma meta cadastrada.\nComece a planejar seus sonhos!",
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(goals) { goal ->
                    GoalCard(
                        goal = goal, 
                        onDelete = { onDelete(goal) },
                        onAdjust = { onAdjust(goal) }
                    )
                }
            }
        }
    }
}

@Composable
fun GoalCard(
    goal: com.jack.meuholerite.database.FinanceGoalEntity, 
    onDelete: () -> Unit,
    onAdjust: () -> Unit
) {
    val progress = (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    val color = remember(goal.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(goal.colorHex.ifBlank { "#10B981" }))
        } catch (e: Exception) {
            Color(0xFF10B981)
        }
    }
    
    Surface(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.1f)),
        shadowElevation = 2.dp,
        onClick = onAdjust
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    drawArc(
                        color = color.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(12.dp))
            Text(goal.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (goal.description.isNotBlank()) {
                Text(goal.description, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            PrivacyValueText("R$ ${String.format("%,.0f", goal.targetAmount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
            
            Spacer(Modifier.height(8.dp))
            
            // Botão de Ajuste Rápido
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "AJUSTAR", 
                    modifier = Modifier.padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
        }
    }
}

@Composable
fun AddGoalDialog(onDismiss: () -> Unit, onSave: (String, String, Double, Double) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetStr by remember { mutableStateOf("") }
    var currentStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Meta Financeira", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome da Meta (ex: Viagem)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = targetStr,
                    onValueChange = { targetStr = it.replace(",", ".") },
                    label = { Text("Valor Total Objetivo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    prefix = { Text("R$ ", fontWeight = FontWeight.Bold) }
                )
                OutlinedTextField(
                    value = currentStr,
                    onValueChange = { currentStr = it.replace(",", ".") },
                    label = { Text("Valor Já Guardado (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    prefix = { Text("R$ ", fontWeight = FontWeight.Bold) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val t = targetStr.toDoubleOrNull() ?: 0.0
                    val c = currentStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && t > 0) {
                        onSave(title, description, t, c)
                    }
                },
                enabled = title.isNotBlank() && targetStr.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CRIAR META", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
fun AdjustGoalDialog(
    goal: com.jack.meuholerite.database.FinanceGoalEntity,
    onDismiss: () -> Unit,
    onSave: (com.jack.meuholerite.database.FinanceGoalEntity) -> Unit
) {
    var amountToAddStr by remember { mutableStateOf("") }
    val color = remember(goal.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(goal.colorHex.ifBlank { "#10B981" }))
        } catch (e: Exception) {
            Color(0xFF10B981)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text("Ajustar Valor", fontWeight = FontWeight.Black)
                Text(goal.title, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Atualmente: R$ ${String.format("%,.2f", goal.currentAmount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = amountToAddStr,
                    onValueChange = { amountToAddStr = it.replace(",", ".") },
                    label = { Text("Valor para Adicionar ou Remover") },
                    placeholder = { Text("Use - para subtrair") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    prefix = { Text("R$ ", fontWeight = FontWeight.Bold) }
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quickValues = listOf(10.0, 50.0, 100.0)
                    quickValues.forEach { valQ ->
                        AssistChip(
                            onClick = { amountToAddStr = valQ.toString() },
                            label = { Text("+R$ ${valQ.toInt()}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val adjust = amountToAddStr.toDoubleOrNull() ?: 0.0
                    val newAmount = (goal.currentAmount + adjust).coerceAtLeast(0.0)
                    onSave(goal.copy(currentAmount = newAmount))
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ATUALIZAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancelar", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
fun SmartTipCard(expenses: List<FinanceExpenseEntity>) {
    val highestCategory = expenses.groupBy { it.category }
        .mapValues { it.value.sumOf { exp -> exp.value } }
        .maxByOrNull { it.value }?.key ?: "OTHERS"
    
    val tip = when(highestCategory) {
        "FOOD" -> "Seus maiores gastos são com alimentação. Experimente planejar suas refeições da semana!"
        "TRANSPORT" -> "Gastos com transporte estão altos. Aplicativos de carona ou transporte público podem ajudar."
        "LEISURE" -> "O lazer é importante, mas que tal buscar opções gratuitas neste final de semana?"
        "HOUSING" -> "Gastos fixos de moradia consomem bastante seu orçamento. Fique atento às contas de consumo!"
        else -> "Mantenha o registro de todas as pequenas despesas para ter um controle total!"
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("DICA DO ANALISTA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(tip, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
            }
        }
    }
}

fun String.toAmountDouble(): Double = this.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0

@Composable
fun DebtsSection(
    debts: List<com.jack.meuholerite.database.FinanceDebtEntity>,
    onAdd: () -> Unit,
    onSimulate: () -> Unit,
    onDelete: (com.jack.meuholerite.database.FinanceDebtEntity) -> Unit,
    onPayInstallment: (com.jack.meuholerite.database.FinanceDebtEntity) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("DÍVIDAS E FINANCIAMENTOS")
            Row {
                IconButton(onClick = onSimulate) {
                    Icon(Icons.Outlined.Calculate, "Simular", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Outlined.AddCircleOutline, "Adicionar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (debts.isEmpty()) {
            Text(
                "Nenhuma dívida ou financiamento registrado.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            debts.forEach { debt ->
                DebtItemCard(debt, onDelete, onPayInstallment)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DebtItemCard(
    debt: com.jack.meuholerite.database.FinanceDebtEntity,
    onDelete: (com.jack.meuholerite.database.FinanceDebtEntity) -> Unit,
    onPayInstallment: (com.jack.meuholerite.database.FinanceDebtEntity) -> Unit
) {
    val progress = debt.paidInstallments.toFloat() / debt.totalInstallments.toFloat()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(debt.description, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Vencimento: ${debt.dueDate}", fontSize = 12.sp, color = Color.Gray)
                }
                IconButton(onClick = { onDelete(debt) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Parcela", fontSize = 10.sp, color = Color.Gray)
                    Text("R$ ${debt.monthlyValue.formatBrMoney()}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Restante", fontSize = 10.sp, color = Color.Gray)
                    Text("R$ ${debt.remainingAmount.formatBrMoney()}", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${debt.paidInstallments}/${debt.totalInstallments} parcelas", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (debt.interestRate > 0) {
                        Text("Juros: ${String.format("%.2f", debt.interestRate)}% am", fontSize = 9.sp, color = Color.Gray)
                    }
                }
                
                if (debt.paidInstallments < debt.totalInstallments) {
                    Surface(
                        onClick = { onPayInstallment(debt) },
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "PAGAR PARCELA", 
                            fontSize = 10.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text("QUITADO", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun AddDebtDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, Int, Double, Double, String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var totalAmountStr by remember { mutableStateOf("") }
    var installmentsStr by remember { mutableStateOf("") }
    var paidInstallmentsStr by remember { mutableStateOf("0") }
    var monthlyValueStr by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }

    // Cálculos automáticos
    val totalAmount = totalAmountStr.toDoubleOrNull() ?: 0.0
    val installments = installmentsStr.toIntOrNull() ?: 0
    val paidInstallments = paidInstallmentsStr.toIntOrNull() ?: 0
    val monthlyValue = monthlyValueStr.toDoubleOrNull() ?: 0.0

    // Cálculo da Taxa de Juros (Iterativo/Newton-Raphson simplificado)
    val calculatedInterestRate = remember(totalAmount, installments, monthlyValue) {
        if (totalAmount > 0 && installments > 0 && monthlyValue > (totalAmount / installments)) {
            var rate = 0.01 
            repeat(20) {
                val pow = Math.pow(1 + rate, installments.toDouble())
                val f = monthlyValue * (pow - 1) / (rate * pow) - totalAmount
                val df = monthlyValue * (pow * (1 - installments * rate) - 1) / (rate * rate * pow)
                rate -= f / df
            }
            rate * 100
        } else 0.0
    }

    val totalContractValue = monthlyValue * installments
    val interestCost = (totalContractValue - totalAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Financiamento", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = description, 
                    onValueChange = { description = it }, 
                    label = { Text("O que você comprou? (Ex: Carro)") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalAmountStr, 
                        onValueChange = { totalAmountStr = it.replace(",", ".") }, 
                        label = { Text("Valor do Bem") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                        modifier = Modifier.weight(1f), 
                        prefix = { Text("R$ ") }
                    )
                    OutlinedTextField(
                        value = installmentsStr, 
                        onValueChange = { installmentsStr = it }, 
                        label = { Text("Parcelas") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                        modifier = Modifier.weight(0.6f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyValueStr, 
                        onValueChange = { monthlyValueStr = it.replace(",", ".") }, 
                        label = { Text("Valor da Parcela") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), 
                        modifier = Modifier.weight(1f), 
                        prefix = { Text("R$ ") }
                    )
                    OutlinedTextField(
                        value = paidInstallmentsStr, 
                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) paidInstallmentsStr = it }, 
                        label = { Text("Já pagas") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                        modifier = Modifier.weight(0.6f)
                    )
                }

                OutlinedTextField(
                    value = dueDate, 
                    onValueChange = { dueDate = it }, 
                    label = { Text("Dia do Vencimento") }, 
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                    modifier = Modifier.fillMaxWidth()
                )

                if (calculatedInterestRate > 0 || totalContractValue > totalAmount) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ANÁLISE DO CONTRATO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxa de Juros:", fontSize = 12.sp)
                                Text("${String.format(java.util.Locale.US, "%.2f", calculatedInterestRate)}% ao mês", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Valor Total do Bem:", fontSize = 12.sp)
                                Text("R$ ${totalAmount.formatBrMoney()}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total c/ Juros (Contrato):", fontSize = 12.sp)
                                Text("R$ ${totalContractValue.formatBrMoney()}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Saldo Devedor Atual:", fontSize = 12.sp)
                                val remainingSum = (installments - paidInstallments) * monthlyValue
                                Text("R$ ${remainingSum.formatBrMoney()}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Custo Total em Juros:", fontSize = 12.sp)
                                Text("R$ ${interestCost.formatBrMoney()}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(description, totalAmount, installments, paidInstallments, monthlyValue, calculatedInterestRate, dueDate)
                },
                enabled = description.isNotBlank() && totalAmount > 0 && installments > 0 && monthlyValue > 0 && (paidInstallments < installments)
            ) {
                Text("SALVAR")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun LoanSimulatorDialog(onDismiss: () -> Unit) {
    var amountStr by remember { mutableStateOf("") }
    var monthsStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val months = monthsStr.toIntOrNull() ?: 0
    val rate = (rateStr.toDoubleOrNull() ?: 0.0) / 100.0
    
    // PMT = P * [r(1+r)^n] / [(1+r)^n - 1]
    val pmt = if (amount > 0 && months > 0 && rate > 0) {
        amount * (rate * Math.pow(1 + rate, months.toDouble())) / (Math.pow(1 + rate, months.toDouble()) - 1)
    } else if (amount > 0 && months > 0) {
        amount / months
    } else 0.0
    
    val totalToPay = pmt * months
    val totalInterest = totalToPay - amount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulador de Empréstimo", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = amountStr, onValueChange = { amountStr = it.replace(",", ".") }, label = { Text("Quanto você quer?") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), prefix = { Text("R$ ") })
                OutlinedTextField(value = monthsStr, onValueChange = { monthsStr = it }, label = { Text("Em quantas parcelas?") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rateStr, onValueChange = { rateStr = it.replace(",", ".") }, label = { Text("Taxa de juros ao mês (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), suffix = { Text("%") })
                
                if (pmt > 0) {
                    HorizontalDivider()
                    Text("RESULTADO ESTIMADO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("Parcela Mensal: R$ ${pmt.formatBrMoney()}", fontWeight = FontWeight.Bold)
                    Text("Total a Pagar: R$ ${totalToPay.formatBrMoney()}", fontSize = 14.sp)
                    Text("Total em Juros: R$ ${totalInterest.formatBrMoney()}", fontSize = 14.sp, color = Color.Red.copy(alpha = 0.7f))
                    
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("DICA DO ANALISTA", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            val tip = when {
                                rate * 100 > 5 -> "Essa taxa está muito alta! Procure opções de crédito consignado que costumam ser menores."
                                totalInterest > amount * 0.5 -> "Você vai pagar mais de 50% de juros. Considere economizar um pouco mais antes de contratar."
                                else -> "Lembre-se: comprometer mais de 30% do seu salário com parcelas pode prejudicar sua saúde financeira."
                            }
                            Text(tip, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("FECHAR") } }
    )
}
