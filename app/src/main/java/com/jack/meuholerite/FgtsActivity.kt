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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import java.util.*
import java.util.concurrent.TimeUnit

class FgtsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val storage = remember { StorageManager(this) }
            MeuHoleriteTheme {
                FgtsScreen(storage) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FgtsScreen(storage: StorageManager, onBack: () -> Unit) {
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

    val fgtsData = remember(receipts, admissionDateStr) { 
        calculateFgtsSummary(receipts, admissionDateStr) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora de FGTS", fontWeight = FontWeight.Bold) },
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
                        Icon(Icons.Outlined.AccountBalance, null, tint = Color(0xFF34C759), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Projeção de Saldo Acumulado", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "R$ " + String.format(Locale.getDefault(), "%.2f", fgtsData.totalAccumulated),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34C759)
                        )
                        Text(
                            "Baseado no tempo de serviço e recibos",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                SectionHeader("Estatísticas")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FgtsSmallCard(
                        title = "Meses de Trabalho",
                        value = fgtsData.monthsCount.toString(),
                        icon = Icons.Outlined.CalendarMonth,
                        modifier = Modifier.weight(1f)
                    )
                    FgtsSmallCard(
                        title = "Depósito Mensal",
                        value = "R$ " + String.format(Locale.getDefault(), "%.2f", fgtsData.averageMonthly),
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                }

                SectionHeader("Em caso de Demissão (Multa 40%)")
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Color(0xFFFF9500).copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Gavel, null, tint = Color(0xFFFF9500))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Valor da Multa (40%)", fontSize = 13.sp, color = Color.Gray)
                            Text(
                                text = "R$ " + String.format(Locale.getDefault(), "%.2f", fgtsData.penaltyValue),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Sobre o saldo projetado no período", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "* Esta é uma projeção refinada que considera seu tempo de serviço desde a admissão ($admissionDateStr) e a média dos depósitos mensais identificados. O saldo real pode variar devido a correções da TR e juros anuais.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FgtsSmallCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

data class FgtsSummary(
    val totalAccumulated: Double,
    val monthsCount: Int,
    val averageMonthly: Double,
    val penaltyValue: Double
)

fun calculateFgtsSummary(receipts: List<ReciboPagamento>, admissionDateStr: String): FgtsSummary {
    var totalFromReceipts = 0.0
    var countReceipts = 0
    
    receipts.forEach { receipt ->
        val fgtsStr = receipt.fgtsMes.replace(".", "").replace(",", ".")
        val fgtsValue = fgtsStr.toDoubleOrNull() ?: 0.0
        if (fgtsValue > 0) {
            totalFromReceipts += fgtsValue
            countReceipts++
        }
    }

    val averageMonthly = if (countReceipts > 0) totalFromReceipts / countReceipts else 0.0
    
    // Tentar projetar o total com base no tempo de admissão
    var monthsSinceAdmission = countReceipts
    if (admissionDateStr.isNotEmpty()) {
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val admissionDate = sdf.parse(admissionDateStr)
            if (admissionDate != null) {
                val diffInMillis = Date().time - admissionDate.time
                val daysWorked = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                monthsSinceAdmission = (daysWorked / 30.44).toInt().coerceAtLeast(countReceipts)
            }
        } catch (_: Exception) {}
    }

    // Se tivermos uma média mas faltarem recibos, projetamos os meses faltantes
    val projectedTotal = if (averageMonthly > 0) {
        averageMonthly * monthsSinceAdmission
    } else {
        totalFromReceipts
    }

    return FgtsSummary(
        totalAccumulated = projectedTotal,
        monthsCount = monthsSinceAdmission,
        averageMonthly = averageMonthly,
        penaltyValue = projectedTotal * 0.4
    )
}
