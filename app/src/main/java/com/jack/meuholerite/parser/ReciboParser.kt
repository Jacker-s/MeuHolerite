package com.jack.meuholerite.parser

import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.model.ReciboTemplate
import java.util.Locale

class ReciboParser(private val customTemplates: List<ReciboTemplate> = emptyList()) {
    
    private val defaultTemplate = ReciboTemplate(
        id = "default",
        companyPattern = null,
        isDefault = true
    )

    fun parse(text: String): ReciboPagamento {
        val templates = customTemplates + defaultTemplate
        
        // Try to find a specific template by company name if provided
        val matchedTemplate = templates.find { template ->
            template.companyPattern?.toRegex(RegexOption.IGNORE_CASE)?.containsMatchIn(text) == true
        } ?: defaultTemplate

        return parseWithTemplate(text, matchedTemplate)
    }

    private fun parseWithTemplate(text: String, template: ReciboTemplate): ReciboPagamento {
        val lines = text.lines()
        val proventos = mutableListOf<ReciboItem>()
        val descontos = mutableListOf<ReciboItem>()
        
        var funcionario = "Não identificado"
        var matricula = ""
        var periodo = "Não identificado"
        var dataPagamento = ""
        var dataAdmissao = ""
        var cargo = ""
        var tipo = com.jack.meuholerite.model.ReciboTipo.MENSAL

        // Detecção de Tipo (Lógica comum)
        val upperText = text.uppercase()
        tipo = when {
            upperText.contains("FÉRIAS") || upperText.contains("RECIBO DE FERIAS") || upperText.contains("GOZO DE FERIAS") -> com.jack.meuholerite.model.ReciboTipo.FERIAS
            upperText.contains("13º") || upperText.contains("DECIMO TERCEIRO") || upperText.contains("GRATIFICACAO NATALINA") -> com.jack.meuholerite.model.ReciboTipo.DECIMO_TERCEIRO
            upperText.contains("ADIANTAMENTO") || upperText.contains("VALE QUINZENAL") -> com.jack.meuholerite.model.ReciboTipo.ADIANTAMENTO
            upperText.contains("RESCISAO") || upperText.contains("TERMO DE RESCISAO") || upperText.contains("TRCT") -> com.jack.meuholerite.model.ReciboTipo.RESCISAO
            upperText.contains("PRO-LABORE") || upperText.contains("PRO LABORE") -> com.jack.meuholerite.model.ReciboTipo.PRO_LABORE
            upperText.contains("ESTAGIO") || upperText.contains("BOLSA AUXILIO") -> com.jack.meuholerite.model.ReciboTipo.ESTAGIO
            upperText.contains("RPA") || upperText.contains("AUTONOMO") -> com.jack.meuholerite.model.ReciboTipo.RPA
            else -> com.jack.meuholerite.model.ReciboTipo.MENSAL
        }

        var empresaExtracted = lines.firstOrNull { it.isNotBlank() && it.length > 5 }?.trim() ?: "Empresa não identificada"
        
        val razaoSocialIndex = lines.indexOfFirst { 
            it.contains("RAZÃO SOCIAL", true) || 
            it.contains("RAZAO SOCIAL", true) ||
            it.contains("TOMADOR", true)
        }
        if (razaoSocialIndex != -1 && razaoSocialIndex + 1 < lines.size) {
            val possibleEmpresa = lines[razaoSocialIndex + 1].trim()
            if (possibleEmpresa.isNotEmpty() && !possibleEmpresa.contains("CARGO", true) && !possibleEmpresa.contains("FUNÇÃO", true)) {
                empresaExtracted = possibleEmpresa.split(Regex("\\s{3,}|\\t|CNPJ", RegexOption.IGNORE_CASE))[0].trim()
            }
        }

        // Matrícula
        matricula = template.matriculaPattern.toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: ""
        
        // Período
        periodo = template.periodoPattern.toRegex().find(text)?.groupValues?.get(1) ?: "Não identificado"

        // Cargo
        cargo = template.cargoPattern.toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim() ?: ""

        // Melhoria Jackson: Identificação em layouts complexos
        if (cargo.isEmpty() || cargo == "Não identificado" || cargo.contains("CBO", true)) {
            val cboIndex = lines.indexOfFirst { 
                (it.contains("CBO", true) && it.contains("ADMISS", true)) || 
                it.contains("Cargo/Fun", true) || 
                it.contains("Função", true)
            }
            if (cboIndex != -1 && cboIndex + 1 < lines.size) {
                cargo = lines[cboIndex + 1].trim()
            }
        }
        
        cargo = cleanCargo(cargo)

        // Nome do Funcionário
        val nomeLineIndex = lines.indexOfFirst { it.contains(template.funcionarioPattern, true) }
        if (nomeLineIndex != -1 && nomeLineIndex + 1 < lines.size) {
            val line = lines[nomeLineIndex + 1].trim()
            funcionario = line.split(Regex("\\s{3,}|\\t|CPF", RegexOption.IGNORE_CASE))[0]
                .replace("*", "").trim()
        }

        // Data de Pagamento e Admissão
        val cleanText = text.replace("\r", "").replace("\n", " ")
        
        val patternAdmissao = "ADMISS[AÃ]O\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        val matchAdmissao = patternAdmissao.find(cleanText)
        if (matchAdmissao != null) {
            dataAdmissao = matchAdmissao.groupValues[1]
        }

        for (patternStr in template.dataPagamentoPatterns) {
            val match = patternStr.toRegex(RegexOption.IGNORE_CASE).find(cleanText)
            if (match != null) {
                dataPagamento = match.groupValues[1]
                break
            }
        }

        // Processamento de Itens
        val itemRegexes = template.itemPatterns.map { it.toRegex() }

        val descontosHeaderIndex = lines.indexOfFirst { it.contains("DESCONTOS", true) }

        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            
            var match: MatchResult? = null
            for (regex in itemRegexes) {
                match = regex.find(trimmed)
                if (match != null) break
            }

            if (match != null) {
                val code = match.groupValues[1]
                val content = match.groupValues[2]
                
                val moneyRegex = "(?:R\\$\\s*)?([\\d,.]+)".toRegex()
                val moneyMatches = moneyRegex.findAll(content).toList()
                if (moneyMatches.isNotEmpty()) {
                    val lastMatch = moneyMatches.last()
                    val valor = lastMatch.groupValues[1]
                    val beforeValue = content.substring(0, lastMatch.range.first).trim()
                    
                    val refMatch = "([\\d,.]+)\\s*$".toRegex().find(beforeValue)
                    val referencia = refMatch?.groupValues?.get(1) ?: ""
                    val descricao = if (referencia.isNotEmpty() && refMatch != null) beforeValue.substring(0, refMatch.range.first).trim() else beforeValue
                    
                    if (valor.contains(",") && valor.split(",").last().length == 2) {
                        val item = ReciboItem(code, descricao, referencia, valor, getDetailForItem(code, descricao))
                        val isDesconto = descontosHeaderIndex != -1 && lineIndex > descontosHeaderIndex
                        if (code.uppercase().startsWith("V") || (!code.uppercase().startsWith("D") && !isDesconto)) {
                            proventos.add(item)
                        } else {
                            descontos.add(item)
                        }
                    }
                }
            }
        }

