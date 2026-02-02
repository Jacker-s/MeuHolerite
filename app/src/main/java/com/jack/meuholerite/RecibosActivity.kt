package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.DeductionDetailDialog
import com.jack.meuholerite.ui.ReceiptItemCard
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecibosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageManager = StorageManager(this)
        
        setContent {
            val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = if (storageManager.hasDarkModeSet()) storageManager.isDarkMode() else systemInDarkTheme
            
            MeuHoleriteTheme(darkTheme = useDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RecibosScreenContent()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RecibosScreenContent() {
        val context = LocalContext.current
        val db = remember { AppDatabase.getDatabase(context) }
        val gson = remember { Gson() }
        val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
        val scope = rememberCoroutineScope()

        var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
        var selectedReciboItemForPopup by remember { mutableStateOf<Pair<ReciboItem, Boolean>?>(null) }
        var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
        var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }

        suspend fun refreshData() {
            withContext(Dispatchers.IO) {
                val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
                if (listRecibos.isNotEmpty()) {
                    selectedRecibo = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.first()
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshData()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.receipts)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        if (selectedRecibo != null) {
                            IconButton(onClick = { sharePdf(selectedRecibo?.pdfFilePath) }) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                ReceiptsScreen(
                    recibo = selectedRecibo,
                    db = db,
                    gson = gson,
                    userName = userName,
                    userMatricula = userMatricula,
                    onEditProfile = { /* Not applicable here */ },
                    onOpen = { sharePdf(it) },
                    onSelect = { selectedRecibo = it },
                    onRefresh = { scope.launch { refreshData() } },
                    onSelectItem = { item, isProvento -> selectedReciboItemForPopup = item to isProvento }
                )

                if (selectedReciboItemForPopup != null) {
                    DeductionDetailDialog(
                        item = selectedReciboItemForPopup!!.first,
                        isProvento = selectedReciboItemForPopup!!.second,
                        onDismiss = { selectedReciboItemForPopup = null }
                    )
                }
            }
        }
    }

    private fun sharePdf(filePath: String?) {
        if (filePath == null) {
            Toast.makeText(this, "Arquivo PDF não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "O arquivo físico não existe no armazenamento", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intentShare = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intentShare, "Exportar Holerite"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ReceiptsScreen(
    recibo: ReciboPagamento?,
    db: AppDatabase,
    gson: Gson,
    userName: String,
    userMatricula: String,
    onEditProfile: () -> Unit,
    onOpen: (String?) -> Unit,
    onSelect: (ReciboPagamento) -> Unit,
    onRefresh: () -> Unit,
    onSelectItem: (ReciboItem, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val recibosEntities by db.reciboDao().getAllFlow().collectAsState(initial = emptyList())
    val recibos = remember(recibosEntities) { recibosEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDateForRecibo() } }
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        ReceiptHistoryDialog(
            recibos = recibos,
            onDismiss = { showHistory = false },
            onSelect = { onSelect(it); showHistory = false },
            onDelete = {
                scope.launch {
                    db.reciboDao().deleteByPeriodo(it.periodo)
                    onRefresh()
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recibo?.periodo ?: stringResource(R.string.select_pdf),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 17.sp
                )
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Default.History, null, tint = Color(0xFF007AFF))
                }
            }
        }
        if (recibo != null) {
            item {
                ReceiptSummaryCard(
                    recibo = recibo,
                    userName = userName,
                    matricula = userMatricula,
                    onEdit = onEditProfile,
                    onOpen = { onOpen(recibo.pdfFilePath) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.earnings).uppercase(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recibo.proventos.forEach { item ->
                        ReceiptItemCard(item, Color(0xFF34C759)) { onSelectItem(item, true) }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.deductions).uppercase(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recibo.descontos.forEach { item ->
                        ReceiptItemCard(item, Color(0xFFFF3B30)) { onSelectItem(item, false) }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ReceiptSummaryCard(recibo: ReciboPagamento, userName: String, matricula: String, onEdit: () -> Unit, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF34C759), Color(0xFF248A3D)))).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userName.ifEmpty { "Usuário" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            if (matricula.isNotEmpty()) Text("Matrícula: $matricula", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.Edit, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onOpen) { Icon(Icons.Default.Share, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.net_pay), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("R$ ${recibo.valorLiquido}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.earnings), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("R$ ${recibo.totalProventos}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.deductions), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("R$ ${recibo.totalDescontos}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ReceiptHistoryDialog(recibos: List<ReciboPagamento>, onDismiss: () -> Unit, onSelect: (ReciboPagamento) -> Unit, onDelete: (ReciboPagamento) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(recibos) { recibo ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(recibo) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(recibo.periodo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Líquido: R$ ${recibo.valorLiquido}", color = Color(0xFF34C759), fontSize = 14.sp)
                        }
                        IconButton(onClick = { onDelete(recibo) }) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}
