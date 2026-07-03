package com.jack.meuholerite.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.jack.meuholerite.database.FinanceDebtEntity
import com.jack.meuholerite.database.FinanceExpenseEntity
import com.jack.meuholerite.database.FinanceGoalEntity
import com.jack.meuholerite.utils.formatBrMoney
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FinancePdfGenerator(private val context: Context) {

    fun generateFinanceReport(
        netSalary: Double,
        expenses: List<FinanceExpenseEntity>,
        goals: List<FinanceGoalEntity>,
        debts: List<FinanceDebtEntity>
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()
        var yPosition = 50f

        // Title
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("Relatório Financeiro - Meu Holerite", 50f, yPosition, paint)
        yPosition += 30f

        // Date
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Gerado em: ${sdf.format(Date())}", 50f, yPosition, paint)
        yPosition += 40f

        // Summary Section
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        canvas.drawText("Resumo Geral", 50f, yPosition, paint)
        yPosition += 25f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        val totalExpenses = expenses.sumOf { it.value }
        val totalDebts = debts.filter { it.paidInstallments < it.totalInstallments }.sumOf { it.monthlyValue }
        val totalDeductions = totalExpenses + totalDebts
        val remaining = netSalary - totalDeductions

        canvas.drawText("Salário Líquido: R$ ${netSalary.formatBrMoney()}", 50f, yPosition, paint)
        yPosition += 20f
        canvas.drawText("Total de Despesas: R$ ${totalExpenses.formatBrMoney()}", 50f, yPosition, paint)
        yPosition += 20f
        canvas.drawText("Total de Dívidas (Mensal): R$ ${totalDebts.formatBrMoney()}", 50f, yPosition, paint)
        yPosition += 20f
        canvas.drawText("Saldo Restante: R$ ${remaining.formatBrMoney()}", 50f, yPosition, paint)
        yPosition += 30f

        // Financial Health Indicator
        val progress = if (netSalary > 0) (totalDeductions / netSalary).toFloat() else 0f
        val (healthText, healthColor) = when {
            remaining < 0 -> "CRÍTICO" to 0xFFFF3B30.toInt()
            progress < 0.5f -> "SAUDÁVEL" to 0xFF34C759.toInt()
            progress < 0.8f -> "ALERTA" to 0xFFFF9500.toInt()
            else -> "PERIGO" to 0xFFFF3B30.toInt()
        }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = healthColor
        canvas.drawText("SAÚDE FINANCEIRA: $healthText", 50f, yPosition, paint)
        paint.color = 0xFF000000.toInt() // Reset to black
        yPosition += 40f

        // Expenses Section
        if (expenses.isNotEmpty()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 16f
            canvas.drawText("Detalhamento de Despesas", 50f, yPosition, paint)
            yPosition += 25f

            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Descrição", 50f, yPosition, paint)
            canvas.drawText("Valor", 350f, yPosition, paint)
            canvas.drawText("Tipo", 480f, yPosition, paint)
            yPosition += 5f
            canvas.drawLine(50f, yPosition, 550f, yPosition, paint)
            yPosition += 20f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            expenses.forEach { expense ->
                if (yPosition > 800) return@forEach
                canvas.drawText(expense.description, 50f, yPosition, paint)
                canvas.drawText("R$ ${expense.value.formatBrMoney()}", 350f, yPosition, paint)
                canvas.drawText(if (expense.isFixed) "Fixa" else "Var.", 480f, yPosition, paint)
                yPosition += 20f
            }
            yPosition += 20f
        }

        // Debts Section
        val activeDebts = debts.filter { it.paidInstallments < it.totalInstallments }
        if (activeDebts.isNotEmpty()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 16f
            canvas.drawText("Dívidas e Financiamentos", 50f, yPosition, paint)
            yPosition += 25f

            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Descrição", 50f, yPosition, paint)
            canvas.drawText("Valor Bem", 180f, yPosition, paint)
            canvas.drawText("Total c/ Juros", 260f, yPosition, paint)
            canvas.drawText("Restante", 350f, yPosition, paint)
            canvas.drawText("Mensal", 430f, yPosition, paint)
            canvas.drawText("Juros", 500f, yPosition, paint)
            canvas.drawText("Parc.", 550f, yPosition, paint)
            yPosition += 5f
            canvas.drawLine(50f, yPosition, 580f, yPosition, paint)
            yPosition += 20f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            activeDebts.forEach { debt ->
                if (yPosition > 800) return@forEach
                val totalContract = debt.monthlyValue * debt.totalInstallments
                val remainingInst = debt.totalInstallments - debt.paidInstallments
                
                canvas.drawText(debt.description.take(20), 50f, yPosition, paint)
                canvas.drawText("R$ ${debt.totalAmount.formatBrMoney()}", 180f, yPosition, paint)
                canvas.drawText("R$ ${totalContract.formatBrMoney()}", 260f, yPosition, paint)
                canvas.drawText("R$ ${debt.remainingAmount.formatBrMoney()}", 350f, yPosition, paint)
                canvas.drawText("R$ ${debt.monthlyValue.formatBrMoney()}", 430f, yPosition, paint)
                canvas.drawText("${String.format("%.1f", debt.interestRate)}%", 500f, yPosition, paint)
                canvas.drawText("${debt.paidInstallments}/${debt.totalInstallments}", 550f, yPosition, paint)
                yPosition += 20f
            }
            yPosition += 20f
        }

        // Goals Section
        if (goals.isNotEmpty()) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 16f
            canvas.drawText("Metas de Economia", 50f, yPosition, paint)
            yPosition += 25f

            paint.textSize = 12f
            goals.forEach { goal ->
                val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount * 100).toInt() else 0
                canvas.drawText("${goal.title}: R$ ${goal.currentAmount.formatBrMoney()} / R$ ${goal.targetAmount.formatBrMoney()} ($progress%)", 50f, yPosition, paint)
                yPosition += 20f
            }
        }

        pdfDocument.finishPage(page)

        val directory = context.getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS).firstOrNull()
        val file = File(directory, "Relatorio_Financeiro_${System.currentTimeMillis()}.pdf")

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
