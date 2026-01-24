package com.jack.meuholerite

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.meuholerite.utils.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AboutInfoRow(Icons.Default.Person, stringResource(R.string.developer_label), stringResource(R.string.developer_name))
                
                ContactActionRow(
                    icon = Icons.Default.Phone,
                    label = "Telefone",
                    value = stringResource(R.string.developer_phone),
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${context.getString(R.string.developer_phone)}"))
                        context.startActivity(intent)
                    }
                )

                ContactActionRow(
                    icon = Icons.Default.Email,
                    label = "E-mail",
                    value = stringResource(R.string.developer_email),
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${context.getString(R.string.developer_email)}"))
                        context.startActivity(intent)
                    }
                )

                AboutInfoRow(Icons.Default.Info, stringResource(R.string.version_label), currentVersion)

                Spacer(modifier = Modifier.height(8.dp))

                if (updateInfo != null) {
                    Surface(
                        color = Color(0xFF34C759).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Nova versão disponível: v${updateInfo!!.first}", fontWeight = FontWeight.Bold, color = Color(0xFF248A3D))
                            Button(
                                onClick = { updateManager.downloadAndInstall(updateInfo!!.second, updateInfo!!.first) },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                            ) {
                                Text("Atualizar Agora")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                checkingUpdate = true
                                updateManager.checkForUpdates(
                                    currentVersion = currentVersion,
                                    onUpdateAvailable = { version, url ->
                                        updateInfo = version to url
                                    },
                                    onNoUpdate = {
                                        Toast.makeText(context, "Você já está na versão mais recente!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Erro ao verificar atualizações", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                checkingUpdate = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !checkingUpdate,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (checkingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Verificar Atualizações")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
fun AboutInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ContactActionRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFFF2F2F7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = Color.Gray)
                Text("Toque para entrar em contato", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
    }
}
