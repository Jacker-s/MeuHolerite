package com.jack.meuholerite.parser

import com.jack.meuholerite.model.InformeRendimento
import java.util.Locale

class InformeParser {

    fun parse(text: String): InformeRendimento {
        val lines = text.lines()
        val normalizedText = normalizeText(text)

        // 1. Extract Ano-Calendário and Exercício
        var anoCalendario = ""
        var exercicio = ""

        val anoCalendarioPattern = """Ano-calendário\s*(?:de|:)?\s*(\d{4})""".toRegex(RegexOption.IGNORE_CASE)
        val matchAno = anoCalendarioPattern.find(text)
        if (matchAno != null) {
            anoCalendario = matchAno.groupValues[1]
        }

        val exercicioPattern = """Exercício\s*(?:de|:)?\s*(\d{4})""".toRegex(RegexOption.IGNORE_CASE)
        val matchExercicio = exercicioPattern.find(text)
        if (matchExercicio != null) {
            exercicio = matchExercicio.groupValues[1]
        }

        // Fallbacks if not found directly
        if (anoCalendario.isEmpty()) {
            val yrPattern = """ANO[- ]CALENDÁRIO:\s*(\d{4})""".toRegex(RegexOption.IGNORE_CASE)
            anoCalendario = yrPattern.find(text)?.groupValues?.get(1) ?: ""
        }
        if (exercicio.isEmpty() && anoCalendario.isNotEmpty()) {
            // Exercise is usually calendar year + 1
            exercicio = (anoCalendario.toIntOrNull()?.plus(1))?.toString() ?: ""
        }

        // 2. Fonte Pagadora (CNPJ and Name)
        val cnpjRegex = """\b(?:\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}|\d{8}/\d{4}-\d{2}|\d{14})\b""".toRegex()
        val cnpj = findValueNearLabels(
            text = text,
            labels = listOf("CNPJ da fonte pagadora", "CNPJ", "Pessoa jurídica"),
            valueRegex = cnpjRegex
        ) ?: cnpjRegex.find(text)?.value.orEmpty()

        var nomeFontePagadora =
            findTextNearLabels(
                lines = lines,
                labels = listOf("Nome Empresarial", "Fonte Pagadora", "Pessoa Jurídica", "Razão Social"),
                rejectPatterns = listOf("CNPJ", "CPF", "ANO-CALEND", "EXERC")
            )
        
        // Clean company name if it contains other fields on same line
        if (nomeFontePagadora.contains("CNPJ", true)) {
            nomeFontePagadora = nomeFontePagadora.split(Regex("CNPJ", RegexOption.IGNORE_CASE))[0].trim()
        }
        if (nomeFontePagadora.isBlank()) {
            nomeFontePagadora = extractTextBeforeValueOnSameLine(
                text = text,
                value = cnpj,
                removeLabels = listOf("CNPJ", "Fonte Pagadora", "Pessoa Jurídica")
            )
        }
        if (nomeFontePagadora.isBlank()) {
            nomeFontePagadora = extractCompanyNameFromSourceSection(text)
        }

        // 3. Beneficiário (CPF and Name)
        val cpfRegex = """\b(?:\d{3}\.\d{3}\.\d{3}-\d{2}|\d{9}-\d{2}|\d{11})\b""".toRegex()
        val cpf = findValueNearLabels(
            text = text,
            labels = listOf("CPF do beneficiário", "CPF beneficiário", "CPF", "Pessoa Física", "Beneficiário"),
            valueRegex = cpfRegex
        ) ?: cpfRegex.find(text)?.value.orEmpty()

        var nomeBeneficiario =
            findTextNearLabels(
                lines = lines,
                labels = listOf("Nome Completo", "Beneficiário", "Beneficiária", "Pessoa Física"),
                rejectPatterns = listOf("CPF", "CNPJ", "ANO-CALEND", "EXERC")
            )
        
        if (nomeBeneficiario.contains("CPF", true)) {
            nomeBeneficiario = nomeBeneficiario.split(Regex("CPF", RegexOption.IGNORE_CASE))[0].trim()
        }
        if (nomeBeneficiario.isBlank()) {
            nomeBeneficiario = extractTextBeforeValueOnSameLine(
                text = text,
                value = cpf,
                removeLabels = listOf("CPF", "Beneficiário", "Pessoa Física", "Nome Completo")
            )
        }
        if (nomeBeneficiario.isBlank() || nomeBeneficiario.contains("Imposto sobre a Renda da Pessoa Física", true)) {
            nomeBeneficiario = extractBeneficiaryNameFromSection(text)
        }

        // 4. Extract Financial Values using standard official row prefix matching first (Tier 0)
        val rendimentosTributaveis = matchNumberedRow(text, "1", listOf(
            "TOTAL DOS RENDIMENTOS \\(INCLUSIVE F[ÉE]RIAS\\)",
            "TOTAL DOS RENDIMENTOS INCLUSIVE F[ÉE]RIAS",
            "RENDIMENTOS TRIBUT[ÁA]VEIS"
        )) ?: findValueByKeywords(text, listOf(
            "Total dos rendimentos (inclusive férias)",
            "Total dos rendimentos inclusive férias",
            "Rendimentos Tributáveis"
        )) ?: "0,00"

        val previdenciaOficial = matchNumberedRow(text, "2", listOf(
            "CONTRIBUI[ÇC][ÃA]O PREVIDENCI[ÁA]RIA OFICIAL",
            "PREVID[ÊE]NCIA OFICIAL",
            "CONTRIBUI[ÇC]AO PREVIDENCIARIA"
        )) ?: findValueByKeywords(text, listOf(
            "Contribuição previdenciária oficial",
            "Previdência oficial",
            "Previdencia oficial"
        )) ?: "0,00"

        val impostoRetido = matchNumberedRow(text, "5", listOf(
            "IMPOSTO RETIDO NA FONTE",
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE",
            "IMPOSTO RETIDO",
            "IRRF"
        )) ?: findValueByKeywords(text, listOf(
            "Imposto sobre a renda retido na fonte (IRRF)",
            "Imposto sobre a renda retido na fonte",
            "Imposto retido na fonte",
            "IRRF",
            "Retido na fonte"
        )) ?: findValueByKeywordsNormalized(normalizedText, listOf(
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE",
            "IMPOSTO RETIDO NA FONTE",
            "IRRF"
        )) ?: extractMainSectionValue(text, listOf(
            "IMPOSTO RETIDO NA FONTE",
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE",
            "IRRF"
        )) ?: "0,00"

        val decimoTerceiro = matchNumberedRow(text, "1", listOf(
            "D[ÉE]CIMO TERCEIRO SAL[ÁA]RIO",
            "13[º°O] SAL[ÁA]RIO",
            "GRATIFICA[ÇC][ÃA]O NATALINA",
            "RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA.*13"
        ), isSection5 = true) ?: findValueByKeywords(text, listOf(
            "Décimo terceiro salário",
            "Decimo terceiro salario",
            "13º salário",
            "13o salario",
            "Gratificação natalina",
            "Gratificacao natalina"
        )) ?: matchNumberedRow(text, "1", listOf(
            "DECIMO TERCEIRO SALARIO",
            "13[º°O]? SALARIO",
            "GRATIFICACAO NATALINA"
        )) ?: findValueByKeywordsNormalized(normalizedText, listOf(
            "DECIMO TERCEIRO SALARIO",
            "13O SALARIO",
            "GRATIFICACAO NATALINA"
        )) ?: extractSection5Value(text, listOf(
            "D[ÉE]CIMO TERCEIRO",
            "13[º°O]",
            "GRATIFICA[ÇC][ÃA]O NATALINA"
        )) ?: extractSectionRowValue(text, "1", listOf(
            "DECIMO TERCEIRO SALARIO",
            "13O SALARIO",
            "GRATIFICACAO NATALINA"
        )) ?: extractLineValueByHints(normalizedText, listOf(
            "DECIMO TERCEIRO SALARIO",
            "13O SALARIO",
            "GRATIFICACAO NATALINA"
        )) ?: "0,00"

        val impostoDecimoTerceiro = matchNumberedRow(text, "2", listOf(
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE SOBRE 13[º°O] SAL[ÁA]RIO",
            "IMPOSTO SOBRE O 13[º°O] SAL[ÁA]RIO",
            "IMPOSTO 13[º°O]",
            "IRRF SOBRE 13[º°O]",
            "IMPOSTO.*GRATIFICA[ÇC][ÃA]O NATALINA"
        ), isSection5 = true) ?: findValueByKeywords(text, listOf(
            "Imposto sobre a renda retido na fonte sobre 13º salário",
            "Imposto sobre a renda retido na fonte sobre o 13º",
            "Imposto sobre o 13º salário",
            "Imposto 13º",
            "IRRF sobre 13º salário",
            "Imposto gratificação natalina"
        )) ?: matchNumberedRow(text, "2", listOf(
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE SOBRE 13[º°O]? SALARIO",
            "IRRF SOBRE 13[º°O]? SALARIO",
            "IMPOSTO GRATIFICACAO NATALINA"
        )) ?: findValueByKeywordsNormalized(normalizedText, listOf(
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE SOBRE 13O SALARIO",
            "IRRF SOBRE 13O SALARIO",
            "IMPOSTO GRATIFICACAO NATALINA"
        )) ?: extractSection5Value(text, listOf(
            "IMPOSTO.*13[º°O]",
            "IRRF.*13[º°O]",
            "IMPOSTO.*GRATIFICA[ÇC][ÃA]O NATALINA"
        )) ?: extractSectionRowValue(text, "2", listOf(
            "IMPOSTO SOBRE A RENDA RETIDO NA FONTE SOBRE 13O SALARIO",
            "IRRF SOBRE 13O SALARIO",
            "IMPOSTO GRATIFICACAO NATALINA"
        )) ?: "0,00"
        

        val plr = matchNumberedRow(text, "3", listOf(
            "OUTROS \\(PLR\\)",
            "PARTICIPA[ÇC][ÃA]O NOS LUCROS OU RESULTADOS \\(PLR\\)",
            "PLR"
        ), isSection5 = true) ?: findValueByKeywords(text, listOf(
            "Outros (PLR)",
            "Participação nos lucros ou resultados (PLR)",
            "Participação nos lucros ou resultados",
            "PLR"
        )) ?: findValueByKeywordsNormalized(normalizedText, listOf(
            "PLR",
            "PARTICIPACAO NOS LUCROS OU RESULTADOS"
        )) ?: extractSectionRowValue(text, "3", listOf(
            "PLR",
            "PARTICIPACAO NOS LUCROS OU RESULTADOS"
        )) ?: "0,00"

        return InformeRendimento(
            anoCalendario = anoCalendario,
            exercicio = exercicio,
            cnpjFontePagadora = cnpj,
            nomeFontePagadora = nomeFontePagadora,
            cpfBeneficiario = cpf,
            nomeBeneficiario = nomeBeneficiario,
            rendimentosTributaveis = rendimentosTributaveis,
            previdenciaOficial = previdenciaOficial,
            impostoRetido = impostoRetido,
            decimoTerceiro = decimoTerceiro,
            impostoDecimoTerceiro = impostoDecimoTerceiro,
            plr = plr
        )
    }

