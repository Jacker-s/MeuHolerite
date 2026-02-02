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
import androidx.compose.ui.graphics.vector.ImageVector
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

class VacationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val storage = remember { StorageManager(this) }
            MeuHoleriteTheme {
                VacationScreen(storage) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacationScreen(storage: StorageManager, onBack: () -> Unit) {
    val context = LocalContext.current
    var admissionDateStr by remember { mutableStateOf(storage.getAdmissionDate() ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var lastGrossSalary by remember { mutableStateOf<Double?>(null) }
    
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }

    // Carregar remuneração total (Proventos) para a projeção de 1/3 e valor total
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val recibos = db.reciboDao().getAll().map { it.toModel(gson) }
            if (recibos.isNotEmpty()) {
                // Pega o total de proventos do recibo mais recente
                val latest = recibos.first()
                val value = latest.totalProventos
                    .replace(".", "")
                    .replace(",", ".")
                    .toDoubleOrNull()
                lastGrossSalary = value
            }
        }
    }

    // Cálculo de dados de férias
    val vacationData = remember(admissionDateStr, lastGrossSalary) { 
        calculateVacation(admissionDateStr, lastGrossSalary) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vacation_management), fontWeight = FontWeight.Bold) },
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
            // Card de Entrada: Data de Admissão
            Surface(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.admission_date_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = admissionDateStr.ifEmpty { stringResource(R.string.tap_to_select) },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                }
            }

            if (vacationData != null) {
                SectionHeader(stringResource(R.string.vacation_summary))
                
                // Card 1: Dias Acumulados
                VacationInfoCard(
                    title = stringResource(R.string.accrued_days),
                    value = "${vacationData.accruedDays} dias",
                    subtitle = "Proporcional ao tempo trabalhado",
                    icon = Icons.Outlined.BeachAccess,
                    color = Color(0xFF34C759)
                )

                // Card 2: Data Limite
                VacationInfoCard(
                    title = stringResource(R.string.deadline_title),
                    value = vacationData.deadlineDate,
                    subtitle = "Data máxima para evitar férias dobradas",
                    icon = Icons.Outlined.Timer,
                    color = Color(0xFFFF9500)
                )

                SectionHeader(stringResource(R.string.financial_projection))

                // Card: Valor Total Aproximado (Salário + 1/3)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Paid, null, tint = Color(0xFF34C759))
                            Spacer(Modifier.width(12.dp))
                            Text("Valor Total Estimado das Férias", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "R$ ${vacationData.totalValue}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34C759)
                        )
                        Text(
                            "(Total de Proventos + 1/3 Constitucional)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Card Secundário: Detalhe do 1/3
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.one_third_vacation), fontSize = 12.sp, color = Color.Gray)
                            Text("R$ ${vacationData.oneThirdValue}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5856D6))
                        }
                    }
                    
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Base de Cálculo", fontSize = 12.sp, color = Color.Gray)
                            Text("R$ ${String.format("%.2f", lastGrossSalary ?: 0.0)}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (lastGrossSalary != null) "* Calculado com base no total de proventos do seu último recibo." 
                    else "* Importe recibos para calcular com base na sua remuneração real.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.select_admission_instruction),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
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
fun VacationInfoCard(title: String, value: String, subtitle: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 13.sp, color = Color.Gray)
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmissionDatePickerDialog(onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    onDateSelected(sdf.format(Date(it)))
                }
            }) { Text("Confirmar") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

data class VacationResult(
    val accruedDays: Int,
    val deadlineDate: String,
    val oneThirdValue: String,
    val totalValue: String
)

fun calculateVacation(admissionDateStr: String, grossSalaryInput: Double?): VacationResult? {
    if (admissionDateStr.isEmpty()) return null
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return try {
        val admissionDate = sdf.parse(admissionDateStr) ?: return null
        val today = Date()
        
        val diffInMillis = today.time - admissionDate.time
        val daysWorked = TimeUnit.MILLISECONDS.toDays(diffInMillis)
        
        // 1. Dias adquiridos
        val monthsWorked = (daysWorked / 30.44).toInt()
        val accruedDays = ((monthsWorked % 12) * 2.5).toInt().coerceIn(0, 30)
        
        // 2. Data limite
        val calendar = Calendar.getInstance()
        calendar.time = admissionDate
        val yearsOfService = (daysWorked / 365).toInt()
        calendar.add(Calendar.YEAR, yearsOfService + 2)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val deadlineDate = sdf.format(calendar.time)
        
        // 3. Projeção Financeira
        val grossSalary = grossSalaryInput ?: 2000.0
        val oneThird = grossSalary / 3.0
        val total = grossSalary + oneThird
        
        VacationResult(
            accruedDays = accruedDays,
            deadlineDate = deadlineDate,
            oneThirdValue = String.format("%.2f", oneThird),
            totalValue = String.format("%.2f", total)
        )
    } catch (_: Exception) { null }
}
