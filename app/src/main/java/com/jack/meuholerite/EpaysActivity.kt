package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.ui.EpaysWebViewPage
import com.jack.meuholerite.ui.FullscreenPdfViewerDialog
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.unit.dp
import androidx.activity.viewModels
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jack.meuholerite.viewmodel.EpaysViewModel
import com.jack.meuholerite.viewmodel.ImportState

class EpaysActivity : ComponentActivity() {

    private val viewModel: EpaysViewModel by viewModels()


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
                        
            MeuHoleriteTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("ePays", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    val context = LocalContext.current
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        val importState by viewModel.importState.collectAsState()
                        var importSuccessData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
                        var showSignReminder by remember { mutableStateOf(false) }

                        LaunchedEffect(importState) {
                            when (val state = importState) {
                                is ImportState.Success -> {
                                    importSuccessData = Triple(state.type, state.id, state.path)
                                    viewModel.resetState()
                                }
                                is ImportState.Error -> {
                                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                                    viewModel.resetState()
                                }
                                is ImportState.Loading -> {
                                    // Podemos mostrar um status na UI se desejar
                                    Log.d("EpaysActivity", "Status: ${state.message}")
                                }
                                else -> {}
                            }
                        }

                        val startUrl = remember { intent.getStringExtra("startUrl") }

                        EpaysWebViewPage(startUrl = startUrl) { uri, _ ->
                            Toast.makeText(context, "PDF Detectado! Processando...", Toast.LENGTH_SHORT).show()
                            viewModel.handleImport(uri)
                        }

                        // O ImportingDialog foi removido para permitir importação em segundo plano sem bloquear a WebView.

                        importSuccessData?.let { (type, id, path) ->
                            FullscreenPdfViewerDialog(
                                type = type,
                                filePath = path,
                                onConfirm = {
                                    importSuccessData = null
                                    val targetIntent = when (type) {
                                        "RECIBO" -> Intent(this@EpaysActivity, RecibosActivity::class.java)
                                        "INFORME" -> Intent(this@EpaysActivity, InformesActivity::class.java)
                                        else -> Intent(this@EpaysActivity, PontoActivity::class.java)
                                    }
                                    startActivity(targetIntent)
                                    finish()
                                },
                                onDismiss = {
                                    val wasPonto = type == "PONTO"
                                    importSuccessData = null
                                    if (wasPonto) {
                                        showSignReminder = true
                                    }
                                }
                            )
                        }

                        if (showSignReminder) {
                            AlertDialog(
                                onDismissRequest = { showSignReminder = false },
                                title = { Text("Assinar Ponto?", fontWeight = FontWeight.Bold) },
                                text = { Text("Se as informações do espelho de ponto estiverem corretas, lembre-se de realizar a assinatura agora mesmo.") },
                                confirmButton = {
                                    Button(onClick = { showSignReminder = false }) {
                                        Text("Entendido")
                                    }
                                },
                                shape = RoundedCornerShape(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerPaymentNotification(context: Context, periodo: String, data: String) {
        com.jack.meuholerite.showPaymentNotification(context, periodo, data)
    }
}
