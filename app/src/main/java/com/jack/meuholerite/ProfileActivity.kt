package com.jack.meuholerite

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.InformeRendimento
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageManager = StorageManager(this)

        setContent {
            val systemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            var useDarkTheme by remember {
                val hasSet = storageManager.hasDarkModeSet()
                mutableStateOf(if (hasSet) storageManager.isDarkMode() else systemInDarkTheme)
            }
            var themeAccent by remember { mutableStateOf(storageManager.getThemeAccent()) }

            MeuHoleriteTheme(darkTheme = useDarkTheme, themeAccent = themeAccent) {
                ProfileScreen(
                    onBack = { finish() },
                    onThemeRefresh = {
                        val hasSet = storageManager.hasDarkModeSet()
                        useDarkTheme = if (hasSet) storageManager.isDarkMode() else systemInDarkTheme
                        themeAccent = storageManager.getThemeAccent()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    onBack: () -> Unit,
    onThemeRefresh: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    val reciboEntities by db.reciboDao().getAllFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val espelhoEntities by db.espelhoDao().getAllFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val informeEntities by db.informeDao().getAllFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    val recibos = remember(reciboEntities) { reciboEntities.map { it.toModel(gson) } }
    val espelhos = remember(espelhoEntities) { espelhoEntities.map { it.toModel(gson) } }
    val informes = remember(informeEntities) { informeEntities.map { it.toModel() } }

    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
    var userCargo by remember { mutableStateOf(prefs.getString("user_cargo", "") ?: "") }
    var userPhoto by remember {
        mutableStateOf(
            prefs.getString("user_custom_photo", "")?.takeIf { it.isNotBlank() }
                ?: (prefs.getString("user_photo", "") ?: "")
        )
    }
    val userEmail = remember { prefs.getString("user_email", "") ?: "" }
    var showEditProfile by remember { mutableStateOf(false) }

    val latestRecibo = recibos.firstOrNull()
    val latestEspelho = espelhos.firstOrNull()
    val latestInforme = informes.firstOrNull()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            prefs.edit().putString("user_custom_photo", uri.toString()).apply()
            userPhoto = uri.toString()
            Toast.makeText(context, "Foto de perfil atualizada.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) { onThemeRefresh() }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = userName,
            initialMatricula = userMatricula,
            initialCargo = userCargo,
            onDismiss = { showEditProfile = false }
        ) { name, matricula, cargo ->
            prefs.edit()
                .putString("user_name", name)
                .putString("user_matricula", matricula)
                .putString("user_cargo", cargo)
                .apply()
            userName = name
            userMatricula = matricula
            userCargo = cargo
            showEditProfile = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Meu perfil", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfileAvatar(photo = userPhoto, size = 84.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    userName.ifBlank { latestRecibo?.funcionario ?: "Usuário" },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    userEmail.ifBlank { "Email não disponível" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileActionPill("Editar dados", Icons.Outlined.Edit) {
                                showEditProfile = true
                            }
                            ProfileActionPill("Trocar foto", Icons.Outlined.Image) {
                                photoPickerLauncher.launch(arrayOf("image/*"))
                            }
                            if (prefs.getString("user_custom_photo", "")?.isNotBlank() == true) {
                                ProfileActionPill("Remover foto", Icons.Outlined.PersonOutline) {
                                    prefs.edit().remove("user_custom_photo").apply()
                                    userPhoto = prefs.getString("user_photo", "") ?: ""
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileInfoCard("Cargo", userCargo.ifBlank { latestRecibo?.cargo ?: "Não informado" }, Icons.Outlined.WorkOutline, Modifier.weight(1f))
                    ProfileInfoCard("Matrícula", userMatricula.ifBlank { latestRecibo?.matricula ?: "Não informada" }, Icons.Outlined.Badge, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileInfoCard("Escala / jornada", latestEspelho?.jornada?.ifBlank { "Não identificada" } ?: "Não identificada", Icons.Outlined.Schedule, Modifier.weight(1f))
                    ProfileInfoCard("Empresa", latestRecibo?.empresa?.ifBlank { latestEspelho?.empresa ?: "Não informada" } ?: (latestEspelho?.empresa ?: "Não informada"), Icons.Outlined.Email, Modifier.weight(1f))
                }
            }

            item { ProfileSectionTitle("Resumo do histórico") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ProfileStatCard("Recibos", recibos.size.toString(), latestRecibo?.periodo ?: "Sem dados", Modifier.weight(1f))
                    ProfileStatCard("Espelhos", espelhos.size.toString(), latestEspelho?.periodo ?: "Sem dados", Modifier.weight(1f))
                    ProfileStatCard("Informes", informes.size.toString(), latestInforme?.anoCalendario ?: "Sem dados", Modifier.weight(1f))
                }
            }

            if (latestRecibo != null || latestEspelho != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        latestRecibo?.let { recibo ->
                            ProfileHighlightCard(
                                title = "Último holerite",
                                value = "Líquido R$ ${recibo.valorLiquido}",
                                subtitle = recibo.periodo,
                                modifier = Modifier.weight(1f)
                            )
                        } ?: Spacer(Modifier.weight(1f))

                        latestEspelho?.let { espelho ->
                            ProfileHighlightCard(
                                title = "Último ponto",
                                value = "Saldo BH ${espelho.saldoFinalBH}",
                                subtitle = espelho.periodo,
                                modifier = Modifier.weight(1f)
                            )
                        } ?: Spacer(Modifier.weight(1f))
                    }
                }
            }

        }
    }
}

@Composable
private fun ProfileAvatar(photo: String, size: androidx.compose.ui.unit.Dp) {
    if (photo.isNotBlank()) {
        AsyncImage(
            model = photo,
            contentDescription = "Foto do perfil",
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.PersonOutline,
                contentDescription = "Perfil",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.44f)
            )
        }
    }
}

@Composable
private fun ProfileActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileInfoCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ProfileStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProfileHighlightCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Black, fontSize = 19.sp)
}

@Composable
private fun ProfileHistorySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entries: List<String>
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }

            if (entries.isEmpty()) {
                Text("Nenhum dado disponível ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEach { entry ->
                    Text(
                        entry,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
