package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
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
import com.jack.meuholerite.utils.BackupManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        if (prefs.getBoolean("is_logged_in", false) && auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            MeuHoleriteTheme {
                LoginScreen(
                    onLoginSuccess = { email, name, photoUrl ->
                        prefs.edit().apply {
                            putBoolean("is_logged_in", true)
                            putString("user_email", email)
                            putString("user_name", name)
                            putString("user_photo", photoUrl)
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
fun LoginScreen(onLoginSuccess: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    val backupManager = remember { BackupManager(context) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageFlag(
                    resId = R.drawable.ic_flag_br,
                    contentDescription = "Português",
                    onClick = { changeLanguage("pt-BR") }
                )
                Spacer(modifier = Modifier.width(24.dp))
                LanguageFlag(
                    resId = R.drawable.ic_flag_ve,
                    contentDescription = "Español",
                    onClick = { changeLanguage("es-VE") }
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(id = R.string.onboarding_welcome_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isLoading) {
                CircularProgressIndicator()
                if (loadingText.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(loadingText, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        loadingText = "Autenticando..."
                        scope.launch {
                            try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    .setAutoSelectEnabled(true)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(context, request)
                                val (email, name, photoUrl) = handleGoogleCredentialWithFirebase(result.credential)
                                
                                loadingText = "Restaurando seus dados da nuvem..."
                                backupManager.restoreData().onSuccess {
                                    Log.d("LoginActivity", "Restauração automática concluída")
                                }.onFailure {
                                    Log.e("LoginActivity", "Restauração falhou: ${it.message}")
                                }

                                onLoginSuccess(email, name, photoUrl)

                            } catch (e: GetCredentialException) {
                                Log.e("LoginActivity", "Error getting credential", e)
                                Toast.makeText(context, "Login cancelado.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("LoginActivity", "Unexpected login error", e)
                                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                                loadingText = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(id = R.drawable.ic_google), "Google", tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Entrar com Google", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageFlag(resId: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onClick() },
        shape = CircleShape,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Image(painterResource(id = resId), contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private fun changeLanguage(languageCode: String) {
    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
    AppCompatDelegate.setApplicationLocales(appLocale)
}

private suspend fun handleGoogleCredentialWithFirebase(credential: Credential): Triple<String, String, String> {
    val googleIdTokenCredential = when (credential) {
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data)
            } else {
                throw IllegalStateException("Credencial inesperada.")
            }
        }
        else -> throw IllegalStateException("Tipo de credencial inesperado.")
    }

    val idToken = googleIdTokenCredential.idToken
    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
    val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
    val user = authResult.user ?: throw IllegalStateException("Falha ao obter usuário.")

    return Triple(
        user.email ?: "sem-email@google",
        user.displayName ?: "",
        user.photoUrl?.toString() ?: ""
    )
}