    private fun extractMainSectionValue(text: String, labelPatterns: List<String>): String? {
        val sectionRegex = "(?is)(?:1\\s*[-–—.]\\s*RENDIMENTOS TRIBUT[ÁA]VEIS|RENDIMENTOS TRIBUT[ÁA]VEIS)(.*?)(?:5\\s*[-–—.]|RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA|$)".toRegex()
        val sectionText = sectionRegex.find(text)?.groupValues?.getOrNull(1) ?: text

        for (pattern in labelPatterns) {
            val sameLineRegex = "(?is)$pattern[^\\n\\r]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            sameLineRegex.find(sectionText)?.groupValues?.get(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }
        }
        return null
    }

    private fun matchNumberedRow(text: String, rowNum: String, labelPatterns: List<String>, isSection5: Boolean = false): String? {
        val separator = if (isSection5) "\\." else "[-–—]"
        for (pattern in labelPatterns) {
            // Tier 0.1: Same-line match
            val sameLineRegex = "(?i)\\b$rowNum\\s*$separator\\s*$pattern[^\\n\\r]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            sameLineRegex.find(text)?.groupValues?.get(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }

            // Tier 0.2: Multi-line match fallback
            val multiLineRegex = "(?i)\\b$rowNum\\s*$separator\\s*$pattern[\\s\\S]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            multiLineRegex.find(text)?.groupValues?.get(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }
        }
        return null
    }

