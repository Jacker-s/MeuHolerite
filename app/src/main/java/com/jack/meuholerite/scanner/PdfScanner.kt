package com.jack.meuholerite.scanner

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfScanner(private val context: Context) {

    suspend fun scanForPdfs(): List<Uri> = withContext(Dispatchers.IO) {
        val pdfUris = mutableListOf<Uri>()
        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    pdfUris.add(contentUri)
                }
            }
        } catch (e: Exception) {
            Log.e("PdfScanner", "Erro ao escanear PDFs: ${e.message}")
        }
        return@withContext pdfUris
    }
}