        val totalProvStr = template.totalProventosPattern.toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val totalDescStr = template.totalDescontosPattern.toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        var valorLiquidoStr = template.totalLiquidoPattern.toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"

        val provVal = parseCurrency(totalProvStr)
        val descVal = parseCurrency(totalDescStr)
        val calcLiquido = provVal - descVal
        val parsedLiquido = parseCurrency(valorLiquidoStr)

        if (Math.abs(parsedLiquido - calcLiquido) > 0.01) {
            valorLiquidoStr = formatCurrency(calcLiquido)
        }

        val baseInssValue = findValueByKeywords(text, listOf("Base Cálc. Previdência", "Base Cálc. INSS", "Base INSS")) ?: "0,00"
        val fgtsMesValue = findValueByKeywords(text, listOf("Base Cálc. FGTS", "Base Cálculo FGTS", "Base FGTS", "B. Cálc. FGTS")) ?: "0,00"
        val valorFgtsValue = findValueByKeywords(text, listOf("FGTS do Mês", "FGTS Mês", "Depósito FGTS", "FGTS")) ?: "0,00"
        val baseIrpfValue = findValueByKeywords(text, listOf("Base Cálc. IRRF", "Base IRRF", "Base IRPF")) ?: "0,00"
        
        // Prioriza encontrar o item "SALARIO" nos proventos (que o usuário confirmou que já é extraído corretamente)
        var salarioBaseValue = proventos.find { 
            val d = it.descricao.uppercase()
            d == "SALARIO" || d == "SALÁRIO" || d == "SALARIO BASE" || d == "SALÁRIO BASE" || d == "VENCIMENTO"
        }?.valor
        