    private fun extractSection5Value(text: String, labelPatterns: List<String>): String? {
        val sectionRegex = "(?is)(?:5\\s*[-–—.]\\s*RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA|RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA)(.*?)(?:6\\s*[-–—.]|RESPONS[ÁA]VEL|AUTENTICA[ÇC][ÃA]O|$)".toRegex()
        val sectionText = sectionRegex.find(text)?.groupValues?.getOrNull(1) ?: text

        for (pattern in labelPatterns) {
            val sameLineRegex = "(?is)$pattern[^\\n\\r]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            sameLineRegex.find(sectionText)?.groupValues?.get(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }

            val numberedRegex = "(?is)\\b\\d+\\s*[.)-]?\\s*$pattern[\\s\\S]{0,80}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            numberedRegex.find(sectionText)?.groupValues?.get(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }
        }

        return null
    }

    private fun extractSectionRowValue(text: String, rowNum: String, normalizedHints: List<String> = emptyList()): String? {
        val sectionRegex = "(?is)(?:5\\s*[-–—.]\\s*RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA|RENDIMENTOS SUJEITOS [ÀA] TRIBUTA[ÇC][ÃA]O EXCLUSIVA)(.*?)(?:6\\s*[-–—.]|RESPONS[ÁA]VEL|AUTENTICA[ÇC][ÃA]O|$)".toRegex()
        val sectionText = sectionRegex.find(text)?.groupValues?.getOrNull(1) ?: return null
        val rowRegex = "(?is)\\b$rowNum\\s*[.)-]?[^\\n\\r]{0,160}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
        rowRegex.find(sectionText)?.groupValues?.getOrNull(1)?.let {
            if (isValidCurrencyValue(it)) return it
        }

        if (normalizedHints.isNotEmpty()) {
            val normalizedSection = normalizeText(sectionText)
            normalizedSection.lineSequence().forEach { line ->
                if (line.contains(rowNum) && normalizedHints.any { hint -> line.contains(hint) }) {
                    val value = """([\d.]+,\d{2})""".toRegex().find(line)?.groupValues?.getOrNull(1)
                    if (value != null && isValidCurrencyValue(value)) return value
                }
            }
        }

        return null
    }

