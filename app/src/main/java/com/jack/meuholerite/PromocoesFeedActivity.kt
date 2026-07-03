package com.jack.meuholerite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jack.meuholerite.model.Promocao
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

private val PromoFeedBg = Color(0xFFF5F0E6)
private val PromoFeedInk = Color(0xFF12263A)
private val PromoFeedAccent = Color(0xFFDB6A2E)
private val PromoFeedGreen = Color(0xFF147A5C)
private val PromoFeedRose = Color(0xFFC24E63)

class PromocoesFeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeuHoleriteTheme {
                PromocoesFeedFullScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromocoesFeedFullScreen(onBack: () -> Unit) {
    var promocoes by remember { mutableStateOf<List<Promocao>>(emptyList()) }
    val destaque = promocoes.firstOrNull()

    DisposableEffect(Unit) {
        val ref = FirebaseFirestore.getInstance().collection("promocoes")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val now = System.currentTimeMillis()
            promocoes = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val expirada = data["expirada"] as? Boolean ?: false
                val expiraEm = (data["expiraEm"] as? Number)?.toLong() ?: 0L
                if (expirada || (expiraEm > 0 && expiraEm < now)) return@mapNotNull null
                Promocao(
                    id = doc.id,
                    titulo = data["titulo"] as? String ?: "",
                    descricao = data["descricao"] as? String ?: "",
                    imagemUrl = data["imagemUrl"] as? String ?: "",
                    precoAntes = (data["precoAntes"] as? Number)?.toDouble() ?: 0.0,
                    precoDepois = (data["precoDepois"] as? Number)?.toDouble() ?: 0.0,
                    link = data["link"] as? String ?: "",
                    loja = data["loja"] as? String ?: "",
                    cupom = data["cupom"] as? String ?: "",
                    verificado = data["verificado"] as? Boolean ?: true,
                    expirada = false,
                    expiraEm = expiraEm,
                    curtidas = (data["curtidas"] as? Number)?.toLong() ?: 0L,
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                )
            }.sortedByDescending { it.timestamp }
        }
        onDispose { listener.remove() }
    }

    Scaffold(
        containerColor = PromoFeedBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Feed de Promoções", fontWeight = FontWeight.Black, color = PromoFeedInk)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar", tint = PromoFeedInk)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PromoFeedBg
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PromoFeedBg)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF14283B),
                                        Color(0xFF234764),
                                        Color(0xFF2E6A52)
                                    )
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "ACESSO EXCLUSIVO",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.3.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFE9B44C)
                            ) {
                                Text(
                                    "FULL FEED",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = PromoFeedInk,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Fique ligado nas promoções e faça seu salário render ainda mais.",
                            color = Color.White,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            destaque?.let { "Descubra ofertas novas, acompanhe os melhores cupons e aproveite o destaque atual: ${it.titulo}" }
                                ?: "Descubra ofertas novas, acompanhe os melhores cupons e aproveite oportunidades que podem aliviar o seu mês.",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vitrine recente",
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = PromoFeedInk
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White
                    ) {
                        Text(
                            "${promocoes.size} ofertas",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PromoFeedInk
                        )
                    }
                }
            }

            items(promocoes) { promo ->
                PromocaoEditorialCard(promo = promo)
            }
        }
    }
}

