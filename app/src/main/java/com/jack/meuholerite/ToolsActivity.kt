package com.jack.meuholerite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.IosWidgetFinanceWideCard
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme

class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                ToolsScreenContent { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreenContent(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ferramentas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        ToolsScreen(Modifier.padding(innerPadding))
    }
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun openFinanceManager() {
        context.startActivity(Intent(context, FinanceActivity::class.java))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "CARREIRA E FINANÇAS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
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
            title = "Escala de Trabalho",
            value = "Ver Escala",
            subtitle = "Planeje suas folgas",
            color = Color(0xFF5856D6),
            icon = Icons.Outlined.CalendarMonth,
            onClick = { context.startActivity(Intent(context, EscalaActivity::class.java)) }
        )

        IosWidgetFinanceWideCard(
            title = "Guia do Trabalhador",
            value = "Dicas CLT",
            subtitle = "Conheça seus direitos",
            color = Color(0xFFFF9500),
            icon = Icons.Outlined.Lightbulb,
            onClick = { context.startActivity(Intent(context, DicasCltActivity::class.java)) }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "SIMULADORES CLT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
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
            color = Color(0xFF5AC8FA),
            icon = Icons.Outlined.Redeem,
            onClick = { context.startActivity(Intent(context, ThirteenthActivity::class.java)) }
        )
        IosWidgetFinanceWideCard(
            title = "Rescisão CLT",
            value = "Calcular",
            subtitle = "Pedido ou Demissão",
            color = Color(0xFFFF3B30),
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
