package com.jack.meuholerite.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.jack.meuholerite.FinanceActivity
import com.jack.meuholerite.R
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.utils.extractStartDateForRecibo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FinanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.jack.meuholerite.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, FinanceWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.finance_widget_layout)

        // Abrir app ao clicar no widget
        val intent = Intent(context, FinanceActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val gson = Gson()
            
            val expenses = db.financeExpenseDao().getAll()
            val totalExpenses = expenses.sumOf { it.value }
            
            val recibos = db.reciboDao().getAll().map { it.toModel(gson) }
            val latestRecibo = recibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.firstOrNull()
            
            val netSalary = latestRecibo?.valorLiquido?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val remaining = netSalary - totalExpenses
            
            val calendar = Calendar.getInstance()
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val daysLeft = (daysInMonth - dayOfMonth).coerceAtLeast(1)
            val dailyBudget = if (remaining > 0) remaining / daysLeft else 0.0

            withContext(Dispatchers.Main) {
                views.setTextViewText(R.id.widget_balance, "R$ ${String.format("%.2f", remaining)}")
                views.setTextViewText(R.id.widget_daily, "R$ ${String.format("%.2f", dailyBudget)}")
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
