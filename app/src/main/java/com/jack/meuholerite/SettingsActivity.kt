package com.jack.meuholerite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.utils.UpdateManager
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storageManager = StorageManager(this)
        val updateManager = UpdateManager(this)
        val backupManager = BackupManager(this)
        
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }

        setContent {
            val systemInDarkTheme = isSystemInDarkTheme()
            var useDarkTheme by remember {
                val hasSet = storageManager.hasDarkModeSet()
                mutableStateOf(if (hasSet) storageManager.isDarkMode() else systemInDarkTheme)
            }

            MeuHoleriteTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = remember { getSharedPreferences("user_prefs", MODE_PRIVATE) }
                    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
                    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
                    var showEditProfile by remember { mutableStateOf(false) }
                    var autoUpdateInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) }

                    SettingsScreen(
                        storage = storageManager,
                        backupManager = backupManager,
                        currentVersion = currentVersion,
                        updateManager = updateManager,
                        onEditProfile = { showEditProfile = true },
                        onUpdateAvailable = { v, url, log -> autoUpdateInfo = Triple(v, url, log) },
                        isDarkTheme = useDarkTheme,
                        onToggleDarkMode = { enabled ->
                            storageManager.setDarkMode(enabled)
                            useDarkTheme = enabled
                        },
                        onBack = { finish() }
                    )

                    if (showEditProfile) {
                        EditProfileDialog(
                            initialName = userName,
                            initialMatricula = userMatricula,
                            onDismiss = { showEditProfile = false },
                            onSave = { name, matricula ->
                                userName = name
                                userMatricula = matricula
                                prefs.edit().putString("user_name", name).putString("user_matricula", matricula).apply()
                                showEditProfile = false
                            }
                        )
                    }

                    if (autoUpdateInfo != null) {
                        AlertDialog(
                            onDismissRequest = { autoUpdateInfo = null },
                            title = { Text("Nova Atualização v${autoUpdateInfo!!.first}", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("O que há de novo:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        autoUpdateInfo!!.third,
                                        fontSize = 13.sp,
                                        color = Color.DarkGray,
                                        lineHeight = 18.sp
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        updateManager.downloadAndInstall(autoUpdateInfo!!.second, autoUpdateInfo!!.first)
                                        autoUpdateInfo = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                ) { Text("Baixar Agora") }
                            },
                            dismissButton = {
                                TextButton(onClick = { autoUpdateInfo = null }) { Text("Depois") }
                            },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    storage: StorageManager,
    backupManager: BackupManager,
    currentVersion: String,
    updateManager: UpdateManager,
    onEditProfile: () -> Unit,
    onUpdateAvailable: (String, String, String) -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }

    var appLockEnabled by remember { mutableStateOf(storage.isAppLockEnabled()) }
    var versionTapCount by remember { mutableStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ☁️ BACKUP NA NUVEM
            item {
                SectionHeader("BACKUP NA NUVEM")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            label = if (backingUp) "Fazendo backup..." else "Fazer Backup Agora"
                        ) {
                            if (!backingUp) {
                                scope.launch {
                                    backingUp = true
                                    backupManager.backupData().onSuccess {
                                        Toast.makeText(context, "Backup concluído com sucesso!", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Falha no backup: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                    backingUp = false
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudDownload,
                            label = if (restoring) "Restaurando..." else "Restaurar Backup"
                        ) {
                            if (!restoring) {
                                scope.launch {
                                    restoring = true
                                    backupManager.restoreData().onSuccess {
                                        Toast.makeText(context, "Restauração concluída! Reinicie o app se necessário.", Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Falha na restauração: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                    restoring = false
                                }
                            }
                        }
                    }
                }
            }

            // 🔐 SEGURANÇA
            item {
                SectionHeader("SEGURANÇA")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Outlined.Lock,
                            label = "Senha / Biometria",
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                storage.setAppLockEnabled(enabled)
                                if (enabled && !storage.hasPin()) {
                                    Toast.makeText(context, "Configure um PIN no acesso inicial", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // 🌙 APARÊNCIA
            item {
                SectionHeader("APARÊNCIA")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Outlined.NightsStay,
                            label = "Modo Escuro",
                            checked = isDarkTheme,
                            onCheckedChange = onToggleDarkMode
                        )
                    }
                }
            }

            // 👤 CONTA
            item {
                SectionHeader("CONTA")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsActionRow(icon = Icons.Outlined.Person, label = "Editar Meus Dados") { onEditProfile() }
                    }
                }
            }

            // ℹ️ SOBRE
            item {
                SectionHeader("SOBRE")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                versionTapCount++
                                if (versionTapCount >= 7) { versionTapCount = 0; showEasterEgg = true }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Versão do App", fontSize = 12.sp, color = Color.Gray)
                                Text(currentVersion, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        SettingsActionRow(Icons.Outlined.Code, "Código Fonte (GitHub)") {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jacker-s/MeuHolerite")))
                        }
                    }
                }
            }

            // ⚙️ SISTEMA
            item {
                SectionHeader("SISTEMA")
                Button(
                    onClick = {
                        scope.launch {
                            checking = true
                            updateManager.checkForUpdates(
                                currentVersion = currentVersion,
                                onUpdateAvailable = onUpdateAvailable,
                                onNoUpdate = { Toast.makeText(context, "Você já está na versão mais recente.", Toast.LENGTH_SHORT).show() },
                                onError = { Toast.makeText(context, "Erro ao verificar atualizações.", Toast.LENGTH_SHORT).show() }
                            )
                            checking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !checking,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    if (checking) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Verificar Atualizações", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showEasterEgg) {
        AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            title = { Text("🎉 Easter Egg!") },
            text = { Text("Meu Holerite - Desenvolvido com ❤️\n Jackson") },
            confirmButton = { TextButton(onClick = { showEasterEgg = false }) { Text("Fechar") } }
        )
    }
}

@Composable
fun SettingsActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = Color.LightGray)
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF007AFF))
        )
    }
}
