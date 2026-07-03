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
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ThirteenthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val storage = remember { StorageManager(this) }
            MeuHoleriteTheme {
                ThirteenthScreen(storage) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirteenthScreen(storage: StorageManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    
    var receipts by remember { mutableStateOf<List<ReciboPagamento>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val admissionDateStr = remember { storage.getAdmissionDate() ?: "" }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            receipts = db.reciboDao().getAll().map { it.toModel(gson) }
            isLoading = false
        }
    }

    val calculation = remember(receipts, admissionDateStr) { 
        calculateThirteenth(receipts, admissionDateStr) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simulador de 13º Salário", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Redeem, null, tint = Color(0xFF5856D6), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Valor Total Bruto Estimado", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "R$ ${String.format("%.2f", calculation.totalBruto)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF5856D6)
                        )
                        Text(
                            "Projeção para o final do ano",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                SectionHeader("Parcelas")

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Color(0xFF34C759).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("1ª", fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Primeira Parcela (50%)", fontSize = 13.sp, color = Color.Gray)
                            Text("R$ ${String.format("%.2f", calculation.firstInstallment)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Paga sem descontos de INSS/IRRF", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Color(0xFF007AFF).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("2ª", fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Segunda Parcela (Estimada)", fontSize = 13.sp, color = Color.Gray)
                            Text("R$ ${String.format("%.2f", calculation.secondInstallmentEstimated)}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Com descontos legais aplicados", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                SectionHeader("Base de Cálculo")
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Média de Salário Base:", color = Color.Gray)
                            Text("R$ ${String.format("%.2f", calculation.averageSalary)}", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Avos Calculados (Ano Atual):", color = Color.Gray)
                            Text("${calculation.monthsWorked}/12", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (admissionDateStr.isNotEmpty()) "* Cálculo refinado considerando sua data de admissão: $admissionDateStr"
                    else "* Defina sua data de admissão nas configurações para um cálculo proporcional exato.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

data class ThirteenthCalculation(
    val totalBruto: Double,
    val firstInstallment: Double,
    val secondInstallmentEstimated: Double,
    val averageSalary: Double,
    val monthsWorked: Int
)

fun calculateThirteenth(receipts: List<ReciboPagamento>, admissionDateStr: String): ThirteenthCalculation {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    
    // 1. Determinar quantos meses (avos) o funcionário tem direito no ano atual
    var monthsWorkedInYear = 12
    if (admissionDateStr.isNotEmpty()) {
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val admissionDate = sdf.parse(admissionDateStr)
            val admissionCal = Calendar.getInstance().apply { 
                admissionDate?.let { time = it }
            }
            
            if (admissionCal.get(Calendar.YEAR) == currentYear) {
                // Admitido no ano atual
                val startMonth = admissionCal.get(Calendar.MONTH) // 0-11
                val startDay = admissionCal.get(Calendar.DAY_OF_MONTH)
                
                // Se trabalhou 15 dias ou mais no mês de admissão, conta o mês
                monthsWorkedInYear = 12 - startMonth
                if (startDay > 15) {
                    monthsWorkedInYear--
                }
            } else if (admissionCal.get(Calendar.YEAR) > currentYear) {
                monthsWorkedInYear = 0
            }
        } catch (_: Exception) { }
    }

    // 2. Calcular a média salarial baseada nos recibos do ano atual
    val yearReceipts = receipts.filter { 
        it.periodo.contains(currentYear.toString()) && !it.periodo.contains("13º") 
    }

    val average: Double
    if (yearReceipts.isEmpty()) {
        // Se não houver recibos deste ano, pegar o último disponível
        average = receipts.firstOrNull()?.totalProventos?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 2000.0
    } else {
        val totalProventosSum = yearReceipts.sumOf { 
            it.totalProventos.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 
        }
        average = totalProventosSum / yearReceipts.size.coerceAtLeast(1)
    }
    
    val totalBruto = (average / 12.0) * monthsWorkedInYear
    val first = totalBruto / 2.0
    // Estimativa de descontos (INSS + IRRF) na 2ª parcela (aprox 12-15%)
    val second = (totalBruto / 2.0) * 0.85 

    return ThirteenthCalculation(
        totalBruto = totalBruto,
        firstInstallment = first,
        secondInstallmentEstimated = second,
        averageSalary = average,
        monthsWorked = monthsWorkedInYear
    )
}
