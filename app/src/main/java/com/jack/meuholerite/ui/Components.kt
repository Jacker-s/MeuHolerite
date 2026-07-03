package com.jack.meuholerite.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
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
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.database.AppDatabase
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.input.pointer.pointerInput
import com.jack.meuholerite.utils.formatBrMoney
import com.jack.meuholerite.utils.toMoneyDoubleOrZero
import com.jack.meuholerite.utils.extractStartDateForRecibo
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

val LocalPrivacyActive = compositionLocalOf { false }

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
fun PrivacyValueText(
    value: String,
    modifier: Modifier = Modifier,
    isPrivacyActive: Boolean = LocalPrivacyActive.current,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val displayValue = if (isPrivacyActive) {
        "R$ ••••"
    } else {
        value
    }
    
    Text(
        text = displayValue,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun IosTopBar(
    userName: String, 
    jornada: String? = null, 
    isPrivacyActive: Boolean = false,
    onPrivacyToggle: () -> Unit = {},
    onRankingClick: () -> Unit = {},
    onSettingsClick: () -> Unit
) {
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

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = onRankingClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Ranking Salarial",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Surface(
                onClick = onPrivacyToggle,
                shape = CircleShape,
                color = if (isPrivacyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPrivacyActive) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Modo Privacidade",
                        tint = if (isPrivacyActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
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
}

@Composable
fun EditProfileDialog(
    initialName: String,
    initialMatricula: String = "",
    initialCargo: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var matricula by remember { mutableStateOf(initialMatricula) }
    var cargo by remember { mutableStateOf(initialCargo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(
                    "Meus Dados", 
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Informações profissionais identificadas",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = cargo,
                    onValueChange = { cargo = it },
                    label = { Text("Cargo / Função") },
                    leadingIcon = { Icon(Icons.Outlined.WorkOutline, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = matricula,
                    onValueChange = { matricula = it },
                    label = { Text("Matrícula") },
                    leadingIcon = { Icon(Icons.Outlined.Badge, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, matricula, cargo) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)
            ) {
                Text("Confirmar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Cancelar", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DeductionDetailDialog(item: ReciboItem, isProvento: Boolean, onDismiss: () -> Unit) {
    val color = if (isProvento) Color(0xFF34C759) else Color(0xFFFF3B30)
    val question = if (isProvento) "O que é este ganho?" else "O que é este desconto?"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isProvento) Icons.Outlined.Add else Icons.Outlined.Remove,
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
                
                Text(question, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(getDetalheParaItem(item.descricao, isProvento), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 20.sp)
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

fun getDetalheParaItem(descricao: String, isProvento: Boolean): String {
    val d = descricao.uppercase()
    return if (isProvento) when {
        d.contains("SALARIO") || d.contains("VENCIMENTO") || d.contains("ORDENADO") -> "Seu salário base mensal registrado em contrato, proporcional aos dias trabalhados no mês."
        d.contains("HORA EXTRA") || d.contains("H.E") -> "Pagamento pelas horas trabalhadas além da sua jornada normal. O percentual (50%, 100%) varia conforme o dia e convenção coletiva."
        d.contains("ADICIONAL NOTURNO") -> "Compensação financeira para quem trabalha entre as 22h e 5h, devido ao desgaste maior e alteração do ciclo biológico no trabalho noturno."
        d.contains("FERIAS") || d.contains("1/3") -> "Pagamento referente ao seu descanso anual. Inclui o salário do período e o adicional constitucional de 1/3 sobre o valor."
        d.contains("13O") || d.contains("GRATIFICACAO") || d.contains("NATAL") -> "Gratificação de Natal paga anualmente. Pode ser recebida em parcelas ou em cota única ao final do ano."
        d.contains("PERICULOSIDADE") -> "Adicional de 30% sobre o salário-base devido à exposição a riscos fatais ou inflamáveis no exercício da função."
        d.contains("INSALUBRIDADE") -> "Adicional pago por exposição a agentes nocivos (ruído, químicos, calor). O valor varia por grau: mínimo (10%), médio (20%) ou máximo (40%)."
        d.contains("PLR") || d.contains("PARTICIPACAO") || d.contains("LUCROS") -> "Participação nos Lucros e Resultados da empresa. É um bônus atrelado ao desempenho organizacional e metas batidas."
        d.contains("DSR") || d.contains("REPOUSO") -> "Descanso Semanal Remunerado. Garante que você receba pelo dia de descanso obrigatório (geralmente domingo e feriados)."
        d.contains("PREMIO") || d.contains("BONUS") || d.contains("MERITO") -> "Reconhecimento financeiro por atingimento de metas individuais, tempo de casa ou excelência no desempenho."
        d.contains("ABONO") || d.contains("PIS") -> "Abono salarial ou pecuniário, representando um valor extra concedido por lei ou acordo coletivo."
        d.contains("AUXILIO CRECHE") -> "Auxílio para custeio de berçários ou creches, visando apoiar a jornada de pais e mães colaboradores."
        d.contains("TRIENIO") || d.contains("BIENIO") || d.contains("ANUENIO") -> "Adicional por tempo de serviço, aumentando seu salário conforme sua fidelidade à empresa."
        d.contains("RETROATIVO") || d.contains("DIFERENCA") -> "Pagamento de valores de meses anteriores que não foram quitados na época, muitas vezes devido a reajustes tardios."
        else -> "Este é um provento (ganho) que aumenta seu rendimento bruto no mês."
    }
    else when {
        d.contains("INSS") -> "Previdência Social. Contribuição que dá direito a aposentadoria, auxílio-doença, salário-maternidade e outros benefícios do governo."
        d.contains("IRRF") || d.contains("RENDA") || d.contains("IMPOSTO") -> "Imposto de Renda Retido na Fonte. É o valor pago ao Governo Federal, calculado sobre sua faixa salarial mensal."
        d.contains("VALE TRANSPORTE") || d.contains("V.T") -> "Sua coparticipação (até 6% do salário) para o custeio do transporte utilizado no deslocamento casa-trabalho."
        d.contains("VALE REFEIÇÃO") || d.contains("V.R") || d.contains("ALIMENTACAO") || d.contains("TICKET") -> "Sua parte no custeio do benefício de alimentação ou refeição oferecido pela empresa."
        d.contains("MEDICO") || d.contains("SAUDE") || d.contains("ODONTO") || d.contains("FARMACIA") -> "Sua coparticipação em consultas, exames ou a mensalidade do plano de saúde, odontológico ou convênio farmácia."
        d.contains("SINDICATO") || d.contains("ASSISTENCIAL") || d.contains("CONFEDERATIVA") -> "Contribuição para manter as atividades do sindicato que representa sua categoria e negocia seus benefícios."
        d.contains("FALTA") || d.contains("AUSENCIA") -> "Desconto do salário proporcional aos dias em que não houve comparecimento ao trabalho sem justificativa legal."
        d.contains("ATRASO") -> "Reflexo financeiro do tempo não trabalhado devido a entradas tardias ou saídas antecipadas não autorizadas."
        d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> "Parcela de empréstimo descontada diretamente em folha, geralmente com taxas de juros reduzidas."
        d.contains("ADIANTAMENTO") -> "Valor pago antecipadamente durante o mês (comumente o 'vale' do dia 15 ou 20), agora descontado no fechamento."
        d.contains("PENSAO") || d.contains("ALIMENTICIA") -> "Desconto determinado judicialmente para o pagamento de pensão alimentícia a dependentes."
        d.contains("ESTACIONAMENTO") -> "Custeio do uso de vagas ou infraestrutura de estacionamento oferecida pela empresa."
        d.contains("PREVIDENCIA PRIVADA") || d.contains("PACK") -> "Sua contribuição para um plano de aposentadoria complementar oferecido opcionalmente pela empresa."
        else -> "Este é um desconto (dedução) que reduz o valor líquido recebido no mês."
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
    val isEarning = color == Color(0xFF34C759)
    // Cores premium selecionadas
    val itemColor = if (isEarning) Color(0xFF10B981) else Color(0xFFF43F5E)
    
    if (compact) {
        Surface(
            modifier = modifier.clickable { onClick() },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.linearGradient(listOf(itemColor, itemColor.copy(alpha = 0.8f))))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        getIconForReciboItem(item.descricao, isEarning),
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        if (isEarning) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
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
                PrivacyValueText(
                    value = "R$ ${item.valor}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    } else {
        Surface(
            color = itemColor.copy(alpha = 0.06f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, itemColor.copy(alpha = 0.1f)),
            modifier = modifier.clickable { onClick() }
        ) {
            Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = getIconForReciboItem(item.descricao, isEarning)
                    Box(modifier = Modifier.size(42.dp).background(itemColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = itemColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.descricao, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 15.sp, 
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.2).sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Referação: ${item.referencia}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (isEarning) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                null,
                                tint = itemColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    PrivacyValueText(
                        value = "R$ ${item.valor}", 
                        fontWeight = FontWeight.Black, 
                        color = itemColor, 
                        fontSize = 17.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        }
    }
}

fun getIconForReciboItem(descricao: String, isProvento: Boolean): ImageVector {
    val d = descricao.uppercase()
    return when {
        d.contains("SALARIO") || d.contains("VENCIMENTO") || d.contains("ORDENADO") -> Icons.Outlined.AttachMoney
        d.contains("HORA EXTRA") || d.contains("H.E") -> Icons.Outlined.Timer
        d.contains("ADICIONAL NOTURNO") -> Icons.Outlined.NightsStay
        d.contains("FERIAS") -> Icons.Outlined.BeachAccess
        d.contains("13O") || d.contains("GRATIFICACAO") -> Icons.Outlined.CardGiftcard
        d.contains("PERICULOSIDADE") || d.contains("INSALUBRIDADE") -> Icons.Outlined.Emergency
        d.contains("DSR") || d.contains("REPOUSO") -> Icons.Outlined.EventRepeat
        d.contains("PLR") || d.contains("PARTICIPACAO") || d.contains("LUCROS") -> Icons.Outlined.CorporateFare
        d.contains("PREMIO") || d.contains("BONUS") -> Icons.Outlined.EmojiEvents
        d.contains("CRECHE") || d.contains("ESCOLA") -> Icons.Outlined.ChildCare
        d.contains("AUXILIO") || d.contains("ABONO") -> Icons.Outlined.Redeem
        d.contains("VALE") || d.contains("TICKET") || d.contains("REFEICAO") || d.contains("ALIMENTACAO") -> Icons.Outlined.Restaurant
        d.contains("TRANSPORTE") || d.contains("V.T") -> Icons.Outlined.DirectionsBus
        d.contains("INSS") || d.contains("IRRF") || d.contains("RENDA") -> Icons.Outlined.AccountBalance
        d.contains("MEDICO") || d.contains("SAUDE") -> Icons.Outlined.MedicalServices
        d.contains("ODONTO") -> Icons.Outlined.HealthAndSafety
        d.contains("SINDICATO") -> Icons.Outlined.Groups
        d.contains("FALTA") || d.contains("ATRASO") -> Icons.Outlined.EventBusy
        d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> Icons.Outlined.PriceCheck
        d.contains("PENSAO") -> Icons.Outlined.EscalatorWarning
        d.contains("PREVIDENCIA PRIVADA") -> Icons.Outlined.Savings
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
        PrivacyValueText(
            value = value,
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
            PrivacyValueText(value = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
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
                PrivacyValueText(value = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
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

@Composable
fun BankHoursDashboard(espelho: EspelhoPonto) {
    val finalMins = com.jack.meuholerite.utils.timeToMinutes(espelho.saldoFinalBH)
    val periodMins = com.jack.meuholerite.utils.timeToMinutes(espelho.saldoPeriodoBH)
    val previousMins = com.jack.meuholerite.utils.timeToMinutes(espelho.saldoAnteriorBH)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val creditColor = Color(0xFF34C759)
    val debitColor = Color(0xFFFF3B30)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "MOVIMENTAÇÃO DO BANCO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = primaryColor.copy(alpha=0.8f),
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(20.dp))
            
            // Layout de 3 colunas/estágios
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Saldo Anterior
                SmallBHInfo(
                    label = "ANTERIOR",
                    value = espelho.saldoAnteriorBH,
                    color = if (previousMins >= 0) MaterialTheme.colorScheme.onSurfaceVariant else debitColor,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    Icons.Outlined.Add, 
                    null, 
                    modifier = Modifier.size(12.dp), 
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Mês Atual
                SmallBHInfo(
                    label = "ESTE MÊS",
                    value = (if (periodMins > 0) "+" else "") + espelho.saldoPeriodoBH,
                    color = if (periodMins >= 0) creditColor else debitColor,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    "=", 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                // Saldo Final
                Column(
                    modifier = Modifier.weight(1.3f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "SALDO ATUAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = (if (finalMins > 0) "+" else "") + espelho.saldoFinalBH,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (finalMins >= 0) creditColor else debitColor
                    )
                }
            }
            
            if (espelho.detalhesSaldoBH.isNotBlank() && espelho.detalhesSaldoBH.length > 5) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = espelho.detalhesSaldoBH,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SmallBHInfo(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun AnimatedAppIcon(modifier: Modifier = Modifier, size: Int = 80) {
    val infiniteTransition = rememberInfiniteTransition(label = "iconScale")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        // Efeito de brilho/aura ao fundo
        Box(
            modifier = Modifier
                .size((size * 0.9).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        
        // Base do ícone (simulando o fundo do app icon)
        Surface(
            modifier = Modifier.size((size * 0.75).dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = com.jack.meuholerite.R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.size((size * 0.6).dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun IosWidgetFinanceHighlightCard(
    remaining: Double,
    totalExpenses: Double,
    totalDebts: Double = 0.0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    
    val totalSpent = totalExpenses + totalDebts
    val income = (remaining + totalSpent).coerceAtLeast(0.0)
    val progress = if (income > 0) (totalSpent / income).coerceIn(0.0, 1.0).toFloat() else 0f

    val color = when {
        remaining < 0 -> Color(0xFFFF3B30) // Crítico (Negativo)
        progress < 0.5f -> Color(0xFF34C759) // Saudável (Até 50%)
        progress < 0.8f -> Color(0xFFFF9500) // Alerta (50% a 80%)
        else -> Color(0xFFFF3B30) // Perigo (Acima de 80%)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = 0.9f),
                            color
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "GESTÃO FINANCEIRA",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Saldo Restante",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PrivacyValueText(
                value = "R$ ${String.format("%.2f", remaining)}",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    PrivacyValueText(
                        value = "Despesas: R$ ${String.format("%.2f", totalExpenses)}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (totalDebts > 0) {
                        PrivacyValueText(
                            value = "Parcelas Dívidas: R$ ${String.format("%.2f", totalDebts)}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
            }
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
        label.contains("extra") -> Icons.AutoMirrored.Outlined.TrendingUp
        label.contains("absence") || label.contains("excused") -> Icons.Outlined.CheckCircle
        isNegative -> Icons.AutoMirrored.Outlined.TrendingDown
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

// -------------------------------------------------------------
// PREMIUM ANIMATED NAVIGATION COMPONENTS
// -------------------------------------------------------------

@Composable
fun AnimatedHomeIcon(selected: Boolean, color: Color) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.25f else 1.0f, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val alpha by animateFloatAsState(if (selected) 1f else 0.8f, label = "alpha")
    
    Box(
        modifier = Modifier
            .size(24.dp)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        // Base circular para o ícone (estilo o ícone do app)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = com.jack.meuholerite.R.drawable.ic_launcher_foreground),
                    contentDescription = "Início",
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize(0.7f)
                )
            }
        }
    }
}

@Composable
fun AnimatedGlobeIcon(selected: Boolean, color: Color) {
    val rotation by animateFloatAsState(
        targetValue = if (selected) 360f else 0f, 
        animationSpec = if (selected) {
            infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
        } else {
            snap()
        },
        label = "rotation"
    )
    val scale by animateFloatAsState(if (selected) 1.2f else 1.0f, label = "scale")

    Canvas(modifier = Modifier.size(24.dp).scale(scale)) {
        // Círculo externo
        drawCircle(
            color = color,
            radius = size.minDimension * 0.45f,
            style = Stroke(width = 1.8.dp.toPx())
        )
        
        // Núcleo central
        drawCircle(
            color = color,
            radius = size.minDimension * 0.15f
        )

        // Arcos de sincronização (giratórios)
        val arcPath = Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    0f, 0f, size.width, size.height
                ).deflate(4.dp.toPx()),
                startAngleDegrees = rotation,
                sweepAngleDegrees = 90f
            )
            addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    0f, 0f, size.width, size.height
                ).deflate(4.dp.toPx()),
                startAngleDegrees = rotation + 180f,
                sweepAngleDegrees = 90f
            )
        }
        
        drawPath(
            path = arcPath,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun AnimatedReceiptIcon(selected: Boolean, color: Color) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, label = "progress")
    
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.1f)
            lineTo(size.width * 0.8f, size.height * 0.1f)
            lineTo(size.width * 0.8f, size.height * 0.85f)
            lineTo(size.width * 0.65f, size.height * 0.75f)
            lineTo(size.width * 0.5f, size.height * 0.85f)
            lineTo(size.width * 0.35f, size.height * 0.75f)
            lineTo(size.width * 0.2f, size.height * 0.85f)
            close()
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Linhas internas animadas
        if (selected) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(size.width * (0.35f + 0.3f * progress), size.height * 0.3f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.45f),
                end = androidx.compose.ui.geometry.Offset(size.width * (0.35f + 0.3f * progress), size.height * 0.45f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AnimatedClockIcon(selected: Boolean, color: Color) {
    val rotation by animateFloatAsState(if (selected) 90f else 0f, label = "rotation")
    
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2.2f,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Ponteiro Central
        drawCircle(color = color, radius = 2.dp.toPx())
        
        // Ponteiro Horas
        drawLine(
            color = color,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                center.x + (size.width * 0.2f) * kotlin.math.cos(Math.toRadians(rotation.toDouble() - 90.0)).toFloat(),
                center.y + (size.width * 0.2f) * kotlin.math.sin(Math.toRadians(rotation.toDouble() - 90.0)).toFloat()
            ),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        // Ponteiro Minutos
        drawLine(
            color = color,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                center.x + (size.width * 0.3f) * kotlin.math.cos(Math.toRadians(rotation.toDouble() * 2.0)).toFloat(),
                center.y + (size.width * 0.3f) * kotlin.math.sin(Math.toRadians(rotation.toDouble() * 2.0)).toFloat()
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PremiumNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Boolean, Color) -> Unit,
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val weight by animateFloatAsState(if (selected) 1.2f else 1f, label = "weight")
    
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (selected) accentColor.copy(alpha = 0.08f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            icon(selected, color)
        }
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun LaborAiChatDialog(db: AppDatabase, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val chatHistory = remember { mutableStateListOf<Pair<String, Boolean>>() } // String to IsUser
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val aiParser = remember { AiParser() }
    val listState = rememberLazyListState()

    if (chatHistory.isEmpty()) {
        chatHistory.add("Olá! Sou seu assistente jurídico-trabalhista. No que posso te ajudar hoje?" to false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(16.dp)
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("CONSULTA TRABALHISTA", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, null)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))

                    // Chat Area
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(chatHistory) { (text, isUser) ->
                            ChatBubble(text, isUser)
                        }
                        if (isThinking) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }

                    // Input Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Tire qualquer dúvida sobre seu trabalho...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (query.isNotBlank() && !isThinking) {
                                    val userQuery = query
                                    chatHistory.add(userQuery to true)
                                    query = ""
                                    isThinking = true
                                    
                                    scope.launch {
                                        val systemPrompt = "Você é um assistente jurídico-trabalhista inteligente e versátil. Ajude o usuário com qualquer dúvida sobre o mundo do trabalho, CLT, direitos, deveres e cálculos trabalhistas de forma clara e amigável. Sinta-se livre para fornecer explicações detalhadas e proativas. Usuário pergunta: "
                                        val response = aiParser.getAiAnalysis(systemPrompt + userQuery)
                                        chatHistory.add((response ?: "Desculpe, tive um problema ao processar sua dúvida.") to false)
                                        isThinking = false
                                        listState.animateScrollToItem(chatHistory.size - 1)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = color,
            shape = shape,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(14.dp),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}


@Composable
fun PremiumEvolutionChart(
    history: List<ReciboPagamento>,
    showGross: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sortedHistory = remember(history) { 
        history.sortedBy { it.periodo.extractStartDateForRecibo() }
    }
    val netPoints = sortedHistory.map { it.valorLiquido.toMoneyDoubleOrZero() }
    val grossPoints = if (showGross) sortedHistory.map { it.totalProventos.toMoneyDoubleOrZero() } else null
    
    if (netPoints.size < 2) return

    val maxVal = maxOf(
        netPoints.maxOrNull() ?: 1.0,
        grossPoints?.maxOrNull() ?: 1.0
    )
    val minVal = minOf(
        netPoints.minOrNull() ?: 0.0,
        grossPoints?.minOrNull() ?: 0.0
    )
    val range = (maxVal - minVal).coerceAtLeast(100.0)
    
    val netColor = MaterialTheme.colorScheme.primary
    val grossColor = Color(0xFF34C759)
    val interactionColor = MaterialTheme.colorScheme.tertiary
    
    var animationPlayed by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "pathProgress"
    )
    
    LaunchedEffect(Unit) { animationPlayed = true }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Inteligente
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val title = if (selectedIndex == null) "Análise de Rendimentos" else sortedHistory[selectedIndex!!].periodo
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (selectedIndex == null) {
                        val avg = netPoints.average()
                        Text(
                            "Média líquida: R$ ${avg.formatBrMoney()}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val pt = netPoints[selectedIndex!!]
                        val avg = netPoints.average()
                        val diff = ((pt / avg) - 1) * 100
                        Text(
                            if (diff >= 0.0) "${diff.toInt()}% acima da sua média" else "${(-diff).toInt()}% abaixo da média",
                            fontSize = 11.sp,
                            color = if (diff >= 0.0) Color(0xFF34C759) else Color(0xFFFF3B30),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (selectedIndex != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            color = netColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "R$ ${netPoints[selectedIndex!!].formatBrMoney()}",
                                modifier = Modifier.padding(6.dp),
                                color = netColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { selectedIndex = null },
                            onDragCancel = { selectedIndex = null },
                            onDrag = { change, _ ->
                                val x = change.position.x
                                val spacing = size.width / (netPoints.size - 1).coerceAtLeast(1)
                                val idx = (x / spacing).roundToInt().coerceIn(0, netPoints.size - 1)
                                selectedIndex = idx
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val spacing = width / (netPoints.size - 1).coerceAtLeast(1)
                    
                    // Lógica para desenhar uma linha (Net ou Gross)
                    fun drawLinePath(pts: List<Double>, color: Color, isSecondary: Boolean = false) {
                        val path = Path()
                        val fillPath = Path()
                        val coords = pts.mapIndexed { index, value ->
                            val x = index * spacing
                            val normalized = if (range > 0) (value - minVal) / range else 0.5
                            val y = height - (normalized.toFloat() * height * 0.8f) - (height * 0.1f)
                            androidx.compose.ui.geometry.Offset(x, y)
                        }

                        if (coords.isEmpty()) return

                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, width * animationProgress, height)

                        // Path suave (Bezier)
                        path.moveTo(coords[0].x, coords[0].y)
                        for (i in 1 until coords.size) {
                            val prev = coords[i-1]
                            val curr = coords[i]
                            path.cubicTo((prev.x+curr.x)/2, prev.y, (prev.x+curr.x)/2, curr.y, curr.x, curr.y)
                        }

                        // Preenchimento (apenas para a linha principal/Net)
                        if (!isSecondary) {
                            fillPath.addPath(path)
                            fillPath.lineTo(coords.last().x, height)
                            fillPath.lineTo(0f, height)
                            fillPath.close()
                            drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha=0.15f), Color.Transparent)))
                        }

                        drawPath(path, color, style = Stroke(width = if (isSecondary) 2.dp.toPx() else 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        
                        // Pontos inteligentes (Destaque nos picos se não houver seleção)
                        coords.forEachIndexed { index, offset ->
                            val isPeak = pts[index] == pts.maxOrNull()
                            val shouldShowPoint = selectedIndex == index || (selectedIndex == null && isPeak)
                            
                            if (shouldShowPoint && animationProgress >= (index.toFloat() / (pts.size - 1).coerceAtLeast(1).toFloat())) {
                                val scale = if (selectedIndex == index) 1.5f else 1.2f
                                drawCircle(Color.White, radius = 6.dp.toPx() * scale, center = offset)
                                drawCircle(if (selectedIndex == index) interactionColor else color, radius = 4.dp.toPx() * scale, center = offset)
                            }
                        }
                        
                        drawContext.canvas.restore()
                    }

                    if (grossPoints != null) drawLinePath(grossPoints, grossColor.copy(alpha=0.4f), true)
                    drawLinePath(netPoints, netColor)
                    
                    selectedIndex?.let { idx ->
                        drawLine(
                            color = interactionColor.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(idx * spacing, 0f),
                            end = androidx.compose.ui.geometry.Offset(idx * spacing, height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Legenda
            if (showGross) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    ChartLegendItem("Líquido", netColor)
                    Spacer(Modifier.width(20.dp))
                    ChartLegendItem("Bruto", grossColor)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    sortedHistory.forEachIndexed { index, item ->
                        if (index == 0 || index == sortedHistory.size - 1 || (sortedHistory.size <= 5)) {
                            Text(item.periodo.split(" ").firstOrNull() ?: "", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun DonationBanner(onDismiss: () -> Unit, onAction: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
        kotlinx.coroutines.delay(10000) // 10 segundos
        isVisible = false
        kotlinx.coroutines.delay(500)
        onDismiss()
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut(),
        modifier = androidx.compose.ui.Modifier
            .wrapContentWidth()
            .padding(top = 16.dp, start = 32.dp, end = 32.dp)
    ) {
        Surface(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 10.dp,
            shadowElevation = 15.dp,
            onClick = onAction
        ) {
            Row(
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = androidx.compose.ui.Modifier.size(18.dp)
                )
                
                Spacer(androidx.compose.ui.Modifier.width(12.dp))
                
                Text(
                    "Apoie o Dev: Remova os Anúncios!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                
                Spacer(androidx.compose.ui.Modifier.width(12.dp))
                
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = androidx.compose.ui.Modifier.size(14.dp)
                )
            }
        }
    }
}
