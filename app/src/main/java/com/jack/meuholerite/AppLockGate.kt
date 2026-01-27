package com.jack.meuholerite

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.jack.meuholerite.utils.BiometricAuth
import com.jack.meuholerite.utils.StorageManager

@Composable
fun AppLockGate(
    storage: StorageManager,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity


    // lê do storage 1 vez ao abrir
    val lockEnabled = remember { storage.isAppLockEnabled() }
    var unlocked by remember { mutableStateOf(!lockEnabled) }

    // controla se o PIN deve aparecer
    var showPin by remember { mutableStateOf(false) }

    // 🔐 tenta biometria quando abrir
    LaunchedEffect(lockEnabled) {
        if (!lockEnabled) {
            unlocked = true
            return@LaunchedEffect
        }

        if (activity != null && BiometricAuth.canAuthenticate(activity)) {
            // enquanto tenta biometria, não mostra PIN
            showPin = false

            BiometricAuth.authenticate(
                activity = activity,
                onSuccess = {
                    unlocked = true
                    showPin = false
                },
                onFallback = {
                    // usuário cancelou / deu erro → cai pro PIN
                    showPin = true
                }
            )
        } else {
            showPin = true
        }
    }

    if (unlocked) {
        content()
        return
    }

    // ✅ se ainda não chegou no fallback, mostra "tela aguardando"
    if (!showPin) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Aguardando biometria…", fontSize = 18.sp)

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { showPin = true }) {
                    Text("Usar PIN")
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text("Sair")
                }
            }
        }
        return
    }

    // ✅ PIN UI (só aparece quando showPin=true)
    val hasPin = remember { storage.hasPin() }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val setupMode = !hasPin

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))

            Text(if (setupMode) "Criar PIN" else "Digite o PIN", fontSize = 22.sp)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
                label = { Text("PIN (4 a 6 dígitos)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            if (setupMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 6) confirm = it.filter(Char::isDigit) },
                    label = { Text("Confirmar PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (pin.length < 4) {
                        Toast.makeText(context, "PIN muito curto", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (setupMode) {
                        if (pin != confirm) {
                            Toast.makeText(context, "PINs não conferem", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        storage.setPin(pin)
                        unlocked = true
                    } else {
                        if (pin == storage.getPin()) unlocked = true
                        else Toast.makeText(context, "PIN incorreto", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (setupMode) "Salvar PIN" else "Desbloquear")
            }

            // 🧬 tentar biometria de novo (se disponível)
            if (activity != null && BiometricAuth.canAuthenticate(activity)) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        BiometricAuth.authenticate(
                            activity = activity,
                            onSuccess = { unlocked = true },
                            onFallback = {}
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Usar biometria")
                }
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { (context as? Activity)?.finish() }) {
                Text("Sair")
            }
        }
    }
}
