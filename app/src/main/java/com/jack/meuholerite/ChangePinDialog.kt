package com.jack.meuholerite

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.StorageManager

@Composable
fun ChangePinDialog(
    storage: StorageManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val hasPin = remember { storage.hasPin() }

    ChangePinDialogContent(
        hasPin = hasPin,
        onDismiss = onDismiss,
        onConfirm = { currentPin, newPin, confirm ->
            if (hasPin && currentPin != storage.getPin()) {
                Toast.makeText(context, "PIN atual incorreto", Toast.LENGTH_SHORT).show()
                return@ChangePinDialogContent
            }
            if (newPin.length < 4) {
                Toast.makeText(context, "Novo PIN muito curto", Toast.LENGTH_SHORT).show()
                return@ChangePinDialogContent
            }
            if (newPin != confirm) {
                Toast.makeText(context, "PINs não conferem", Toast.LENGTH_SHORT).show()
                return@ChangePinDialogContent
            }

            storage.setPin(newPin)
            storage.setAppLockEnabled(true)
            Toast.makeText(context, "PIN atualizado", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    )
}

@Composable
fun ChangePinDialogContent(
    hasPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String, confirmPin: String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Trocar PIN" else "Criar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                if (hasPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 6) currentPin = it.filter(Char::isDigit) },
                        label = { Text("PIN atual") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6) newPin = it.filter(Char::isDigit) },
                    label = { Text("Novo PIN (4 a 6 dígitos)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 6) confirm = it.filter(Char::isDigit) },
                    label = { Text("Confirmar PIN") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentPin, newPin, confirm) }
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Preview(showBackground = true)
@Composable
fun ChangePinDialogPreview() {
    MeuHoleriteTheme {
        ChangePinDialogContent(
            hasPin = true,
            onDismiss = {},
            onConfirm = { _, _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CreatePinDialogPreview() {
    MeuHoleriteTheme {
        ChangePinDialogContent(
            hasPin = false,
            onDismiss = {},
            onConfirm = { _, _, _ -> }
        )
    }
}
