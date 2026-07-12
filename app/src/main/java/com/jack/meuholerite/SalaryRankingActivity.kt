package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.ui.NativeInlineAd
import com.jack.meuholerite.ui.NativeAdSize
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private val RankingBg = Color(0xFFF5EFE4)
private val RankingInk = Color(0xFF13263C)
private val RankingGold = Color(0xFFD6A63C)
private val RankingMint = Color(0xFF1D9A77)
private val RankingSky = Color(0xFF4D88F7)
private val RankingRose = Color(0xFFB86057)

class SalaryRankingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                SalaryRankingScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryRankingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }
    val scope = rememberCoroutineScope()
    val adsRemovedState by AdsDataStore.isAdsRemovedFlow(context).collectAsState(initial = false)
    val activity = context as? android.app.Activity

    var ranking by remember { mutableStateOf<List<com.jack.meuholerite.utils.SalaryRanking>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasSyncedLocalStats by remember { mutableStateOf(false) }
    var expandedCargo by rememberSaveable { mutableStateOf<String?>(null) }
    var hasEngagedRanking by rememberSaveable { mutableStateOf(false) }

    fun refreshData(syncLocalStats: Boolean = false) {
        scope.launch {
            isRefreshing = true

            if (syncLocalStats && !hasSyncedLocalStats) {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    val gson = Gson()
                    db.reciboDao().getAll()
                        .map { it.toModel(gson) }
                        .forEach { recibo ->
                            val baseSalary = recibo.salarioBase.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (recibo.cargo.isNotEmpty() && baseSalary > 0) {
                                backupManager.saveAnonymousSalaryStat(recibo)
                            }
                        }
                }
                hasSyncedLocalStats = true
            }

            backupManager.getTopSalaries()
                .onSuccess {
                    ranking = it.sortedByDescending { item -> item.maxReportedSalary }
                    if (expandedCargo != null && ranking.none { item -> item.cargo == expandedCargo }) {
                        expandedCargo = null
                    }
                    error = null
                }
                .onFailure {
                    error = it.localizedMessage ?: "Erro ao carregar dados."
                }

            isRefreshing = false
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData(syncLocalStats = true)
    }

    // Anúncios inseridos a cada 5 itens diretamente no LazyColumn

    fun exitScreen() {
        if (activity == null) {
            onBack()
            return
        }

        scope.launch {
            if (hasEngagedRanking && AdsDataStore.canShowIntervalAd(activity)) {
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

    Scaffold(
        containerColor = RankingBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Radar Salarial", fontWeight = FontWeight.Black, color = RankingInk)
                        Text(
                            "Card premium por cargo",
                            fontSize = 12.sp,
                            color = RankingInk.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::exitScreen) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = RankingInk)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hasEngagedRanking = true
                        refreshData()
                    }) {
                        Icon(Icons.Outlined.QueryStats, contentDescription = "Atualizar", tint = RankingInk)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = RankingBg
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                RankingBg,
                                Color(0xFFF0E5CF),
                                Color(0xFFE7EEF6)
                            )
                        )
                    )
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        isLoading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = RankingInk)
                                }
                            }
                        }

                        error != null -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = Color.White.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = error!!,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        }

                        ranking.isEmpty() -> {
                            item { EmptyRankingState() }
                        }

                        else -> {
                            itemsIndexed(
                                items = ranking,
                                key = { _, item -> item.cargo }
                            ) { index, item ->
                                SalaryExpandableCard(
                                    item = item,
                                    expanded = expandedCargo == item.cargo,
                                    onToggle = {
                                        hasEngagedRanking = true
                                        expandedCargo = if (expandedCargo == item.cargo) null else item.cargo
                                    }
                                )

                                val shouldShowAd = !adsRemovedState && (
                                    index == 2 || (index > 2 && (index - 2) % 7 == 0)
                                )

                                if (shouldShowAd) {
                                    NativeInlineAd(
                                        adUnitId = "ca-app-pub-7931782163570852/1526069738",
                                        size = NativeAdSize.Compact
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryExpandableCard(
    item: com.jack.meuholerite.utils.SalaryRanking,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        shadowElevation = if (expanded) 8.dp else 2.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            RankingInk,
                            Color(0xFF1C3B5E),
                            if (expanded) Color(0xFF305E87) else Color(0xFF274D72)
                        )
                    )
                )
                .clickable(onClick = onToggle)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = RankingGold, modifier = Modifier.size(22.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (expanded) "Detalhes abertos" else "Toque para expandir",
                            color = RankingGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.cargo,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            lineHeight = 23.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Business, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                item.empresa,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotlightPill(
                        label = "R$ ${formatMoney(item.maxReportedSalary)}",
                        tint = RankingGold,
                        modifier = Modifier.weight(1f)
                    )
                    SpotlightPill(
                        label = "${item.count} relatos",
                        tint = RankingMint,
                        modifier = Modifier.weight(1f)
                    )
                    SpotlightPill(
                        label = "${item.empresasCount} empresas",
                        tint = RankingSky,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (expanded) {
                    SectionTitle(
                        title = "Panorama salarial",
                        subtitle = "Leitura rápida de faixa, mediana, média e retenções"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumMetricCard("Mediana", "R$ ${formatMoney(item.medianaSalary)}", Icons.Outlined.Equalizer, RankingMint, Modifier.weight(1f))
                        PremiumMetricCard("Faixa", salaryRangeLabel(item.minSalary, item.maxSalary), Icons.AutoMirrored.Outlined.TrendingUp, RankingGold, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumMetricCard("Média", "R$ ${formatMoney(item.media)}", Icons.Outlined.QueryStats, RankingSky, Modifier.weight(1f))
                        PremiumMetricCard("Líquido médio", "R$ ${formatMoney(item.mediaLiquida)}", Icons.Outlined.AccountBalanceWallet, RankingMint, Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumMetricCard("Cobertura", coverageLabel(item), Icons.Outlined.Groups, RankingSky, Modifier.weight(1f))
                        PremiumMetricCard("Leitura fiscal", taxInsightLabel(item), Icons.Outlined.Payments, RankingRose, Modifier.weight(1f))
                    }

                    SectionTitle(
                        title = "Sinais do cargo",
                        subtitle = "Resumo da consistência e dos adicionais mais recorrentes"
                    )

                    FlowRowLike(
                        labels = listOf(
                            confidenceLabel(item.count),
                            "${item.count} relatos",
                            "${item.empresasCount} empresas",
                            additionalInsight(item)
                        )
                    )

                    if (item.empresasRelacionadas.isNotEmpty()) {
                        SectionTitle(
                            title = "Empresas recorrentes",
                            subtitle = "Onde esse cargo apareceu com mais frequência"
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Empresas mais recorrentes",
                                    color = RankingGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    item.empresasRelacionadas.joinToString(" • "),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    SectionTitle(
                        title = "Leitura estratégica",
                        subtitle = "Interpretação rápida para tomada de decisão"
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = getCareerInsight(item.cargo, item.media),
                            color = Color.White.copy(alpha = 0.84f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Mediana R$ ${formatMoney(item.medianaSalary)} • Média R$ ${formatMoney(item.media)} • ${item.empresasCount} empresas",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightPill(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun PremiumMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.16f)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FlowRowLike(labels: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoListSheet(
    items: List<com.jack.meuholerite.utils.SalaryRanking>,
    selectedCargo: String,
    onCargoSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Todos os cargos informados", fontWeight = FontWeight.Black, fontSize = 20.sp, color = RankingInk)
                Text("${items.size} cargos disponíveis", color = RankingInk.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (item.cargo == selectedCargo) RankingInk else Color.White,
                    border = BorderStroke(1.dp, RankingInk.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCargoSelected(item.cargo) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            item.cargo,
                            fontWeight = FontWeight.Black,
                            color = if (item.cargo == selectedCargo) Color.White else RankingInk
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Maior salário: R$ ${formatMoney(item.maxReportedSalary)} • ${item.count} relatos",
                            color = if (item.cargo == selectedCargo) Color.White.copy(alpha = 0.74f) else RankingInk.copy(alpha = 0.68f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun EmptyRankingState() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.8f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = RankingInk, modifier = Modifier.size(52.dp))
            Text("Nenhum cargo disponível", fontWeight = FontWeight.Black, fontSize = 18.sp, color = RankingInk)
            Text(
                "Importe mais holerites para montar os cards salariais da base.",
                color = RankingInk.copy(alpha = 0.66f),
                fontSize = 13.sp
            )
        }
    }
}

private fun confidenceLabel(count: Int): String {
    return when {
        count >= 10 -> "Confianca muito alta"
        count >= 5 -> "Confianca alta"
        count >= 3 -> "Confianca boa"
        else -> "Confianca moderada"
    }
}

// Função generateRadarBannerInsertions removida, pois os anúncios agora são a cada 5 itens

private fun salaryRangeLabel(min: Double, max: Double): String {
    if (min <= 0.0 && max <= 0.0) return "R$ 0"
    if (kotlin.math.abs(max - min) < 1.0) return "R$ ${formatMoney(max)}"
    return "R$ ${formatMoney(min)} a R$ ${formatMoney(max)}"
}

private fun coverageLabel(item: com.jack.meuholerite.utils.SalaryRanking): String {
    return "${item.count} relatos • ${item.empresasCount} empresas"
}

private fun additionalInsight(item: com.jack.meuholerite.utils.SalaryRanking): String {
    val tags = buildList {
        if (item.percNoturno >= 0.3) add("Noturno")
        if (item.percInsalubridade >= 0.3) add("Insalubridade")
        if (item.percHoraExtra >= 0.3) add("Hora extra")
    }
    return if (tags.isEmpty()) "Baixa recorrência de adicionais" else tags.joinToString(" • ")
}

private fun taxInsightLabel(item: com.jack.meuholerite.utils.SalaryRanking): String {
    return when {
        item.inssSamples == 0 && item.irrfSamples == 0 -> "Sem amostra confiável"
        item.inssSamples == 0 -> "IRRF R$ ${formatMoney(item.mediaIRRF)}"
        item.irrfSamples == 0 -> "INSS R$ ${formatMoney(item.mediaINSS)}"
        else -> "INSS R$ ${formatMoney(item.mediaINSS)} • IRRF R$ ${formatMoney(item.mediaIRRF)}"
    }
}

private fun getCareerInsight(cargo: String, media: Double): String {
    return when {
        media > 10000 -> "Este cargo aparece na base com padrão de remuneração muito acima da média geral observada."
        media > 5000 -> "Faixa salarial competitiva, com bom espaço para comparação entre empresas e progressão."
        cargo.contains("AUXILIAR", ignoreCase = true) -> "Bom cargo para comparar empresas que pagam melhor na operação e nos adicionais."
        else -> "Use este card para comparar mediana, faixa real e consistência entre relatos do mesmo cargo."
    }
}

private fun formatMoney(value: Double): String {
    return String.format("%,.0f", value)
}