        if (salarioBaseValue == null) {
            salarioBaseValue = findValueByKeywords(text, listOf("Salário Base", "Vencimento", "Salário Mensal", "Salário")) ?: "0,00"
        }

        val inssItemValue = descontos.firstOrNull {
            it.descricao.contains("INSS", true) || it.descricao.contains("PREVID", true)
        }?.valor ?: findDiscountValueByKeywords(lines, listOf("INSS", "PREVIDENCIA SOCIAL", "DESCONTO INSS")) ?: "0,00"

        val irrfItemValue = descontos.firstOrNull {
            it.descricao.contains("IRRF", true) || it.descricao.contains("IMPOSTO DE RENDA", true) || it.descricao.contains("IRPF", true)
        }?.valor ?: findDiscountValueByKeywords(lines, listOf("IRRF", "IMPOSTO DE RENDA", "IRPF")) ?: "0,00"

        val descontosAjustados = descontos.toMutableList().apply {
            if (none { it.descricao.contains("INSS", true) || it.descricao.contains("PREVID", true) } && inssItemValue != "0,00") {
                add(ReciboItem(codigo = "DESC_INSS", descricao = "INSS", valor = inssItemValue, detalhe = "Contribuição previdenciária obrigatória."))
            }
            if (none { it.descricao.contains("IRRF", true) || it.descricao.contains("IMPOSTO DE RENDA", true) || it.descricao.contains("IRPF", true) } && irrfItemValue != "0,00") {
                add(ReciboItem(codigo = "DESC_IRRF", descricao = "IRRF", valor = irrfItemValue, detalhe = "Imposto de renda retido na fonte."))
            }
        }

        return ReciboPagamento(
            funcionario = funcionario,
            matricula = matricula,
            periodo = periodo,
            dataPagamento = dataPagamento,
            dataAdmissao = dataAdmissao,
            empresa = empresaExtracted,
            proventos = proventos,
            descontos = descontosAjustados,
            totalProventos = totalProvStr,
            totalDescontos = totalDescStr,
            valorLiquido = valorLiquidoStr,
            cargo = cargo,
            salarioBase = salarioBaseValue,
            baseInss = baseInssValue,
            fgtsMes = fgtsMesValue,
            valorFgts = valorFgtsValue,
            baseIrpf = baseIrpfValue,
            tipo = tipo
        )
    }

    private fun findValueByKeywords(text: String, keywords: List<String>): String? {
        for (key in keywords) {
            // Tenta encontrar o valor com ou sem o prefixo R$
            val pattern = "$key[\\s\\S]{0,50}?(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE)
            val match = pattern.find(text)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun findDiscountValueByKeywords(lines: List<String>, keywords: List<String>): String? {
        val excludedHints = listOf("BASE", "CALC", "FGTS")
        val moneyRegex = "(?:R\\$\\s*)?([\\d.]+,\\d{2})".toRegex()

        for (line in lines) {
            val normalized = line.uppercase()
            if (excludedHints.any { normalized.contains(it) }) continue
            if (keywords.none { normalized.contains(it) }) continue

            val candidate = moneyRegex.findAll(line).map { it.groupValues[1] }.lastOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun parseCurrency(value: String): Double {
        return value.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale("pt", "BR"), "%,.2f", value)
    }

    private fun cleanCargo(text: String): String {
        // Remove tudo a partir de R$ caso tenha sido capturado junto
        val raw = if (text.contains("R$")) text.split("R$")[0] else text
        
        return raw.replace("CBO", "", true)
            .replace("ADMISSÃO", "", true)
            .replace("ADMISSAO", "", true)
            .replace("SALÁRIO", "", true)
            .replace("SALARIO", "", true)
            .replace("FUNÇÃO", "", true)
            .replace("FUNCAO", "", true)
            .replace("CARGO", "", true)
            .replace("/", " ")
            .replace(Regex("\\d+"), "") // Remove códigos numéricos
            .replace(Regex("\\s+"), " ") // Normaliza espaços
            .trim()
    }

    private fun getDetailForItem(code: String, description: String): String {
        val desc = description.uppercase()
        return when {
            desc.contains("SALARIO") -> "Salário base mensal conforme contrato."
            desc.contains("INSS") -> "Contribuição previdenciária obrigatória."
            desc.contains("ALIMENT") -> "Desconto de vale alimentação/refeição."
            else -> ""
        }
    }
}
