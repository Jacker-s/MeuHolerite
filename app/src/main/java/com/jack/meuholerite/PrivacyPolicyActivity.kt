package com.jack.meuholerite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrivacyPolicyScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Altere aqui se quiser expor um e-mail/URL oficial:
    val contactEmail = "suporte@meuholerite.app"
    val policyUrl = "" // ex: "https://meuholerite.app/privacidade"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de Privacidade", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (policyUrl.isNotBlank()) {
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir no navegador")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrivacyHeaderCard(
                title = "Meu Holerite",
                subtitle = "Transparência sobre como seus dados são tratados no app.",
            )

            PrivacyQuickFacts()

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacySectionCard(
                        icon = Icons.Default.Folder,
                        title = "1. Coleta de Dados",
                        content =
                            "O Meu Holerite não coleta nem vende seus dados pessoais para servidores de terceiros. " +
                                    "Os arquivos importados (holerites/recibos e espelhos de ponto) são processados no próprio dispositivo."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.Storage,
                        title = "2. Armazenamento no Dispositivo",
                        content =
                            "Os PDFs e as informações extraídas ficam no armazenamento privado do aplicativo. " +
                                    "Você pode apagar esses dados a qualquer momento nas configurações."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.Cloud,
                        title = "3. Backup na Nuvem (Opcional)",
                        content =
                            "Se você ativar o backup/restauração, os dados poderão ser enviados para a nuvem vinculada à sua conta. " +
                                    "Para sua segurança, informações sensíveis podem ser criptografadas antes do envio. " +
                                    "Você pode desativar e apagar o backup quando quiser."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.Security,
                        title = "4. Segurança e Criptografia",
                        content =
                            "Quando disponível, o app utiliza criptografia para proteger informações sensíveis e " +
                                    "armazenamento privado do Android para reduzir o risco de acesso por outros apps."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.VpnKey,
                        title = "5. Permissões do App",
                        content =
                            "• Internet: portal ePays e backup/restauração.\n" +
                                    "• Notificações: alertas (ex.: faltas/avisos).\n" +
                                    "• Biometria/credenciais do dispositivo (se ativado): bloqueio do app.\n" +
                                    "O app solicita apenas o necessário para as funcionalidades escolhidas por você."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.AccountCircle,
                        title = "6. Contas e Login",
                        content =
                            "Ao usar login (ex.: Google/Firebase), identificadores da conta podem ser usados para " +
                                    "autenticar você e associar backups ao seu usuário."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.DeleteForever,
                        title = "7. Exclusão de Dados",
                        content =
                            "Você pode limpar os dados locais e/ou apagar o backup na nuvem pelas configurações. " +
                                    "Ao sair da conta e limpar dados, os PDFs locais também podem ser removidos do dispositivo."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.Update,
                        title = "8. Alterações nesta Política",
                        content =
                            "Esta política pode ser atualizada para refletir melhorias do app ou mudanças legais. " +
                                    "Ao continuar usando o app após uma atualização, você concorda com a versão vigente."
                    )

                    PrivacySectionCard(
                        icon = Icons.Default.AdsClick,
                        title = "9. Anúncios e Serviços de Terceiros",
                        content =
                            "O app utiliza o Google AdMob para exibição de anúncios e o Firebase para análises de uso e erros. " +
                                    "Essas ferramentas coletam identificadores anônimos para fins técnicos e publicitários, " +
                                    "sujeitos às políticas de privacidade da Google (https://policies.google.com/privacy)."
                    )
                }
            }

            PrivacyContactCard(
                contactEmail = contactEmail,
                onEmail = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$contactEmail")
                            putExtra(Intent.EXTRA_SUBJECT, "Privacidade - Meu Holerite")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            FooterUpdated(dateText = "Última atualização: Abril de 2026")
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacyHeaderCard(
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyQuickFacts() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Em resumo",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FactChip(icon = Icons.Default.PhoneAndroid, text = "Processamento local", modifier = Modifier.weight(1f))
                FactChip(icon = Icons.Default.CloudDone, text = "Backup opcional", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FactChip(icon = Icons.Default.Lock, text = "Armazenamento privado", modifier = Modifier.weight(1f))
                FactChip(icon = Icons.Default.Delete, text = "Excluir quando quiser", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FactChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PrivacySectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    var expanded by remember { mutableStateOf(true) } // já abre expandido, bem informativo
    val interaction = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { expanded = !expanded }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }

            if (expanded) {
                Text(
                    content,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun PrivacyContactCard(
    contactEmail: String,
    onEmail: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Contato",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Se tiver dúvidas sobre privacidade ou quiser solicitar exclusão de dados, fale com a gente.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Surface(
                onClick = onEmail,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("E-mail", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(contactEmail, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable
private fun FooterUpdated(dateText: String) {
    Text(
        text = dateText,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}
