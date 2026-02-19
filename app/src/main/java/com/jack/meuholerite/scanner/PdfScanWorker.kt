package com.jack.meuholerite.scanner

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.PdfDocumentEntity

class PdfScanWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pdfScanner = PdfScanner(applicationContext)
        val foundPdfs = pdfScanner.scanForPdfs()
        val db = AppDatabase.getDatabase(applicationContext)

        Log.d("PdfScanWorker", "Found ${foundPdfs.size} PDFs in scan.")

        foundPdfs.forEach { uri ->
            val filePath = uri.toString()
            if (!db.pdfDocumentDao().exists(filePath)) {
                db.pdfDocumentDao().insert(PdfDocumentEntity(filePath = filePath, isProcessed = false))
                Log.i("PdfScanWorker", "New PDF found: $filePath")
            }
        }

        return Result.success()
    }
}
