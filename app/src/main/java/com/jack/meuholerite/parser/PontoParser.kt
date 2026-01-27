package com.jack.meuholerite.parser

import com.jack.meuholerite.model.EspelhoItem
import com.jack.meuholerite.model.EspelhoPonto

class PontoParser {
    fun parse(text: String): EspelhoPonto {
        val empresa = text.split("\n").firstOrNull { it.isNotBlank() }?.trim() ?: "Empresa não identificada"

        val funcionario = "FUNCIONARIO\\s+\\d+\\s+-\\s+([^\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim() ?: "Não encontrado"
            
        val periodo = "Período\\s+([^\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim() ?: "Não encontrado"

        // Extração de Jornada (Horários) considerando quebras de linha
        val jornadaRegex = "(?:Horário\\s+Padronizado|Jornada):?\\s*((?:\\d{2}:\\d{2}[\\s\\n]*)+)".toRegex(RegexOption.IGNORE_CASE)
        val jornada = jornadaRegex.find(text)?.groupValues?.get(1)?.trim()?.replace("\\s+".toRegex(), " ") ?: ""

        // Extração de Jornada Realizada considerando quebras de linha
        val jornadaRealizadaRegex = "Jornada\\s+Realizada:?\\s*([\\d:\\s\\n]+)".toRegex(RegexOption.IGNORE_CASE)
        val jornadaRealizadaRaw = jornadaRealizadaRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""
        val jornadaRealizada = if (jornadaRealizadaRaw.contains(":")) {
            jornadaRealizadaRaw.split("\\s+".toRegex()).firstOrNull { it.contains(":") } ?: ""
        } else ""

        val itens = mutableListOf<EspelhoItem>()
        val labelsProcessadas = mutableSetOf<String>()
        
        // Mapeamento dos campos para chaves de recursos de string
        val camposDesejados = mapOf(
            "HORAS TRABALHADAS" to "label_worked_hours",
            "ADICIONAL NOTURNO" to "label_night_allowance",
            "ATRASO NO INTERVALO" to "label_interval_delay",
            "SAIDA ANTECIPADA" to "label_early_departure",
            "HORAS EXTRAS 50%" to "label_extra_hours_50",
            "HORAS EXTRAS 100%" to "label_extra_hours_100",
            "ABONO" to "label_excused_absence",
            "FALTAS" to "label_absences"
        )
        
        val metricRegex = "(\\d{2,}\\s*:\\s*\\d{2})\\s+([^\\n\\d]+(?:\\d+%)?)".toRegex()
        
        val matches = metricRegex.findAll(text).toList().reversed()
        
        for (match in matches) {
            val rawValue = match.groupValues[1]
            val originalLabel = match.groupValues[2].trim()
            val normalizedLabel = originalLabel.uppercase()

            if (normalizedLabel.contains("DSR")) continue

            val entry = camposDesejados.entries.find { normalizedLabel.contains(it.key) } ?: continue
            val resourceKey = entry.value
            
            if (labelsProcessadas.contains(resourceKey)) continue

            val formattedValue = formatTime(rawValue)
            val isNegative = normalizedLabel.contains("ATRASO") || 
                            normalizedLabel.contains("SAIDA") || 
                            normalizedLabel.contains("FALTA")
            
            itens.add(EspelhoItem(resourceKey, formattedValue, isNegative))
            labelsProcessadas.add(resourceKey)
        }

        // Se jornadaRealizada não foi encontrada pelo padrão específico, podemos usar o valor de HORAS TRABALHADAS se disponível
        val finalJornadaRealizada = if (jornadaRealizada.isEmpty()) {
            itens.find { it.label == "label_worked_hours" }?.value ?: ""
        } else {
            jornadaRealizada
        }

        // Busca mais robusta pelo saldo final do banco de horas
        val patternsSaldoFinal = listOf(
            "=\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(),
            "SALDO ATUAL\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "SALDO FINAL\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "TOTAL\\s+BANCO\\s+HORAS\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "SALDO\\s+BANCO\\s+HORAS\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "BANCO\\s+HORAS\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        var saldoFinalRaw = "0:00"
        for (pattern in patternsSaldoFinal) {
            val match = pattern.findAll(text).lastOrNull()
            if (match != null) {
                saldoFinalRaw = match.groupValues[1]
                break
            }
        }
        val saldoFinal = formatTime(saldoFinalRaw)
        
        val saldoPeriodoBH = "SALDO DO PERÍODO\\s+([-|+]?\\s*\\d+\\s*:\\s*\\d{2})".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { formatTime(it) } ?: "0:00"

        // Extracao de dias com falta - Ignorando explicitamente DSR
        val diasFaltas = mutableListOf<String>()
        val diaRegex = "(\\d{2}/\\d{2}/\\d{4})((?!DSR).)*?(FALTA)".toRegex(RegexOption.IGNORE_CASE)
        diaRegex.findAll(text).forEach {
            val data = it.groupValues[1]
            diasFaltas.add(data)
        }

        val hasAbsences = diasFaltas.isNotEmpty()

        var detalhesBH = "SALDO ANTERIOR[^\\n]+".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(0) ?: ""
            
        detalhesBH = detalhesBH.replace("DSR", "", ignoreCase = true)
            .replace("()", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return EspelhoPonto(
            funcionario = funcionario,
            empresa = empresa,
            periodo = periodo,
            jornada = jornada,
            jornadaRealizada = finalJornadaRealizada,
            resumoItens = itens.reversed(),
            saldoFinalBH = saldoFinal,
            saldoPeriodoBH = saldoPeriodoBH,
            detalhesSaldoBH = detalhesBH,
            hasAbsences = hasAbsences,
            diasFaltas = diasFaltas.distinct()
        )
    }

    private fun formatTime(time: String): String {
        val cleaned = time.replace("\\s".toRegex(), "")
        val isNegative = cleaned.contains("-")
        val absoluteTime = cleaned.replace("[-+]".toRegex(), "")

        val parts = absoluteTime.split(":")
        if (parts.size < 2) return "0:00"

        val rawHours = parts[0].trimStart('0')
        val hours = if (rawHours.isEmpty()) "0" else rawHours
        val minutes = parts[1].take(2).padStart(2, '0')

        return "${if (isNegative) "-" else ""}$hours:$minutes"
    }
}
