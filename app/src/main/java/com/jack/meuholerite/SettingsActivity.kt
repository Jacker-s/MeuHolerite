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
import androidx.compose.material.icons.automirrored.outlined.*
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
import coil.compose.AsyncImage
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
                    val userPhoto by remember { mutableStateOf(prefs.getString("user_photo", "") ?: "") }
                    var showEditProfile by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    SettingsScreen(
                        storage = storageManager,
                        backupManager = backupManager,
                        currentVersion = currentVersion,
                        userName = userName,
                        userPhoto = userPhoto,
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
                                prefs.edit()
                                    .putString("user_name", name)
                                    .putString("user_matricula", matricula)
                                    .apply()
                                showEditProfile = false
                                scope.launch { backupManager.backupData() }
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
    userPhoto: String,
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
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
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
                SectionHeader(stringResource(R.string.profile_section))
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
                            if (userPhoto.isNotEmpty()) {
                                AsyncImage(
                                    model = userPhoto,
                                    contentDescription = stringResource(R.string.profile_photo),
                                    modifier = Modifier.size(50.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(userName.ifEmpty { stringResource(R.string.user_label) }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(currentUser?.email ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Text(stringResource(R.string.app_language), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LanguageFlagItem(R.drawable.ic_flag_br, stringResource(R.string.portuguese)) { changeAppLanguage("pt-BR") }
                            LanguageFlagItem(R.drawable.ic_flag_ve, stringResource(R.string.spanish)) { changeAppLanguage("es-VE") }
                        }
                    }
                }
            }

            // 🛠️ SEÇÃO: PREFERÊNCIAS E SEGURANÇA
            item {
                SectionHeader(stringResource(R.string.preferences_security))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsToggleRow(
                            icon = Icons.Outlined.NightsStay,
                            label = stringResource(R.string.dark_mode),
                            checked = isDarkTheme,
                            onCheckedChange = onToggleDarkMode
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsToggleRow(
                            icon = Icons.Outlined.Lock,
                            label = stringResource(R.string.app_lock),
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                storage.setAppLockEnabled(enabled)
                                if (enabled && !storage.hasPin()) {
                                    Toast.makeText(context, context.getString(R.string.pin_toast), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(
                            icon = Icons.Outlined.BeachAccess,
                            label = stringResource(R.string.vacation_management),
                            color = Color(0xFF34C759)
                        ) {
                            context.startActivity(Intent(context, VacationActivity::class.java))
                        }
                    }
                }
            }

            // ☁️ SEÇÃO: DADOS E BACKUP
            item {
                SectionHeader(stringResource(R.string.backup_section))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            label = if (backingUp) stringResource(R.string.backing_up) else stringResource(R.string.backup_now),
                            color = Color(0xFF007AFF)
                        ) {
                            if (currentUser == null) {
                                Toast.makeText(context, context.getString(R.string.backup_needed_toast), Toast.LENGTH_LONG).show()
                                return@SettingsActionRow
                            }
                            if (!backingUp) {
                                scope.launch {
                                    backingUp = true
                                    backupManager.backupData().onSuccess {
                                        Toast.makeText(context, context.getString(R.string.backup_success_toast), Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        Toast.makeText(context, "${context.getString(R.string.update_error)}: ${it.message}", Toast.LENGTH_LONG).show()
                                    }
                                    backingUp = false
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudDownload,
                            label = if (restoring) stringResource(R.string.restoring) else stringResource(R.string.restore_cloud),
                            color = Color(0xFF5856D6)
                        ) {
                            if (currentUser == null) {
                                Toast.makeText(context, context.getString(R.string.restore_needed_toast), Toast.LENGTH_LONG).show()
                                return@SettingsActionRow
                            }
                            if (!restoring) {
                                scope.launch {
                                    restoring = true
                                    backupManager.restoreData().onSuccess {
                                        Toast.makeText(context, context.getString(R.string.restore_success_toast), Toast.LENGTH_LONG).show()
                                    }.onFailure {
                                        Toast.makeText(context, "${context.getString(R.string.update_error)}: ${it.message}", Toast.LENGTH_LONG).show()
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
                SectionHeader(stringResource(R.string.account_section))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsActionRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.logout), MaterialTheme.colorScheme.primary) { showLogoutConfirm = true }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(Icons.Outlined.DeleteForever, stringResource(R.string.delete_data), Color(0xFFFF3B30)) { showDeleteConfirm = true }
                    }
                }
            }

            // ℹ️ SEÇÃO: SOBRE
            item {
                SectionHeader(stringResource(R.string.about_section))
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
                                Text(stringResource(R.string.version_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(currentVersion, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(Icons.Outlined.Code, stringResource(R.string.source_code_github), Color(0xFF8E8E93)) {
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
            title = { Text(stringResource(R.string.logout_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.logout_confirm)) },
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
                ) { Text(stringResource(R.string.exit_and_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_confirm)) },
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
                ) { Text(stringResource(R.string.delete_all), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showEasterEgg) {
        AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            title = { Text("🎉 Easter Egg!") },
            text = { Text("Meu Holerite - Desenvolvido com ❤️\n Jackson") },
            confirmButton = { TextButton(onClick = { showEasterEgg = false }) { Text(stringResource(R.string.close)) } }
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
