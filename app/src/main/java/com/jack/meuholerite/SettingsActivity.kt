package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.google.firebase.auth.FirebaseAuth
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storageManager = StorageManager(this)
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

                    SettingsScreen(
                        storage = storageManager,
                        backupManager = backupManager,
                        currentVersion = currentVersion,
                        userName = userName,
                        userMatricula = userMatricula,
                        onEditProfile = { showEditProfile = true },
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
    userName: String,
    userMatricula: String,
    onEditProfile: () -> Unit,
    isDarkTheme: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser

    var appLockEnabled by remember { mutableStateOf(storage.isAppLockEnabled()) }
    var versionTapCount by remember { mutableStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.language), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(id = R.string.close))
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // 👤 SEÇÃO: MEU PERFIL
            item {
                SectionHeader(stringResource(id = R.string.onboarding_finish_title).uppercase())
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onEditProfile() }.padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(userName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Matrícula: $userMatricula", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Text("Idioma do App", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LanguageFlagItem(R.drawable.ic_flag_br, "Português") { changeAppLanguage("pt-BR") }
                            LanguageFlagItem(R.drawable.ic_flag_ve, "Español") { changeAppLanguage("es-VE") }
                        }
                    }
                }
            }

            // 🛠️ SEÇÃO: PREFERÊNCIAS E SEGURANÇA
            item {
                SectionHeader("PREFERÊNCIAS E SEGURANÇA")
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
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsToggleRow(
                            icon = Icons.Outlined.Lock,
                            label = "Senha / Biometria ao Abrir",
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                storage.setAppLockEnabled(enabled)
                                if (enabled && !storage.hasPin()) {
                                    Toast.makeText(context, "Configure seu PIN no acesso inicial", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // ☁️ SEÇÃO: DADOS E BACKUP
            item {
                SectionHeader("DADOS E BACKUP")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (currentUser != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                Icon(Icons.Outlined.CloudDone, null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Nuvem: ${currentUser.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            label = if (backingUp) "Fazendo backup..." else "Fazer Backup Agora",
                            color = Color(0xFF007AFF)
                        ) {
                            if (currentUser == null) {
                                Toast.makeText(context, "Faça login para ativar o backup.", Toast.LENGTH_LONG).show()
                                return@SettingsActionRow
                            }
                            if (!backingUp) {
                                scope.launch {
                                    backingUp = true
                                    backupManager.backupData().onSuccess {
                                        Toast.makeText(context, "Backup concluído!", Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Falha: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                    backingUp = false
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudDownload,
                            label = if (restoring) "Restaurando..." else "Restaurar da Nuvem",
                            color = Color(0xFF5856D6)
                        ) {
                            if (currentUser == null) {
                                Toast.makeText(context, "Faça login para restaurar dados.", Toast.LENGTH_LONG).show()
                                return@SettingsActionRow
                            }
                            if (!restoring) {
                                scope.launch {
                                    restoring = true
                                    backupManager.restoreData().onSuccess {
                                        Toast.makeText(context, "Restauração concluída!", Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        Toast.makeText(context, "Falha: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                    restoring = false
                                }
                            }
                        }
                    }
                }
            }

            // ⚠️ SEÇÃO: CONTA E LIMPEZA
            item {
                SectionHeader("CONTA E LIMPEZA")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsActionRow(Icons.Outlined.Logout, "Sair da Conta", MaterialTheme.colorScheme.primary) { showLogoutConfirm = true }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(Icons.Outlined.DeleteForever, "Apagar Todos os Dados", Color(0xFFFF3B30)) { showDeleteConfirm = true }
                    }
                }
            }

            // ℹ️ SEÇÃO: SOBRE
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
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Versão", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(currentVersion, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(Icons.Outlined.Code, "Código Fonte (GitHub)", Color(0xFF8E8E93)) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jacker-s/MeuHolerite")))
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sair da Conta?", fontWeight = FontWeight.Bold) },
            text = { Text("Isso removerá seu acesso e limpará os arquivos salvos localmente (PDFs, banco de dados e cookies). Certifique-se de ter feito backup na nuvem.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                db.clearAllTables()
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                val pdfDir = File(context.filesDir, "pdfs")
                                if (pdfDir.exists()) pdfDir.deleteRecursively()
                                withContext(Dispatchers.Main) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                }
                                FirebaseAuth.getInstance().signOut()
                            }
                            showLogoutConfirm = false
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        }
                    }
                ) { Text("Sair e Limpar") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Apagar todos os dados?", fontWeight = FontWeight.Bold) },
            text = { Text("Isso removerá permanentemente todos os holerites, pontos e configurações locais, além do seu backup na nuvem.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            backupManager.deleteBackup()
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                db.clearAllTables()
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                val pdfDir = File(context.filesDir, "pdfs")
                                if (pdfDir.exists()) pdfDir.deleteRecursively()
                                withContext(Dispatchers.Main) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                }
                                FirebaseAuth.getInstance().signOut()
                            }
                            showDeleteConfirm = false
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) { Text("Apagar Tudo", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(22.dp)
        )
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
fun LanguageFlagItem(resId: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(48.dp).clip(CircleShape),
            shape = CircleShape,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun changeAppLanguage(languageCode: String) {
    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
    AppCompatDelegate.setApplicationLocales(appLocale)
}

@Composable
fun SettingsActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp).background(Color(0xFF007AFF).copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF007AFF))
        )
    }
}