@Composable
private fun PromocaoEditorialCard(promo: Promocao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasDiscount = promo.precoAntes > 0 && promo.precoDepois > 0
    val discountPct = if (hasDiscount) ((1.0 - promo.precoDepois / promo.precoAntes) * 100).toInt() else 0
    val tempoRestante = promoFeedTempoRestante(promo.expiraEm)
    var curtidas by remember(promo.id, promo.curtidas) { mutableStateOf(promo.curtidas) }
    var liked by remember(promo.id) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                if (promo.imagemUrl.isNotBlank()) {
                    AsyncImage(
                        model = promo.imagemUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE8D8BE), Color(0xFFD7E8E0))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = PromoFeedInk,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasDiscount && discountPct > 0) {
                        Surface(shape = RoundedCornerShape(999.dp), color = PromoFeedAccent) {
                            Text(
                                "-$discountPct%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (promo.verificado) {
                        Surface(shape = RoundedCornerShape(999.dp), color = PromoFeedInk) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Verificada", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (tempoRestante != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = PromoFeedInk, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(tempoRestante, color = PromoFeedInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(18.dp)) {
                if (promo.loja.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Store, contentDescription = null, tint = PromoFeedInk.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            promo.loja.uppercase(Locale.getDefault()),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PromoFeedInk.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    promo.titulo,
                    fontSize = 21.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Black,
                    color = PromoFeedInk
                )

                if (promo.descricao.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        promo.descricao,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = PromoFeedInk.copy(alpha = 0.72f)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (promo.precoDepois == 0.0) "Grátis" else "R$ ${"%.2f".format(promo.precoDepois)}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = PromoFeedGreen
                    )
                    if (promo.precoAntes > 0 && promo.precoDepois > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "R$ ${"%.2f".format(promo.precoAntes)}",
                            fontSize = 14.sp,
                            color = PromoFeedInk.copy(alpha = 0.4f),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                if (promo.cupom.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3E5C8),
                        border = BorderStroke(1.dp, Color(0xFFE4C98D))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clip = ClipData.newPlainText("cupom", promo.cupom)
                                    (context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
                                    Toast.makeText(context, "Cupom copiado!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Cupom: ${promo.cupom}",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = PromoFeedInk
                            )
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "Copiar cupom",
                                tint = PromoFeedInk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            val willLike = !liked
                            liked = willLike
                            curtidas = if (willLike) curtidas + 1 else (curtidas - 1).coerceAtLeast(0)
                            FirebaseFirestore.getInstance()
                                .collection("promocoes")
                                .document(promo.id)
                                .update("curtidas", com.google.firebase.firestore.FieldValue.increment(if (willLike) 1 else -1))
                                .addOnFailureListener {
                                    liked = !willLike
                                    curtidas = promo.curtidas
                                }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (liked) PromoFeedRose.copy(alpha = 0.12f) else Color(0xFFF3EFE7),
                        border = BorderStroke(1.dp, if (liked) PromoFeedRose.copy(alpha = 0.28f) else Color(0xFFE1D8CB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (liked) PromoFeedRose else PromoFeedInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$curtidas curtidas", fontWeight = FontWeight.Bold, color = PromoFeedInk, fontSize = 12.sp)
                        }
                    }
                    Surface(
                        onClick = { showComments = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3EFE7),
                        border = BorderStroke(1.dp, Color(0xFFE1D8CB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = PromoFeedInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Comentar", fontWeight = FontWeight.Bold, color = PromoFeedInk, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (promo.link.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(promo.link)))
                        }
                    },
                    enabled = promo.link.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (promo.loja.isNotBlank()) "Abrir oferta na ${promo.loja}" else "Abrir oferta",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    if (showComments) {
        PromoCommentsSheet(
            promo = promo,
            onDismiss = { showComments = false }
        )
    }
}

private fun promoFeedTempoRestante(expiraEm: Long): String? {
    if (expiraEm <= 0L) return null
    val remaining = expiraEm - System.currentTimeMillis()
    if (remaining <= 0L) return "Encerrando"
    val totalMinutes = remaining / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours >= 24 -> {
            val days = hours / 24
            if (days == 1L) "mais 1 dia" else "mais $days dias"
        }
        hours > 0 -> if (minutes > 0) "mais ${hours}h ${minutes}m" else "mais ${hours}h"
        else -> "mais ${minutes.coerceAtLeast(1)} min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromoCommentsSheet(
    promo: Promocao,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comentarios by remember { mutableStateOf<List<com.jack.meuholerite.model.Comentario>>(emptyList()) }
    var novoComentario by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erroComentario by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(promo.id) {
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("promocoes")
                .document(promo.id)
                .collection("comentarios")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            comentarios = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                com.jack.meuholerite.model.Comentario(
                    id = doc.id,
                    autor = data["autor"] as? String ?: "",
                    texto = data["texto"] as? String ?: "",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                )
            }
        } catch (_: Exception) {
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
        ) {
            Text("Comentários", fontSize = 20.sp, fontWeight = FontWeight.Black, color = PromoFeedInk)
            Text(
                promo.titulo,
                fontSize = 12.sp,
                color = PromoFeedInk.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            if (comentarios.isEmpty()) {
                Text(
                    "Seja o primeiro a comentar.",
                    color = PromoFeedInk.copy(alpha = 0.6f),
                    fontStyle = FontStyle.Italic
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(comentarios) { comentario ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8F5EF)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(comentario.autor, fontWeight = FontWeight.Bold, color = PromoFeedInk, fontSize = 12.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text(formatPromoCommentTime(comentario.timestamp), color = PromoFeedInk.copy(alpha = 0.5f), fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(comentario.texto, color = PromoFeedInk.copy(alpha = 0.82f), lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = novoComentario,
                    onValueChange = {
                        if (it.length <= 500) {
                            novoComentario = it
                            if (erroComentario != null) erroComentario = null
                        }
                    },
                    placeholder = { Text("Escreva um comentário") },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp)
                )
                IconButton(
                    onClick = {
                        val texto = novoComentario.trim()
                        if (texto.isBlank()) return@IconButton
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser == null) {
                            erroComentario = "Faça login para comentar."
                            return@IconButton
                        }
                        enviando = true
                        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        val autor = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: "Anônimo"
                        val timestamp = System.currentTimeMillis()
                        scope.launch {
                            try {
                                val ref = FirebaseFirestore.getInstance()
                                    .collection("promocoes")
                                    .document(promo.id)
                                    .collection("comentarios")
                                    .document()
                                ref.set(
                                    hashMapOf(
                                        "autor" to autor,
                                        "texto" to texto,
                                        "timestamp" to timestamp,
                                        "promoId" to promo.id,
                                        "uid" to currentUser.uid,
                                        "userId" to currentUser.uid
                                    )
                                ).await()
                                comentarios = listOf(
                                    com.jack.meuholerite.model.Comentario(
                                        id = ref.id,
                                        autor = autor,
                                        texto = texto,
                                        timestamp = timestamp
                                    )
                                ) + comentarios
                                novoComentario = ""
                                erroComentario = null
                                Toast.makeText(context, "Comentário enviado.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                erroComentario = e.message ?: "Não foi possível comentar."
                            } finally {
                                enviando = false
                            }
                        }
                    },
                    enabled = !enviando && novoComentario.trim().isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    if (enviando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PromoFeedInk)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = PromoFeedInk)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                erroComentario ?: "${novoComentario.length}/500",
                color = if (erroComentario != null) MaterialTheme.colorScheme.error else PromoFeedInk.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatPromoCommentTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60000
    val h = diff / 3600000
    val d = diff / 86400000
    return when {
        min < 1 -> "agora"
        min < 60 -> "${min}m"
        h < 24 -> "${h}h"
        d < 7 -> "${d}d"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale("pt", "BR")).format(java.util.Date(timestamp))
    }
}
