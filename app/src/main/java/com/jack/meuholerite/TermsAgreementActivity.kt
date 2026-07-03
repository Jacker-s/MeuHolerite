package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme

class TermsAgreementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        setContent {
            MeuHoleriteTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TermsAgreementScreen(
                        onAccept = {
                            prefs.edit().putBoolean("terms_accepted", true).apply()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAgreementScreen(onAccept: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ao clicar em aceitar, você concorda com nossos termos e política de privacidade.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Aceitar e Continuar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Termos e Privacidade",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Leia atentamente como tratamos seus dados antes de começar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

            TermsSection(
                title = "1. Privacidade em Primeiro Lugar",
                content = "O Meu Holerite não coleta seus dados financeiros em servidores próprios. " +
                        "Toda a extração de dados dos seus PDFs ocorre localmente no seu dispositivo."
            )

            TermsSection(
                title = "2. Armazenamento Seguro",
                content = "Seus holerites e dados extraídos são salvos no armazenamento privado do Android, " +
                        "protegidos contra o acesso de outros aplicativos."
            )

            TermsSection(
                title = "3. Backup Opcional",
                content = "Você tem a opção de sincronizar seus dados com o Google Drive e Firebase. " +
                        "Isso é opcional e serve apenas para restaurar seus dados em outro dispositivo."
            )

            TermsSection(
                title = "4. Uso de Cookies e Anúncios",
                content = "Utilizamos o Google AdMob para exibir anúncios e o Firebase para análises técnicas anônimas. " +
                        "Isso nos ajuda a manter o aplicativo gratuito."
            )

            TermsSection(
                title = "5. Responsabilidade",
                content = "Os cálculos realizados pelo app são baseados nas informações extraídas dos documentos fornecidos. " +
                        "Sempre confira os valores com seu departamento de RH em caso de dúvidas."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TermsSection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
