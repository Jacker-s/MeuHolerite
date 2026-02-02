package com.jack.meuholerite.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.R
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.utils.AiAnalyst
import kotlinx.coroutines.launch

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        fontSize = 13.sp,
        color = Color.Gray,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
    )
}

@Composable
fun IosTopBar(userName: String, jornada: String? = null, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Olá,",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = userName.ifEmpty { "Usuário" }.split(" ").firstOrNull() ?: "Usuário",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
            if (jornada != null) {
                Text(
                    text = jornada,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            onClick = onSettingsClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Configurações",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    initialName: String,
    initialMatricula: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var matricula by remember { mutableStateOf(initialMatricula) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Meus Dados", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = matricula,
                    onValueChange = { matricula = it },
                    label = { Text("Matrícula") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, matricula) },
                enabled = name.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun DeductionDetailDialog(item: ReciboItem, isProvento: Boolean, onDismiss: () -> Unit) {
    val color = if (isProvento) Color(0xFF34C759) else Color(0xFFFF3B30)
    val question = if (isProvento) "O que é este ganho?" else "O que é este desconto?"
    val context = LocalContext.current
    val aiAnalyst = remember { AiAnalyst(context) }
    val scope = rememberCoroutineScope()
    
    var aiExplanation by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isProvento) Icons.Outlined.AddCircle else Icons.Outlined.RemoveCircle,
                    contentDescription = null,
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(item.descricao, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Valor: R$ ${item.valor}", fontWeight = FontWeight.SemiBold, color = color)
                Text("Referência: ${item.referencia}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                
                HorizontalDivider()
                
                Text(question, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(getDetalheParaItem(item.descricao, isProvento), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 20.sp)

                Spacer(Modifier.height(8.dp))
                
                Surface(
                    onClick = {
                        if (!isAnalyzing && aiExplanation == null) {
                            scope.launch {
                                isAnalyzing = true
                                aiExplanation = aiAnalyst.explainReciboItem(item, isProvento)
                                isAnalyzing = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Explicação com IA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                            
                            if (isAnalyzing) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        
                        if (aiExplanation != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(aiExplanation!!, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        } else if (!isAnalyzing) {
                            Spacer(Modifier.height(4.dp))
                            Text("Toque para analisar este item com o Gemini AI.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

fun getDetalheParaItem(descricao: String, isProvento: Boolean): String {
    val d = descricao.uppercase()
    return if (isProvento) when {
        d.contains("SALARIO") || d.contains("VENCIMENTO") -> "Seu salário base mensal registrado em contrato, proporcional aos dias trabalhados no mês."
        d.contains("HORA EXTRA") || d.contains("H.E") -> "Pagamento pelas horas trabalhadas além da sua jornada normal. Geralmente com adicional de 50% ou 100%."
        d.contains("ADICIONAL NOTURNO") -> "Compensação financeira para quem trabalha entre as 22h e 5h, devido ao desgaste maior do trabalho noturno."
        d.contains("FERIAS") -> "Pagamento referente ao seu período de descanso anual, incluindo o adicional de 1/3 constitucional."
        d.contains("13O") || d.contains("GRATIFICACAO") -> "Décimo Terceiro Salário (13º), uma gratificação de Natal paga em uma ou duas parcelas ao final do ano."
        d.contains("PERICULOSIDADE") -> "Adicional de 30% sobre o salário-base, pago a profissionais expostos a atividades de risco."
        d.contains("INSALUBRIDADE") -> "Adicional pago a trabalhadores expostos a agentes nocivos à saúde."
        d.contains("DSR") || d.contains("REPOUSO") -> "Descanso Semanal Remunerado. Pagamento referente ao domingo ou feriado."
        d.contains("PREMIO") || d.contains("BONUS") -> "Valor extra pago como reconhecimento por metas atingidas ou desempenho excepcional."
        d.contains("AUXILIO") || d.contains("ABONO") -> "Benefício ou ajuda de custo paga pela empresa."
        else -> "Este é um provento (ganho) que compõe seu salário bruto."
    }
    else when {
        d.contains("INSS") -> "Contribuição obrigatória para a Previdência Social. Garante sua aposentadoria e auxílio-doença."
        d.contains("IRRF") || d.contains("RENDA") -> "Imposto de Renda Retido na Fonte. É o imposto pago ao Governo Federal sobre o que você ganha."
        d.contains("VALE TRANSPORTE") || d.contains("V.T") -> "Sua coparticipação no benefício de transporte fornecido pela empresa."
        d.contains("VALE REFEIÇÃO") || d.contains("V.R") || d.contains("ALIMENTACAO") -> "Desconto referente à sua parte no custo dos cartões de refeição ou alimentação."
        d.contains("MEDICO") || d.contains("SAUDE") || d.contains("ODONTO") -> "Coparticipação ou mensalidade do seu plano de saúde ou odontológico."
        d.contains("SINDICATO") -> "Contribuição voltada ao sindicato da sua categoria."
        d.contains("FALTA") -> "Desconto referente a dias ou horas de ausência não justificadas."
        d.contains("ATRASO") -> "Desconto pelo tempo em que você chegou após o horário de entrada."
        d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> "Pagamento de parcela de Empréstimo Consignado."
        d.contains("ADIANTAMENTO") -> "Valor de adiantamento salarial (o famoso 'vale') pago no início ou meio do mês."
        else -> "Este é um desconto específico da sua folha de pagamento."
    }
}

@Composable
fun ReceiptItemCard(
    item: ReciboItem, 
    color: Color, 
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    if (compact) {
        Surface(
            modifier = modifier.clickable { onClick() },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f))))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        getIconForReciboItem(item.descricao, color == Color(0xFF34C759)),
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = item.descricao,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "R$ ${item.valor}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    } else {
        Surface(
            color = color.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.15f)),
            modifier = modifier.clickable { onClick() }
        ) {
            Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = getIconForReciboItem(item.descricao, color == Color(0xFF34C759))
                    Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.descricao, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Ref: ${item.referencia}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Text("R$ ${item.valor}", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
                }
            }
        }
    }
}

fun getIconForReciboItem(descricao: String, isProvento: Boolean): ImageVector {
    val d = descricao.uppercase()
    return when {
        d.contains("SALARIO") || d.contains("VENCIMENTO") -> Icons.Outlined.AttachMoney
        d.contains("HORA EXTRA") || d.contains("H.E") -> Icons.Outlined.Timer
        d.contains("ADICIONAL NOTURNO") -> Icons.Outlined.NightsStay
        d.contains("13O") || d.contains("GRATIFICACAO") -> Icons.Outlined.CardGiftcard
        d.contains("PERICULOSIDADE") || d.contains("INSALUBRIDADE") -> Icons.Outlined.WarningAmber
        d.contains("DSR") || d.contains("REPOUSO") -> Icons.Outlined.EventRepeat
        d.contains("PREMIO") || d.contains("BONUS") -> Icons.Outlined.EmojiEvents
        d.contains("AUXILIO") || d.contains("ABONO") || d.contains("VALE") -> Icons.Outlined.Redeem
        d.contains("INSS") || d.contains("IRRF") || d.contains("RENDA") -> Icons.Outlined.AccountBalance
        d.contains("MEDICO") || d.contains("SAUDE") || d.contains("ODONTO") -> Icons.Outlined.MedicalServices
        d.contains("SINDICATO") -> Icons.Outlined.Groups
        d.contains("FALTA") || d.contains("ATRASO") -> Icons.Outlined.EventBusy
        d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> Icons.Outlined.CreditScore
        else -> if (isProvento) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline
    }
}

@Composable
fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = if (small) 11.sp else 12.sp,
            maxLines = 1
        )
        Text(
            value,
            color = Color.White,
            fontSize = if (small) 20.sp else 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun IosWidgetCardClickable(title: String, value: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun IosWidgetFinanceWideCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbsenceDetailCard(espelho: EspelhoPonto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFF3B30).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Warning,
                    null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "FALTAS NO PERÍODO",
                    color = Color(0xFFFF3B30),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                espelho.diasFaltas.forEach { data ->
                    Surface(color = Color(0xFFFF3B30), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = data,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IosWidgetSummaryLargeCard(
    espelho: EspelhoPonto,
    userName: String,
    matricula: String = "",
    onEdit: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF007AFF),
                        Color(0xFF005BBF)
                    )
                )
            ).padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.clickable { onEdit() },
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                userName.ifEmpty { "Usuário" },
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (matricula.isNotEmpty()) {
                                Text(
                                    "Matrícula: $matricula",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            Icons.Outlined.Edit,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "SALDO ATUAL",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = espelho.saldoFinalBH,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TRABALHADAS", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(
                        espelho.resumoItens.find { it.label == "label_worked_hours" }?.value
                            ?: "0:00", color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("CRÉDITO H.E.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    val credit =
                        espelho.resumoItens.find { it.label.contains("credit", true) || it.label.contains("extra", true) }?.value
                            ?: "0:00"
                    Text(credit, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IosWidgetTimesheetFullCard(
    espelho: EspelhoPonto,
    userName: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    Surface(
        modifier = modifier.scale(scale).clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF007AFF),
                        Color(0xFF00C6FF)
                    )
                )
            ).padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        userName.ifEmpty { "Ponto" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        espelho.periodo,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = espelho.saldoFinalBH,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    label = "SALDO ATUAL",
                    value = espelho.saldoFinalBH,
                    modifier = Modifier.weight(1f)
                )
                SummaryItem(
                    label = "TRABALHADAS",
                    value = espelho.resumoItens.find { it.label == "label_worked_hours" }?.value
                        ?: "0:00",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun IosWidgetReceiptFullCard(
    recibo: ReciboPagamento,
    userName: String,
    modifier: Modifier,
    onClick: () -> Unit,
    onOpen: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    Surface(
        modifier = modifier.scale(scale).clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF34C759),
                        Color(0xFF248A3D)
                    )
                )
            ).padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        userName.ifEmpty { "Holerite" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        recibo.periodo,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "R$ ${recibo.valorLiquido}",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(
                    label = "PROVENTOS",
                    value = "R$ ${recibo.totalProventos}",
                    modifier = Modifier.weight(1f),
                    small = true
                )
                SummaryItem(
                    label = "DESCONTOS",
                    value = "R$ ${recibo.totalDescontos}",
                    modifier = Modifier.weight(1f),
                    small = true
                )
            }
        }
    }
}

fun getIconForLabel(label: String, isNegative: Boolean): ImageVector {
    return when {
        label.contains("worked") -> Icons.Outlined.Schedule
        label.contains("night") -> Icons.Outlined.NightsStay
        label.contains("extra") -> Icons.Outlined.TrendingUp
        label.contains("absence") || label.contains("excused") -> Icons.Outlined.CheckCircle
        isNegative -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.Info
    }
}

@Composable
fun PontoDetailDialog(labelKey: String, value: String, isNegative: Boolean, onDismiss: () -> Unit) {
    val color = if (isNegative) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val labelText = when {
        labelKey.contains("worked") -> "Horas Trabalhadas"
        labelKey.contains("night") -> "Adicional Noturno"
        labelKey.contains("interval") -> "Atraso no Intervalo"
        labelKey.contains("early") -> "Saída Antecipada"
        labelKey.contains("extra_hours_50") -> "Horas Extras 50%"
        labelKey.contains("extra_hours_100") -> "Horas Extras 100%"
        labelKey.contains("absence") -> "Faltas"
        labelKey.contains("excused") -> "Abono"
        else -> labelKey
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    getIconForLabel(labelKey, isNegative),
                    contentDescription = null,
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(labelText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tempo: $value", fontWeight = FontWeight.SemiBold, color = color)
                HorizontalDivider()
                Text("O que é isso?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(getDetalheParaPonto(labelKey), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 20.sp)
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

fun getDetalheParaPonto(labelKey: String): String {
    return when {
        labelKey.contains("worked") -> "Representa o tempo total que você trabalhou no período, já descontando os intervalos."
        labelKey.contains("night") -> "Horas trabalhadas entre 22h e 05h. Cada hora noturna é computada como 52 minutos e 30 segundos (hora reduzida)."
        labelKey.contains("interval") -> "Ocorre quando o intervalo de descanso/almoço foi menor que o mínimo permitido ou planejado."
        labelKey.contains("early") -> "Tempo que faltou para completar sua jornada devido a uma saída antes do horário previsto."
        labelKey.contains("extra_hours_50") -> "Horas trabalhadas além da jornada normal em dias úteis ou sábados (conforme convenção)."
        labelKey.contains("extra_hours_100") -> "Horas trabalhadas em domingos ou feriados, geralmente remuneradas em dobro."
        labelKey.contains("absence") -> "Tempo referente a ausências não justificadas ou que não foram abonadas pela empresa."
        labelKey.contains("excused") -> "Tempo de ausência que foi justificado (por atestado ou dispensa) e não será descontado."
        else -> "Informação registrada no seu espelho de ponto."
    }
}
