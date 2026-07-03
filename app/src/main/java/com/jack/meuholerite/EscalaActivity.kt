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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import java.text.SimpleDateFormat
import java.util.*

class EscalaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                EscalaScreen { finish() }
            }
        }
    }
}

enum class TipoEscala(val label: String) {
    E6X1("Escala 6x1"),
    E12X36("Escala 12x36"),
    E5X2("Escala 5x2")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaScreen(onBack: () -> Unit) {
    var selectedScale by remember { mutableStateOf(TipoEscala.E6X1) }
    val daysOff = remember(selectedScale) { calculateNextDaysOff(selectedScale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escala de Trabalho", fontWeight = FontWeight.Bold) },
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
        ) {
            Text("Selecione seu tipo de escala", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            
            TipoEscala.entries.forEach { escala ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedScale == escala,
                        onClick = { selectedScale = escala }
                    )
                    Text(
                        escala.label,
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Próximas Folgas (Estimado)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(daysOff) { date ->
                    DayOffItem(date)
                }
            }
        }
    }
}

@Composable
fun DayOffItem(date: Date) {
    val sdfDay = SimpleDateFormat("EEEE", Locale("pt", "BR"))
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF34C759).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.EventAvailable, null, tint = Color(0xFF34C759))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(sdfDay.format(date).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                Text(sdfDate.format(date), fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

fun calculateNextDaysOff(scale: TipoEscala): List<Date> {
    val daysOff = mutableListOf<Date>()
    val calendar = Calendar.getInstance()
    
    // Projeta folgas para os próximos 30 dias de forma simplificada
    when (scale) {
        TipoEscala.E5X2 -> {
            for (i in 0..30) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || 
                    calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    daysOff.add(calendar.time)
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        TipoEscala.E12X36 -> {
            // Assume que hoje é dia de trabalho para simplificar a projeção
            calendar.add(Calendar.DAY_OF_YEAR, 1) // Amanhã é folga
            for (i in 0..15) {
                daysOff.add(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 2)
            }
        }
        TipoEscala.E6X1 -> {
            // Assume folga no domingo como exemplo padrão
            for (i in 0..30) {
                if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    daysOff.add(calendar.time)
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }
    return daysOff
}