    private fun findValueByKeywords(text: String, keywords: List<String>): String? {
        for (key in keywords) {
            // Tier 1: Try to match on the same line (no newlines allowed, up to 120 characters)
            val sameLinePattern = "(?i)${Regex.escape(key)}[^\\n\\r]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            val sameLineMatch = sameLinePattern.find(text)
            if (sameLineMatch != null) {
                val value = sameLineMatch.groupValues[1]
                if (isValidCurrencyValue(value)) {
                    return value
                }
            }

            // Tier 2: Fall back to multi-line search (up to 120 characters)
            val multiLinePattern = "(?i)${Regex.escape(key)}[\\s\\S]{0,120}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()
            val multiLineMatch = multiLinePattern.find(text)
            if (multiLineMatch != null) {
                val value = multiLineMatch.groupValues[1]
                if (isValidCurrencyValue(value)) {
                    return value
                }
            }
        }
        return null
    }

    private fun findValueByKeywordsNormalized(normalizedText: String, keywords: List<String>): String? {
        for (key in keywords) {
            val sameLinePattern = "(?i)${Regex.escape(key)}[^\\n\\r]{0,120}?([\\d.]+,\\d{2})".toRegex()
            sameLinePattern.find(normalizedText)?.groupValues?.getOrNull(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }

            val multiLinePattern = "(?i)${Regex.escape(key)}[\\s\\S]{0,120}?([\\d.]+,\\d{2})".toRegex()
            multiLinePattern.find(normalizedText)?.groupValues?.getOrNull(1)?.let {
                if (isValidCurrencyValue(it)) return it
            }
        }
        return null
    }

