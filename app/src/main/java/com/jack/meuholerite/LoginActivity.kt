package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.jack.meuholerite.ui.AnimatedAppIcon

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        if (prefs.getBoolean("is_logged_in", false) && auth.currentUser != null) {
            val destination = if (prefs.getBoolean("terms_accepted", false)) {
                MainActivity::class.java
            } else {
                TermsAgreementActivity::class.java
            }
            startActivity(Intent(this, destination))
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
                        val destination = if (prefs.getBoolean("terms_accepted", false)) {
                            MainActivity::class.java
                        } else {
                            TermsAgreementActivity::class.java
                        }
                        val intent = Intent(this, destination)
                        intent.putExtra("JUST_LOGGED_IN", true)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    val db = remember { AppDatabase.getDatabase(context) }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        // Barra de topo com botões premium
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Política de Privacidade
            Surface(
                onClick = { 
                    context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Privacidade",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botão de Informações Colorido
            Surface(
                onClick = { showInfoDialog = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Informações",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo e Título com animação premium
            AnimatedAppIcon(size = 140)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Meu Holerite",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1).sp
            )
            
            Text(
                text = "Bem-vindo ao Meu Holerite",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Área de Login
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "login_area"
            ) { loading ->
                if (!loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = {
                                isLoading = true
                                loadingText = "Autenticando..."
                                scope.launch {
                                    try {
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(context.getString(R.string.default_web_client_id))
                                            .setAutoSelectEnabled(false)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context, request)
                                        val (email, name, photoUrl) = handleGoogleCredentialWithFirebase(result.credential)

                                        loadingText = "Sincronizando conta..."
                                        com.jack.meuholerite.service.newFunction(context)

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
                            modifier = Modifier.size(68.dp),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = "Continuar com Google",
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            loadingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Entendi")
                    }
                },
                title = { Text("Sobre o Meu Holerite", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "O Meu Holerite é seu assistente financeiro pessoal. " +
                        "Importe seus recibos de pagamento para analisar Proventos, Descontos, FGTS e muito mais. " +
                        "Seus dados são processados localmente e criptografados para sua segurança.",
                        lineHeight = 20.sp
                    )
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun LanguageCard(
    flagResId: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 1.0f, label = "scale")
    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0.6f, label = "alpha")
    
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        label = "color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(38.dp)
            .width(68.dp)
            .clip(RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .shadow(elevation = if (isSelected) 6.dp else 0.dp, shape = RoundedCornerShape(19.dp), spotColor = MaterialTheme.colorScheme.primary),
        color = containerColor,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = flagResId),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .alpha(alpha),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
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

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MeuHoleriteTheme {
        LoginScreen(onLoginSuccess = { _, _, _ -> })
    }
}
