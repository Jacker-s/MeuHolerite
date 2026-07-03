package com.jack.meuholerite.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.jack.meuholerite.R
import com.jack.meuholerite.FinanceActivity
import com.jack.meuholerite.VacationActivity
import com.jack.meuholerite.ThirteenthActivity
import com.jack.meuholerite.ResignationActivity
import com.jack.meuholerite.FgtsActivity
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val aiParser = remember { AiParser() }

    var showAiDialog by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var isCalculatingAi by remember { mutableStateOf(false) }

    fun openFinanceManager() {
        context.startActivity(Intent(context, FinanceActivity::class.java))
    }

    fun calculateAiPredictions() {
        scope.launch {
            isCalculatingAi = true
            showAiDialog = true
            val recibos = withContext(Dispatchers.IO) {
                db.reciboDao().getAll().take(6).map { it.toModel(gson) }
            }
            
            if (recibos.isEmpty()) {
                aiResult = "Importe seus holerites primeiro para que a IA possa analisar seus rendimentos."
                isCalculatingAi = false
                return@launch
            }

            val recibosContext = recibos.joinToString("\n") { r ->
                "Período: ${r.periodo}, Bruto: ${r.totalProventos}, Líquido: ${r.valorLiquido}, Admissão: ${r.dataAdmissao}"
            }
            
            val activity = context as? android.app.Activity
            if (activity != null && AdsDataStore.canShowIntervalAd(context)) {
                RewardedInterstitialAdManager.showAd(activity) {
                    scope.launch {
                        AdsDataStore.incrementAdsShown(context)
                        AdsDataStore.markIntervalAdShown(context)
                        aiResult = aiParser.getPredictions(recibosContext)
                        isCalculatingAi = false
                    }
                }
            } else {
                aiResult = aiParser.getPredictions(recibosContext)
                isCalculatingAi = false
            }
        }
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCalculatingAi) showAiDialog = false },
            confirmButton = {
                TextButton(onClick = { showAiDialog = false }, enabled = !isCalculatingAi) {
                    Text("Fechar")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Previsões Inteligentes", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (isCalculatingAi) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Analisando seus holerites...", fontSize = 14.sp)
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = aiResult ?: "Erro ao gerar previsões.",
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Nota: Estes valores são estimativas baseadas nos seus dados e podem variar conforme regras específicas da empresa ou sindicato.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        IosWidgetFinanceWideCard(
            title = "Previsão com IA",
            value = "Gerar",
            subtitle = "Estimativa de Férias e Rescisão baseada em seus holerites",
            color = Color(0xFF673AB7),
            icon = Icons.Filled.AutoAwesome,
            onClick = { calculateAiPredictions() }
        )

        IosWidgetFinanceWideCard(
            title = "Gestão Financeira",
            value = "Gerenciar",
            subtitle = "Controle gastos e metas",
            color = Color(0xFF34C759),
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = { openFinanceManager() }
        )
        
        IosWidgetFinanceWideCard(
            title = "Gestão de Férias",
            value = "Calcular",
            subtitle = "Projeção e dias acumulados",
            color = Color(0xFF007AFF),
            icon = Icons.Outlined.BeachAccess,
            onClick = { context.startActivity(Intent(context, VacationActivity::class.java)) }
        )

        IosWidgetFinanceWideCard(
            title = "13º Salário",
            value = "Projetar",
            subtitle = "Média das parcelas",
            color = Color(0xFF5856D6),
            icon = Icons.Outlined.Redeem,
            onClick = { context.startActivity(Intent(context, ThirteenthActivity::class.java)) }
        )

        IosWidgetFinanceWideCard(
            title = "Rescisão CLT",
            value = "Calcular",
            subtitle = "Pedido ou Demissão",
            color = Color(0xFFFF9500),
            icon = Icons.Outlined.Gavel,
            onClick = { context.startActivity(Intent(context, ResignationActivity::class.java)) }
        )

        IosWidgetFinanceWideCard(
            title = "Saldo FGTS",
            value = "Consultar",
            subtitle = "Saldo e multa estimada",
            color = Color(0xFF34C759),
            icon = Icons.Outlined.AccountBalance,
            onClick = { context.startActivity(Intent(context, FgtsActivity::class.java)) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