    private fun extractLineValueByHints(normalizedText: String, hints: List<String>): String? {
        normalizedText.lineSequence().forEach { line ->
            if (hints.any { hint -> line.contains(hint) }) {
                val value = """([\d.]+,\d{2})""".toRegex().find(line)?.groupValues?.getOrNull(1)
                if (value != null && isValidCurrencyValue(value)) return value
            }
        }
        return null
    }

    private fun isValidCurrencyValue(value: String): Boolean {
        return !value.contains("/") && !value.contains("-") && value.count { it == '.' } <= 2
    }

    private fun findValueNearLabels(text: String, labels: List<String>, valueRegex: Regex): String? {
        val escaped = labels.joinToString("|") { Regex.escape(it) }
        val regex = "(?is)(?:$escaped)[^\\n\\r]{0,80}?(${valueRegex.pattern})".toRegex()
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun findTextNearLabels(lines: List<String>, labels: List<String>, rejectPatterns: List<String>): String {
        val index = lines.indexOfFirst { line -> labels.any { label -> line.contains(label, true) } }
        if (index == -1) return ""

        for (offset in 0..3) {
            val candidate = lines.getOrNull(index + offset)?.trim().orEmpty()
            if (candidate.isBlank()) continue
            if (labels.any { candidate.equals(it, true) }) continue
            if (rejectPatterns.any { candidate.contains(it, true) }) continue
            if (candidate.length < 3) continue
            return candidate.removePrefix(":").trim()
        }
        return ""
    }

    private fun extractTextBeforeValueOnSameLine(text: String, value: String, removeLabels: List<String>): String {
        if (value.isBlank()) return ""
        val line = text.lineSequence().firstOrNull { it.contains(value) }?.trim().orEmpty()
        if (line.isBlank()) return ""
        var result = line.substringBefore(value).trim()
        removeLabels.forEach { label ->
            result = result.replace(label, "", ignoreCase = true).trim()
            result = result.removePrefix(":").trim()
        }
        return result
    }

    private fun extractCompanyNameFromSourceSection(text: String): String {
        val sectionRegex = "(?is)1\\.\\s*FONTE PAGADORA.*?CNPJ/CPF\\s+Nome Empresarial/Nome Completo(.*?)(?:2\\.\\s*PESSOA FISICA BENEFICIARIA|$)".toRegex()
        val block = sectionRegex.find(normalizeText(text))?.groupValues?.getOrNull(1).orEmpty()
        val line = block.lineSequence().map { it.trim() }.firstOrNull {
            it.contains("/") && !it.contains("CNPJ CPF") && !it.contains("NOME EMPRESARIAL")
        }.orEmpty()
        return line.substringAfter(Regex("""(?:\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}|\d{8}/\d{4}-\d{2}|\d{14})""").find(line)?.value ?: "")
            .replace("-", " ")
            .trim()
    }

    private fun extractBeneficiaryNameFromSection(text: String): String {
        val normalized = normalizeText(text)
        val sectionRegex = "(?is)2\\.\\s*PESSOA FISICA BENEFICIARIA.*?CPF\\s+NOME COMPLETO(.*?)(?:TRABALHO ASSALARIADO|6\\.\\s*RENDIMENTOS RECEBIDOS ACUMULADAMENTE|$)".toRegex()
        val block = sectionRegex.find(normalized)?.groupValues?.getOrNull(1).orEmpty()
        val line = block.lineSequence().map { it.trim() }.firstOrNull {
            Regex("""(?:\d{3}\.\d{3}\.\d{3}-\d{2}|\d{9}-\d{2}|\d{11})\s+.+""").matches(it)
        }.orEmpty()
        return line.substringAfter(Regex("""(?:\d{3}\.\d{3}\.\d{3}-\d{2}|\d{9}-\d{2}|\d{11})""").find(line)?.value ?: "").trim()
    }

    private fun normalizeText(text: String): String {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase()
    }
}
