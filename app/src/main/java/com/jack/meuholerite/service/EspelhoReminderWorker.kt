package com.jack.meuholerite.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jack.meuholerite.showEspelhoReminderNotification
import com.jack.meuholerite.showHoleriteReminderNotification
import java.util.Calendar

class EspelhoReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val calendar = Calendar.getInstance()
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        when (dayOfMonth) {
            4 -> showHoleriteReminderNotification(applicationContext, isFifthDay = false)
            5 -> showHoleriteReminderNotification(applicationContext, isFifthDay = true)
            16 -> showEspelhoReminderNotification(applicationContext)
        }

        return Result.success()
    }
}
