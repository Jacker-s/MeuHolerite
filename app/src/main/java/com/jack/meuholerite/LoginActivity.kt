package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_logged_in", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            MeuHoleriteTheme {
                LoginScreen(
                    onLoginSuccess = { name, email ->
                        prefs.edit().apply {
                            putBoolean("is_logged_in", true)
                            putString("user_name", name)
                            putString("user_email", email)
                            apply()
                        }
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Bem-vindo ao\nMeu Holerite",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Acesse seus holerites e banco de horas de forma simples e segura.",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    // Se ficar dando "auto select" estranho, mude para false
                                    .setAutoSelectEnabled(true)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(
                                    context = context,
                                    request = request
                                )

                                val (name, email) = handleGoogleCredentialWithFirebase(result.credential)
                                onLoginSuccess(name, email)

                            } catch (e: GetCredentialException) {
                                Log.e("LoginActivity", "Error getting credential", e)
                                Toast.makeText(context, "Login cancelado ou falhou.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("LoginActivity", "Unexpected login error", e)
                                Toast.makeText(context, "Erro no login: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Troque por um ícone Google real (ex: R.drawable.ic_google)
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Entrar com Google",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Corrige o seu erro "Unexpected credential type".
 * No Credential Manager, o retorno geralmente é CustomCredential.
 * Aí extraímos o GoogleIdTokenCredential via createFrom(credential.data),
 * e logamos no Firebase para obter email/nome com consistência.
 */
private suspend fun handleGoogleCredentialWithFirebase(credential: Credential): Pair<String, String> {
    val googleIdTokenCredential = when (credential) {
        is CustomCredential -> {
            // Precisa ser o tipo do Google ID Token
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data)
            } else {
                throw IllegalStateException("Credencial inesperada (type=${credential.type}).")
            }
        }
        else -> {
            // Pode acontecer se o sistema retornar outro tipo (password/passkey)
            throw IllegalStateException("Tipo de credencial inesperado: ${credential::class.java.simpleName}")
        }
    }

    val idToken = googleIdTokenCredential.idToken

    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
    val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
    val user = authResult.user ?: throw IllegalStateException("Falha ao obter usuário do Firebase.")

    val name = user.displayName ?: "Usuário"
    val email = user.email ?: "sem-email@google"

    return name to email
}
