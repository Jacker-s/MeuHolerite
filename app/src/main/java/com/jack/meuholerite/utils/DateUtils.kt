package com.jack.meuholerite.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun String.extractStartDate(): Date {
    val dateRegex = """\d{2}/\d{2}/\d{4}""".toRegex()
    return try {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateRegex.find(this)?.value ?: "") ?: Date(0)
    } catch (_: Exception) {
        Date(0)
    }
}

fun String.extractEndDate(): Date {
    val dateRegex = """\d{2}/\d{2}/\d{4}""".toRegex()
    val matches = dateRegex.findAll(this).toList()
    return try {
        if (matches.size >= 2) {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(matches[1].value) ?: Date(0)
        } else {
            Date(0)
        }
    } catch (_: Exception) {
        Date(0)
    }
}

fun calculateRemainingWorkDays(endDate: Date): Int {
    val today = Calendar.getInstance()
    val end = Calendar.getInstance()
    end.time = endDate
    
    if (today.after(end)) return 0
    
    var count = 0
    val tempCalendar = today.clone() as Calendar
    while (tempCalendar.before(end) || (tempCalendar.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR) && tempCalendar.get(Calendar.YEAR) == end.get(Calendar.YEAR))) {
        val dayOfWeek = tempCalendar.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
            count++
        }
        tempCalendar.add(Calendar.DAY_OF_MONTH, 1)
    }
    return count
}

fun calculateStandardHours(jornada: String): Int {
    val timeRegex = """\d{2}:\d{2}""".toRegex()
    val times = timeRegex.findAll(jornada).map { it.value }.toList()
    if (times.size < 2) return 8 * 60 // Default 8h
    
    return try {
        var totalMinutes = 0
        for (i in 0 until (times.size / 2)) {
            val start = times[i * 2]
            val end = times[i * 2 + 1]
            totalMinutes += timeToMinutes(end) - timeToMinutes(start)
        }
        if (totalMinutes <= 0) 8 * 60 else totalMinutes
    } catch (_: Exception) {
        8 * 60
    }
}

fun timeToMinutes(time: String): Int {
    val clean = time.trim().replace(" ", "")
    val isNegative = clean.startsWith("-")
    val parts = clean.replace("-", "").split(":")
    if (parts.size < 2) return 0
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts[1].toIntOrNull() ?: 0
    val total = h * 60 + m
    return if (isNegative) -total else total
}

fun String.extractStartDateForRecibo(): Date {
    val monthsMap = mapOf(
        "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04",
        "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08",
        "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
    )
    val text = this.uppercase()
    val nameMatch = """([A-Z]{3})\s+(\d{4})""".toRegex().find(text)
    if (nameMatch != null) {
        val monthName = nameMatch.groupValues[1]
        val year = nameMatch.groupValues[2]
        val monthNum = monthsMap[monthName]
        if (monthNum != null) return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$monthNum/$year") ?: Date(0)
        } catch (_: Exception) {
            Date(0)
        }
    }
    val dateRegex = """(\d{2})/(\d{4})""".toRegex()
    val match = dateRegex.find(this)
    if (match != null) {
        val month = match.groupValues[1]
        val year = match.groupValues[2]
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$month/$year") ?: Date(0)
        } catch (_: Exception) {
            Date(0)
        }
    }
    return try {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(this) ?: Date(0)
    } catch (_: Exception) {
        Date(0)
    }
}
