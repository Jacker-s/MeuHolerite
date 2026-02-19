package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.FinanceExpenseEntity
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.extractStartDateForRecibo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var latestRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    val expenses by db.financeExpenseDao().getAllFlow().collectAsState(initial = emptyList<FinanceExpenseEntity>())
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = db.reciboDao().getAll().map { it.toModel(gson) }
            if (list.isNotEmpty()) {
                latestRecibo = list.sortedByDescending { it.periodo.extractStartDateForRecibo() }.firstOrNull()
            }
        }
    }

    val netSalary = latestRecibo?.valorLiquido?.toAmountDouble() ?: 0.0
    val totalExpenses = expenses.sumOf { it.value }
    val remaining = netSalary - totalExpenses
    val progress = if (netSalary > 0) (totalExpenses / netSalary).coerceIn(0.0, 1.0).toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.finance_management), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            FinanceSummaryCard(
                netSalary = netSalary,
                totalExpenses = totalExpenses,
                remaining = remaining,
                period = latestRecibo?.periodo ?: "",
                progress = progress
            )

            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.deductions))

            if (expenses.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.no_expenses), color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(expenses) { expense ->
                        ExpenseItemRow(expense) {
                            scope.launch {
                                db.financeExpenseDao().delete(expense)
                                backupManager.backupData()
                            }
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
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FinanceSummaryCard(netSalary: Double, totalExpenses: Double, remaining: Double, period: String, progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = stringResource(R.string.remaining_balance).uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "R$ ${String.format("%.2f", remaining)}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 6.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White,
                        strokeWidth = 6.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            if (period.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.net_salary_source, period),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.net_pay), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text("R$ ${String.format("%.2f", netSalary)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.committed_salary), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text("R$ ${String.format("%.2f", totalExpenses)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun ExpenseItemRow(expense: FinanceExpenseEntity, onDelete: () -> Unit) {
    val category = ExpenseCategory.entries.find { it.id == expense.category } ?: ExpenseCategory.OTHERS
    
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(category.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, null, tint = category.color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(category.labelRes), fontSize = 12.sp, color = Color.Gray)
                    if (expense.isFixed) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text(stringResource(R.string.fixed_expense).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ ${String.format("%.2f", expense.value)}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B30),
                    fontSize = 16.sp
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Delete, null, tint = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                }
            }
        }
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
        title = { Text(stringResource(R.string.add_expense), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.expense_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = valueStr,
                    onValueChange = { valueStr = it.replace(",", ".") },
                    label = { Text(stringResource(R.string.expense_value)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Text("R$", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.Bold, color = Color.Gray) }
                )
                
                Text(stringResource(R.string.category_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExpenseCategory.entries) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) category.color else category.color.copy(alpha = 0.1f),
                            contentColor = if (isSelected) Color.White else category.color
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(category.icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(category.labelRes), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isFixed = !isFixed }) {
                    Checkbox(checked = isFixed, onCheckedChange = { isFixed = it })
                    Text(stringResource(R.string.fixed_expense), fontSize = 14.sp)
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

fun String.toAmountDouble(): Double {
    return this.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
}
