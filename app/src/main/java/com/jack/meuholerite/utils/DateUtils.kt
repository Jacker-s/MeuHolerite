package com.jack.meuholerite.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ─── Feriados nacionais FIXOS do Brasil (dd/MM) ───────────────────────────────
// Não incluímos feriados móveis (Carnaval, Corpus Christi, Páscoa) pois dependem
// do ano, mas os fixos já cobrem a maioria dos casos.
private val FERIADOS_FIXOS = setOf(
    "01/01", // Ano Novo
    "21/04", // Tiradentes
    "01/05", // Trabalho
    "07/09", // Independência
    "12/10", // N. Sra. Aparecida
    "02/11", // Finados
    "15/11", // Proclamação da República
    "20/11", // Consciência Negra (lei federal)
    "25/12"  // Natal
)

/**
 * Retorna true se [cal] não for um dia útil para esta empresa.
 * REGRA: Sábado é considerado dia útil. Apenas domingo e feriados nacionais
 * fixos são excluídos da contagem.
 */
private fun Calendar.isFeriadoOuFimDeSemana(): Boolean {
    val dow = get(Calendar.DAY_OF_WEEK)
    if (dow == Calendar.SUNDAY) return true          // domingo: não útil
    // sábado NÃO é excluído — empresa conta como dia útil
    val key = "%02d/%02d".format(get(Calendar.DAY_OF_MONTH), get(Calendar.MONTH) + 1)
    return key in FERIADOS_FIXOS
}

/**
 * Calcula o N-ésimo dia útil de um mês/ano.
 * @param mes 1-12
 * @param ano  ex.: 2026
 * @param nesimoUtil número do dia útil desejado (padrão = 5)
 * @return [Calendar] apontando para o dia, ou null se o mês não tiver dias úteis suficientes.
 */
fun calcularNesimoDiaUtil(mes: Int, ano: Int, nesimoUtil: Int = 5): Calendar? {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, ano)
        set(Calendar.MONTH, mes - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    var count = 0
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    while (cal.get(Calendar.DAY_OF_MONTH) <= maxDays) {
        if (!cal.isFeriadoOuFimDeSemana()) {
            count++
            if (count == nesimoUtil) return cal
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        if (cal.get(Calendar.MONTH) != mes - 1) break // ultrapassou o mês
    }
    return null
}

/**
 * Resultado da previsão de pagamento.
 */
data class ProximoPagamento(
    /** Data formatada "dd/MM/yyyy" do próximo pagamento */
    val dataFormatada: String,
    /** Mês de referência do trabalho que será pago (ex: "Abril/2026") */
    val mesReferencia: String,
    /** Dias restantes até o pagamento (0 = hoje, negativo = já passou) */
    val diasRestantes: Int,
    /** Mês/ano em que o pagamento cai (para exibição) */
    val mesAno: String
)

/**
 * Calcula o próximo pagamento com base nas regras:
 * - Pagamento sempre no 5º dia útil do mês M
 * - Refere-se ao trabalho do mês M-1
 * - Espelho de ponto emitido todo dia 16, referente a 15/M-1 → 16/M
 * - Se hoje já passou o 5º dia útil do mês atual → próximo pagamento é no mês que vem
 * - Se hoje é dia 16+, o espelho de hoje só será pago no 5º dia útil do próximo mês
 *
 * @param hoje [Calendar] representando a data atual (padrão = hoje)
 */
fun calcularProximoPagamento(hoje: Calendar = Calendar.getInstance()): ProximoPagamento {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val sdfMes = SimpleDateFormat("MMMM/yyyy", Locale("pt", "BR"))

    val diaHoje = hoje.get(Calendar.DAY_OF_MONTH)
    val mesHoje = hoje.get(Calendar.MONTH) + 1 // 1-12
    val anoHoje = hoje.get(Calendar.YEAR)

    // O pagamento deste mês (M) refere-se ao trabalho de M-1.
    // Mas se hoje já é dia 16+, o espelho de ponto de M foi emitido HOJE
    // e só será pago no 5º dia útil de M+1.
    // Além disso, se já passamos do 5º dia útil do mês atual, o próximo pagamento
    // é no mês que vem.

    // Determina o mês de pagamento candidato
    val quintoDiaUtilMesAtual = calcularNesimoDiaUtil(mesHoje, anoHoje)

    val mesPagamento: Int
    val anoPagamento: Int

    if (quintoDiaUtilMesAtual != null && !hoje.after(quintoDiaUtilMesAtual) && diaHoje < 16) {
        // Ainda não chegou o 5º dia útil do mês atual E ainda não chegou o dia 16:
        // o pagamento deste mês (referente ao mês passado) ainda está por vir.
        mesPagamento = mesHoje
        anoPagamento = anoHoje
    } else {
        // Ou já passamos do 5º dia útil, ou já é dia 16+ (espelho de hoje só pago no próximo mês)
        // → próximo pagamento é no mês seguinte
        if (mesHoje == 12) {
            mesPagamento = 1
            anoPagamento = anoHoje + 1
        } else {
            mesPagamento = mesHoje + 1
            anoPagamento = anoHoje
        }
    }

    val quintoDiaUtil = calcularNesimoDiaUtil(mesPagamento, anoPagamento)
        ?: Calendar.getInstance().apply {
            set(Calendar.YEAR, anoPagamento)
            set(Calendar.MONTH, mesPagamento - 1)
            set(Calendar.DAY_OF_MONTH, 5)
        }

    // Mês de referência do trabalho (mês anterior ao pagamento)
    val calRef = Calendar.getInstance().apply {
        set(Calendar.YEAR, anoPagamento)
        set(Calendar.MONTH, mesPagamento - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, -1)
    }

    val dataFormatada = sdf.format(quintoDiaUtil.time)
    val mesReferencia = sdfMes.format(calRef.time)
        .replaceFirstChar { it.uppercase() }
    val mesAno = sdfMes.format(quintoDiaUtil.time)
        .replaceFirstChar { it.uppercase() }

    val diffMs = quintoDiaUtil.timeInMillis - hoje.timeInMillis
    val diasRestantes = (diffMs / (1000L * 60 * 60 * 24)).toInt()

    return ProximoPagamento(
        dataFormatada = dataFormatada,
        mesReferencia = mesReferencia,
        diasRestantes = diasRestantes,
        mesAno = mesAno
    )
}

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
    return try {
        val clean = time.trim().replace(" ", "").replace("+", "")
        val isNegative = clean.startsWith("-")
        val parts = clean.replace("-", "").split(":")
        if (parts.size < 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val total = h * 60 + m
        if (isNegative) -total else total
    } catch (e: Exception) {
        0
    }
}

fun minutesToTime(totalMinutos: Int): String {
    val isNegative = totalMinutos < 0
    val absMinutos = kotlin.math.abs(totalMinutos)
    val hours = absMinutos / 60
    val minutes = absMinutos % 60
    return "${if (isNegative) "-" else ""}${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
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
