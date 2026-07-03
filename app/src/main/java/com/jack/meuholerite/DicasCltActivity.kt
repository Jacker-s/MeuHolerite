package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme

class DicasCltActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                DicasCltScreen { finish() }
            }
        }
    }
}

data class CltTip(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DicasCltScreen(onBack: () -> Unit) {
    val tips = listOf(
        CltTip("Adicional Noturno", "Acima das 22h, o valor da hora é acrescido de no mínimo 20%.", Icons.Outlined.NightsStay, Color(0xFF5856D6)),
        CltTip("Horas Extras", "Mínimo de 50% de acréscimo sobre a hora normal (seg-sex).", Icons.Outlined.Timer, Color(0xFFFF9500)),
        CltTip("Férias", "Após 12 meses, você tem direito a 30 dias com acréscimo de 1/3.", Icons.Outlined.BeachAccess, Color(0xFF007AFF)),
        CltTip("Banco de Horas", "Compensação de horas excedentes por folgas, conforme acordo.", Icons.Outlined.History, Color(0xFF34C759))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dicas CLT", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tips) { tip ->
                TipCard(tip)
            }
        }
    }
}

@Composable
fun TipCard(tip: CltTip) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(tip.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(tip.icon, null, tint = tip.color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(tip.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(tip.desc, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
        }
    }
}
