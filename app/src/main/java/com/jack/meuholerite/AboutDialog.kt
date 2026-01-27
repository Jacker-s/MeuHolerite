package com.jack.meuholerite

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jack.meuholerite.utils.UpdateManager
import kotlinx.coroutines.launch

// Helper para encontrar a Activity a partir do Contexto
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

data class DetailedUpdateInfo(
    val version: String,
    val url: String,
    val changeLog: String
)




@Composable
fun AboutMainContent(
    currentVersion: String,
    updateInfo: DetailedUpdateInfo?,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onUpdateNow: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.padding(24.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ContactActionRow(Icons.Default.Code, stringResource(R.string.source_code_label)) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.github_url)))
                context.startActivity(intent)
            }

            AboutInfoRow(Icons.Default.Info, stringResource(R.string.version_label), currentVersion)

            if (updateInfo != null) {
                Surface(color = Color(0xFF34C759).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.new_version_available, updateInfo.version), fontWeight = FontWeight.Bold, color = Color(0xFF248A3D))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.whats_new), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(updateInfo.changeLog, fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onUpdateNow, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_now))
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onCheckUpdate, enabled = !checkingUpdate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    if (checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.check_updates))
                }
            }
        }
    }
}

@Composable
fun AboutInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ContactActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF2F2F7),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}
