package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ResignationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val storage = remember { StorageManager(this) }
            MeuHoleriteTheme {
                ResignationScreen(storage) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResignationScreen(storage: StorageManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }

    var admissionDateStr by remember { mutableStateOf(storage.getAdmissionDate() ?: "") }
    var lastGrossSalary by remember { mutableStateOf<Double?>(null) }
    var resignationType by remember { mutableStateOf(ResignationType.WITHOUT_CAUSE) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val receipts = db.reciboDao().getAll().map { it.toModel(gson) }
            if (receipts.isNotEmpty()) {
                val latest = receipts.first()
                lastGrossSalary = latest.totalProventos.replace(".", "").replace(",", ".").toDoubleOrNull()
            }
        }
    }

    val result = remember(admissionDateStr, lastGrossSalary, resignationType) {
        calculateResignation(admissionDateStr, lastGrossSalary ?: 2000.0, resignationType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simulador de Rescisão", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Configurações
            SectionHeader("Configurações da Rescisão")
            
            Surface(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Data de Admissão", fontSize = 12.sp, color = Color.Gray)
                        Text(admissionDateStr.ifEmpty { "Selecionar" }, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Motivo da Saída", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResignationTypeChip(
                    label = "Demissão (Sem Justa Causa)",
                    selected = resignationType == ResignationType.WITHOUT_CAUSE,
                    onClick = { resignationType = ResignationType.WITHOUT_CAUSE },
                    modifier = Modifier.weight(1f)
                )
                ResignationTypeChip(
                    label = "Pedido de Demissão",
                    selected = resignationType == ResignationType.QUIT,
                    onClick = { resignationType = ResignationType.QUIT },
                    modifier = Modifier.weight(1f)
                )
            }

            if (result != null) {
                // Total
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Estimado a Receber", fontSize = 14.sp)
                        Text(
                            "R$ ${String.format("%.2f", result.total)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                SectionHeader("Detalhamento")
                ResignationItemRow("Saldo de Salário (Dias do mês)", result.salaryBalance)
                ResignationItemRow("Férias Proporcionais + 1/3", result.vacationBalance)
                ResignationItemRow("13º Salário Proporcional", result.thirteenthBalance)
                
                if (resignationType == ResignationType.WITHOUT_CAUSE) {
                    ResignationItemRow("Aviso Prévio Indenizado", result.noticePeriod)
                    ResignationItemRow("Multa FGTS (40%)", result.fgtsPenalty)
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "* Valores aproximados e simplificados. Não inclui descontos de INSS/IRRF sobre as verbas salariais ou outros descontos específicos.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showDatePicker) {
        AdmissionDatePickerDialog(
            onDateSelected = { 
                admissionDateStr = it
                storage.setAdmissionDate(it)
                showDatePicker = false 
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun ResignationTypeChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            label,
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ResignationItemRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text("R$ ${String.format("%.2f", value)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

enum class ResignationType { WITHOUT_CAUSE, QUIT }

data class ResignationResult(
    val salaryBalance: Double,
    val vacationBalance: Double,
    val thirteenthBalance: Double,
    val noticePeriod: Double,
    val fgtsPenalty: Double,
    val total: Double
)

fun calculateResignation(admissionDateStr: String, salary: Double, type: ResignationType): ResignationResult? {
    if (admissionDateStr.isEmpty()) return null
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return try {
        val admissionDate = sdf.parse(admissionDateStr) ?: return null
        val today = Date()
        val diffInMillis = today.time - admissionDate.time
        val daysWorked = TimeUnit.MILLISECONDS.toDays(diffInMillis)
        val monthsWorked = (daysWorked / 30.44).toInt()

        // 1. Saldo de Salário (Simulando que hoje é dia 15)
        val salaryBalance = (salary / 30.0) * 15.0

        // 2. Férias Proporcionais
        val monthsVacation = (monthsWorked % 12)
        val vacationVal = (salary / 12.0) * monthsVacation
        val vacationPlusThird = vacationVal + (vacationVal / 3.0)

        // 3. 13º Proporcional (Simplificado: meses no ano atual)
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val thirteenth = (salary / 12.0) * currentMonth

        // 4. Aviso Prévio e Multa FGTS (Simplificado)
        var notice = 0.0
        var penalty = 0.0
        if (type == ResignationType.WITHOUT_CAUSE) {
            notice = salary // 30 dias base
            val fgtsAccumulated = (salary * 0.08) * monthsWorked.coerceAtLeast(1)
            penalty = fgtsAccumulated * 0.4
        }

        ResignationResult(
            salaryBalance = salaryBalance,
            vacationBalance = vacationPlusThird,
            thirteenthBalance = thirteenth,
            noticePeriod = notice,
            fgtsPenalty = penalty,
            total = salaryBalance + vacationPlusThird + thirteenth + notice + penalty
        )
    } catch (_: Exception) { null }
}
