package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme

class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                PrivacyPolicyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Política de Privacidade", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Política de Privacidade - Meu Holerite",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PrivacySection(
                title = "1. Coleta de Dados",
                content = "O aplicativo Meu Holerite não coleta dados pessoais em servidores externos. Todos os dados importados (holerites e espelhos de ponto) são processados e armazenados localmente no seu dispositivo."
            )

            PrivacySection(
                title = "2. Armazenamento",
                content = "Os arquivos PDF e as informações extraídas são salvos no armazenamento privado do aplicativo. Você tem total controle sobre esses dados, podendo apagá-los a qualquer momento nas configurações."
            )

            PrivacySection(
                title = "3. Backup na Nuvem",
                content = "Caso você opte por utilizar a função de backup, os dados serão criptografados e enviados para o seu próprio Google Drive ou Firebase associado à sua conta, garantindo que apenas você tenha acesso."
            )

            PrivacySection(
                title = "4. Permissões",
                content = "O app solicita permissões para acessar a internet (para o portal ePays e backup), biometria (para proteção de acesso) e notificações (para alertas de falta)."
            )

            PrivacySection(
                title = "5. Alterações",
                content = "Esta política pode ser atualizada ocasionalmente. O uso continuado do app após alterações constitui sua aceitação dos novos termos."
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Última atualização: Fevereiro de 2024",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PrivacySection(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = content, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
