package com.jack.meuholerite.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.WorkHistory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.jack.meuholerite.FgtsActivity
import com.jack.meuholerite.FinanceActivity
import com.jack.meuholerite.ResignationActivity
import com.jack.meuholerite.ThirteenthActivity
import com.jack.meuholerite.VacationActivity
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.model.ReciboTipo
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.utils.extractStartDateForRecibo
import com.jack.meuholerite.utils.formatBrMoney
import com.jack.meuholerite.utils.toMoneyDoubleOrZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ToolsSnapshot(
    val latestVacationRecibo: ReciboPagamento? = null,
    val latestMonthlyRecibo: ReciboPagamento? = null,
    val totalRecibos: Int = 0
)

@Composable
fun ToolsScreen(
    navController: NavController,
    onUserEngaged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val aiParser = remember { AiParser() }

    var showAiDialog by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var isCalculatingAi by remember { mutableStateOf(false) }
    var toolsSnapshot by remember { mutableStateOf(ToolsSnapshot()) }
    var isLoadingSnapshot by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingSnapshot = true
        toolsSnapshot = withContext(Dispatchers.IO) {
            val recibos = db.reciboDao().getAll()
                .map { it.toModel(gson) }
                .sortedByDescending { it.periodo.extractStartDateForRecibo() }

            ToolsSnapshot(
                latestVacationRecibo = recibos.firstOrNull { it.tipo == ReciboTipo.FERIAS },
                latestMonthlyRecibo = recibos.firstOrNull { it.tipo == ReciboTipo.MENSAL },
                totalRecibos = recibos.size
            )
        }
        isLoadingSnapshot = false
    }

    fun openActivity(intent: Intent) {
        onUserEngaged()
        context.startActivity(intent)
    }

    fun openFinanceManager() {
        openActivity(Intent(context, FinanceActivity::class.java))
    }

    fun calculateAiPredictions() {
        scope.launch {
            onUserEngaged()
            isCalculatingAi = true
            showAiDialog = true
            val recibos = withContext(Dispatchers.IO) {
                db.reciboDao().getAll()
                    .map { it.toModel(gson) }
                    .sortedByDescending { it.periodo.extractStartDateForRecibo() }
                    .take(6)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ToolsHeroCard(
            totalRecibos = toolsSnapshot.totalRecibos,
            latestMonthlyRecibo = toolsSnapshot.latestMonthlyRecibo,
            isLoading = isLoadingSnapshot,
            onAiClick = { calculateAiPredictions() },
            onFinanceClick = { openFinanceManager() }
        )

        LatestVacationStatusCard(
            latestVacationRecibo = toolsSnapshot.latestVacationRecibo,
            isLoading = isLoadingSnapshot,
            onOpenVacation = { openActivity(Intent(context, VacationActivity::class.java)) }
        )

        ToolsSectionHeader(
            title = "Planejamento",
            subtitle = "Simulações e leituras rápidas com base nos seus dados."
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolsActionCard(
                title = "Previsão com IA",
                value = "Gerar",
                subtitle = "Estimativa de férias e rescisão baseada nos seus últimos holerites.",
                color = Color(0xFF0F6FFF),
                icon = Icons.Filled.AutoAwesome,
                onClick = { calculateAiPredictions() }
            )
            ToolsActionCard(
                title = "Gestão de Férias",
                value = toolsSnapshot.latestVacationRecibo?.periodo ?: "Calcular",
                subtitle = if (toolsSnapshot.latestVacationRecibo != null) {
                    "Últimas férias identificadas nos seus dados. Abra para projetar o próximo ciclo."
                } else {
                    "Projeção de valores, período aquisitivo e dias acumulados."
                },
                color = Color(0xFF007AFF),
                icon = Icons.Outlined.BeachAccess,
                onClick = { openActivity(Intent(context, VacationActivity::class.java)) }
            )
        }

        ToolsSectionHeader(
            title = "Cálculos trabalhistas",
            subtitle = "Atalhos diretos para estimativas do dia a dia."
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactToolCard(
                    title = "13º",
                    subtitle = "Projetar parcelas",
                    icon = Icons.Outlined.Redeem,
                    color = Color(0xFF5856D6),
                    modifier = Modifier.weight(1f),
                    onClick = { openActivity(Intent(context, ThirteenthActivity::class.java)) }
                )
                CompactToolCard(
                    title = "Rescisão",
                    subtitle = "Pedido ou dispensa",
                    icon = Icons.Outlined.Gavel,
                    color = Color(0xFFFF9500),
                    modifier = Modifier.weight(1f),
                    onClick = { openActivity(Intent(context, ResignationActivity::class.java)) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactToolCard(
                    title = "FGTS",
                    subtitle = "Saldo e multa",
                    icon = Icons.Outlined.AccountBalance,
                    color = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                    onClick = { openActivity(Intent(context, FgtsActivity::class.java)) }
                )
                CompactToolCard(
                    title = "Finanças",
                    subtitle = "Gastos e metas",
                    icon = Icons.Outlined.AccountBalanceWallet,
                    color = Color(0xFF1E9E63),
                    modifier = Modifier.weight(1f),
                    onClick = { openFinanceManager() }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ToolsHeroCard(
    totalRecibos: Int,
    latestMonthlyRecibo: ReciboPagamento?,
    isLoading: Boolean,
    onAiClick: () -> Unit,
    onFinanceClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0F2342),
                            Color(0xFF184976),
                            Color(0xFF2D7FF9)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WorkHistory, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("CENTRAL DE FERRAMENTAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Planeje melhor férias, rescisão, FGTS e organização financeira.",
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isLoading) {
                    "Lendo seus dados salvos para montar atalhos mais inteligentes."
                } else {
                    "Você já tem $totalRecibos recibos salvos${latestMonthlyRecibo?.periodo?.let { " e o último mensal é de $it." } ?: "."}"
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMiniInfo(
                    title = "Líquido atual",
                    value = latestMonthlyRecibo?.valorLiquido?.let { "R$ $it" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                HeroMiniInfo(
                    title = "Base recente",
                    value = if (latestMonthlyRecibo != null) {
                        "R$ ${latestMonthlyRecibo.totalProventos}"
                    } else {
                        "--"
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAiClick,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Previsão IA", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onFinanceClick,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Finanças", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeroMiniInfo(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.76f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LatestVacationStatusCard(
    latestVacationRecibo: ReciboPagamento?,
    isLoading: Boolean,
    onOpenVacation: () -> Unit
) {
    val grossValue = latestVacationRecibo?.totalProventos?.toMoneyDoubleOrZero() ?: 0.0
    val netValue = latestVacationRecibo?.valorLiquido?.toMoneyDoubleOrZero() ?: 0.0

    Surface(
        onClick = onOpenVacation,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF007AFF).copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.BeachAccess, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Últimas férias identificadas", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        text = when {
                            isLoading -> "Lendo seus recibos salvos..."
                            latestVacationRecibo != null -> "Recibo de férias encontrado em ${latestVacationRecibo.periodo}"
                            else -> "Ainda não encontramos um recibo classificado como férias."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            if (latestVacationRecibo != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VacationMetricCard(
                        title = "Bruto",
                        value = "R$ ${grossValue.formatBrMoney()}",
                        accent = Color(0xFF34C759),
                        modifier = Modifier.weight(1f)
                    )
                    VacationMetricCard(
                        title = "Líquido",
                        value = "R$ ${netValue.formatBrMoney()}",
                        accent = Color(0xFF0F6FFF),
                        modifier = Modifier.weight(1f)
                    )
                }

                ToolsHintStrip(
                    icon = Icons.Outlined.Insights,
                    text = "Abrir gestão de férias para comparar esse recibo com a projeção do próximo ciclo."
                )
            } else {
                ToolsHintStrip(
                    icon = Icons.Outlined.Payments,
                    text = "Quando você importar um recibo de férias, esse bloco passa a destacar automaticamente o período mais recente."
                )
            }
        }
    }
}

@Composable
private fun VacationMetricCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolsHintStrip(
    icon: ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ToolsSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ToolsActionCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                Icon(Icons.Outlined.Insights, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CompactToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}
