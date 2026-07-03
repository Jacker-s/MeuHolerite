package com.jack.meuholerite.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FullscreenPdfViewerDialog(
    type: String,
    filePath: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Animações para tornar o zoom "fácil" e suave
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = scale,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "scale"
    )
    val animatedOffset by androidx.compose.animation.core.animateOffsetAsState(
        targetValue = offset,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "offset"
    )

    val pdfFile = remember(filePath) { File(filePath) }
    var pageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(pdfFile) {
        if (pdfFile.exists() && filePath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    Log.e("FullscreenPdfViewer", "Error reading PDF page count", e)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (type == "RECIBO") "Confirmar Holerite" else if (type == "INFORME") "Confirmar Informe" else "Confirmar Ponto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = {
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                pdfFile
                            )
                            val intentShare = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                this.type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intentShare, "Compartilhar PDF"))
                        } catch (e: Exception) {
                            Log.e("FullscreenPdfViewer", "Error sharing PDF", e)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                    }

                    IconButton(onClick = { 
                        if (scale != 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                // PDF Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Gray.copy(alpha = 0.05f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 8f) // Aumentado para 8x para facilitar ver detalhes
                                if (newScale != scale) {
                                    scale = newScale
                                }
                                if (scale > 1f) {
                                    val maxX = (size.width * (scale - 1)) / 2
                                    val maxY = (size.height * (scale - 1)) / 2
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 3f
                                        // Tenta centralizar no ponto do toque para um zoom mais "inteligente"
                                        val x = (size.width / 2 - tapOffset.x) * 1.5f
                                        val y = (size.height / 2 - tapOffset.y) * 1.5f
                                        offset = Offset(x, y)
                                    }
                                }
                            )
                        }
                ) {
                    if (pageCount > 0) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = animatedScale,
                                    scaleY = animatedScale,
                                    translationX = animatedOffset.x,
                                    translationY = animatedOffset.y
                                )
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            userScrollEnabled = scale < 1.1f // Permite um pouco de folga no scroll
                        ) {
                            items(pageCount) { index ->
                                PdfPageItem(pdfFile, index)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                // Footer (Opcional, se precisar de botões fixos embaixo)
                if (type == "RECIBO" || type == "INFORME") {
                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) {
                            Text("Sim, está OK", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageItem(file: File, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val renderScale = 2.5f // Reduzido de 4.5f para 2.5f para melhor performance e menor uso de memória

    LaunchedEffect(file, index) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                if (index < renderer.pageCount) {
                    val page = renderer.openPage(index)
                    val width = (page.width * renderScale).toInt()
                    val height = (page.height * renderScale).toInt()
                    
                    // Usamos ARGB_8888 para qualidade, mas o scale menor já reduz drasticamente o peso
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = b
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                Log.e("FullscreenPdfViewer", "Error rendering page $index", e)
            }
        }
    }

    Surface(
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${index + 1}",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Proporção A4 aproximada
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
