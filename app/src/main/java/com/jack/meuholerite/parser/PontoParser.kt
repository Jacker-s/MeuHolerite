package com.jack.meuholerite.parser

import com.jack.meuholerite.model.EspelhoItem
import com.jack.meuholerite.model.EspelhoPonto

class PontoParser {
    fun parse(text: String): EspelhoPonto {
        val empresa = text.split("\n").firstOrNull { it.isNotBlank() }?.trim() ?: "Empresa não identificada"

        val funcionarioMatch = "FUNCIONARIO\\s+(\\d+)\\s+-\\s+([^\\n]+)".toRegex(RegexOption.IGNORE_CASE).find(text)
        val matricula = funcionarioMatch?.groupValues?.get(1) ?: ""
        val funcionario = funcionarioMatch?.groupValues?.get(2)?.trim() ?: "Não encontrado"
        
        val cargo = "(?:CARGO|FUN[CÇ][ÃA]O)[:\\s]+([^\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim() ?: ""
            
        val periodo = "Período\\s+([^\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim() ?: "Não encontrado"

        // 1. Extração de Jornada - Focando na estrutura padrão de escalas
        val timeRegex = "\\d{2}:\\d{2}".toRegex()
        val jornadaRegex = "(?:Horário\\s+Padronizado|Jornada|Horas\\s+Trabalhadas)[:\\s]*((?:\\d{2,}:\\d{2}[\\s\\n]*)+)".toRegex(RegexOption.IGNORE_CASE)
        var jornada = ""
        jornadaRegex.findAll(text).forEach { match ->
            val times = timeRegex.findAll(match.groupValues[1]).map { it.value }.toList()
            if (times.size >= 2 && jornada.isEmpty()) {
                jornada = times.take(4).joinToString(" ")
            }
        }

        // 2. Extração de Itens (Metricas) com proteção contra falsos positivos
        val itens = mutableListOf<EspelhoItem>()
        val labelsProcessadas = mutableSetOf<String>()
        val camposDesejados = mapOf(
            "HORAS TRABALHADAS" to "label_worked_hours",
            "ADICIONAL NOTURNO" to "label_night_allowance",
            "ATRASO NO INTERVALO" to "label_interval_delay",
            "SAIDA ANTECIPADA" to "label_early_departure",
            "HORAS EXTRAS 50%" to "label_extra_hours_50",
            "HORAS EXTRAS 100%" to "label_extra_hours_100",
            "ABONO" to "label_excused_absence",
            "TROCA DE UNIFORME" to "label_bh_uniform",
            "FOLGA COMP. TROCA DE UNIFORME" to "label_bh_uniform_off",
            "FALTAS" to "label_absences"
        )

        // Regex aprimorada: busca o VALOR seguido do NOME (comum em layouts de coluna)
        val metricRegex = "(\\d{2,}\\s*:\\s*\\d{2})\\s+([^\\n\\d]{3,}(?:\\d+%)?)".toRegex()
        metricRegex.findAll(text).toList().reversed().forEach { match ->
            val rawValue = match.groupValues[1]
            val originalLabel = match.groupValues[2].trim().uppercase()

            if (originalLabel.contains("DSR")) return@forEach

            val entry = camposDesejados.entries.find { originalLabel.contains(it.key) } ?: return@forEach
            val resourceKey = entry.value

            if (!labelsProcessadas.contains(resourceKey)) {
                val formattedValue = formatTime(rawValue)
                val isNegative = originalLabel.contains("ATRASO") ||
                                originalLabel.contains("SAIDA") ||
                                originalLabel.contains("FALTA") ||
                                originalLabel.contains("FOLGA")

                itens.add(EspelhoItem(resourceKey, formattedValue, isNegative))
                labelsProcessadas.add(resourceKey)
            }
        }

        // 3. Saldo BH com Validação de Sanidade
        val saldoFinal = findBestTimeMatch(text, listOf(
            "TOTAL\\s+BANCO\\s+HORAS\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})",
            "SALDO\\s+FINAL\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})",
            "SALDO\\s+ATUAL\\s*[:|\\s]?\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})",
            "=\\s*([-|+]?\\s*\\d+\\s*:\\s*\\d{2})"
        ))

        val saldoPeriodoBH = findBestTimeMatch(text, listOf(
            "SALDO\\s+DO\\s+PER[IÍ]ODO\\s+([-|+]?\\s*\\d+\\s*:\\s*\\d{2})"
        ))

        // 4. Detecção de Faltas
        val diasFaltas = mutableListOf<String>()
        val diaRegex = "(\\d{2}/\\d{2}/\\d{4}).*?\\bFALTA\\b(?!\\s+DE\\s+MARC)".toRegex(RegexOption.IGNORE_CASE)
        diaRegex.findAll(text).forEach {
            if (!it.value.contains("DSR", true)) {
                diasFaltas.add(it.groupValues[1])
            }
        }

        return EspelhoPonto(
            funcionario = funcionario,
            matricula = matricula,
            cargo = cargo,
            empresa = empresa,
            periodo = periodo,
            jornada = jornada,
            jornadaRealizada = itens.find { it.label == "label_worked_hours" }?.value ?: "",
            resumoItens = itens.reversed(),
            saldoFinalBH = saldoFinal,
            saldoPeriodoBH = saldoPeriodoBH,
            detalhesSaldoBH = extractBhSummary(text),
            hasAbsences = diasFaltas.isNotEmpty(),
            diasFaltas = diasFaltas.distinct()
        )
    }

    private fun findBestTimeMatch(text: String, patterns: List<String>): String {
        for (p in patterns) {
            val match = p.toRegex(RegexOption.IGNORE_CASE).findAll(text).lastOrNull()
            if (match != null) return formatTime(match.groupValues[1])
        }
        return "0:00"
    }

    private fun extractBhSummary(text: String): String {
        // Tenta capturar a linha completa do resumo do banco de horas
        val patterns = listOf(
            "SALDO\\s+ANTERIOR.*?(?:=)\\s*([-|+]?\\s*\\d+:\\d{2})",
            "SALDO\\s+ANTERIOR.*"
        )
        for (p in patterns) {
            val match = p.toRegex(RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                return match.value.replace(Regex("\\s+"), " ").trim()
            }
        }
        return ""
    }

    private fun formatTime(time: String): String {
        val cleaned = time.replace("\\s".toRegex(), "")
        val isNegative = cleaned.contains("-")
        val absoluteTime = cleaned.replace("[-+]".toRegex(), "")
        val parts = absoluteTime.split(":")
        if (parts.size < 2) return "0:00"
        val hours = parts[0].trimStart('0').ifEmpty { "0" }
        val minutes = parts[1].take(2).padStart(2, '0')
        return "${if (isNegative) "-" else ""}$hours:$minutes"
    }
}
