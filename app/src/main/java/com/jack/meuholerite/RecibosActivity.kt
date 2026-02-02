package com.jack.meuholerite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.launch
import java.io.File
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider

class RecibosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageManager = StorageManager(this)
        setContent {
            val useDarkTheme = storageManager.isDarkMode()
            MeuHoleriteTheme(darkTheme = useDarkTheme) {
                RecibosScreenContent()
            }
        }
    }

    @Composable
    fun RecibosScreenContent() {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val gson = remember { Gson() }
        val prefs = remember { context.getSharedPreferences("user_prefs", MODE_PRIVATE) }
        val scope = rememberCoroutineScope()

        var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
        var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
        var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
        var selectedReciboItemForPopup by remember { mutableStateOf<Pair<ReciboItem, Boolean>?>(null) }

        LaunchedEffect(Unit) {
            val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
            if (listRecibos.isNotEmpty()) {
                selectedRecibo = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.first()
            }
        }

        Scaffold { padding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
                ReceiptsScreen(
                    recibo = selectedRecibo,
                    db = db,
                    gson = gson,
                    userName = userName,
                    userMatricula = userMatricula,
                    onEditProfile = { /* No-op or handle if needed */ },
                    onOpen = { openPdf(it) },
                    onSelect = { selectedRecibo = it },
                    onRefresh = {
                        scope.launch {
                            val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
                            if (listRecibos.isNotEmpty()) {
                                selectedRecibo = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.first()
                            }
                        }
                    },
                    onSelectItem = { item, isProvento -> selectedReciboItemForPopup = item to isProvento }
                )

                if (selectedReciboItemForPopup != null) {
                    DeductionDetailDialog(selectedReciboItemForPopup!!.first, selectedReciboItemForPopup!!.second) {
                        selectedReciboItemForPopup = null
                    }
                }
            }
        }
    }

    private fun openPdf(filePath: String?) {
        if (filePath == null) return
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intentView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            startActivity(Intent.createChooser(intentView, "Abrir PDF com..."))
        } catch (_: Exception) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }
}
