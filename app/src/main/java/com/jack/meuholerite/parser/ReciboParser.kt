package com.jack.meuholerite.parser

import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento

class ReciboParser {
    fun parse(text: String): ReciboPagamento {
        val lines = text.lines()
        
        val proventos = mutableListOf<ReciboItem>()
        val descontos = mutableListOf<ReciboItem>()
        
        var funcionario = "Não identificado"
        var matricula = ""
        var periodo = "Não identificado"
        var dataPagamento = ""

        // 1. Matrícula e Período (Topo do documento)
        matricula = "MATR[IÍ]CULA\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: ""
        val periodoMatch = "FOLHA_PAGAMENTO\\s+([A-Z]{3,}\\s+\\d{4})".toRegex().find(text)
        if (periodoMatch != null) {
            periodo = periodoMatch.groupValues[1]
        }

        // 2. Nome do Funcionário (Abaixo de DADOS PESSOAIS)
        val nomeLineIndex = lines.indexOfFirst { it.contains("NOME", true) }
        if (nomeLineIndex != -1 && nomeLineIndex + 1 < lines.size) {
            val line = lines[nomeLineIndex + 1].trim()
            funcionario = line.split(Regex("\\s{3,}|\\t|CPF", RegexOption.IGNORE_CASE))[0]
                .replace("*", "").trim()
        }

        // 3. DATA DE PAGAMENTO (Filtro para ignorar data de admissão)
        val dateRegex = "(\\d{2}/\\d{2}/\\d{4})".toRegex()
        val cleanText = text.replace("\r", "").replace("\n", " ")
        
        // Padrões específicos que buscam a data APÓS o rótulo de pagamento
        val patternsDataPagamento = listOf(
            "DATA\\s+DE\\s+PAGAMENTO\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "PAGAMENTO\\s+EM\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "PAGO\\s+EM\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in patternsDataPagamento) {
            val match = pattern.find(cleanText)
            if (match != null) {
                dataPagamento = match.groupValues[1]
                break
            }
        }

        // Se falhar, procura a data na seção inferior do holerite (onde geralmente fica o pagamento)
        if (dataPagamento.isEmpty()) {
            val idx = lines.indexOfLast { it.contains("DATA DE PAGAMENTO", true) }
            if (idx != -1) {
                // Tenta linhas abaixo primeiro (comum em Marfrig/ePays)
                for (i in 0..3) {
                    if (idx + i < lines.size) {
                        val m = dateRegex.find(lines[idx + i])
                        if (m != null) {
                            dataPagamento = m.value
                            break
                        }
                    }
                }
            }
        }
        
        // Verificação final: Se a data encontrada for a mesma da Admissão (que geralmente aparece no topo),
        // continuamos procurando por outra data no final do texto.
        val dataAdmissao = "ADMISS[AÃ]O\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        
        if (dataPagamento == dataAdmissao || dataPagamento.isEmpty()) {
            // No ePays, a data de pagamento costuma ser uma das últimas datas no arquivo
            val allDates = dateRegex.findAll(text).map { it.value }.toList()
            if (allDates.isNotEmpty()) {
                val lastDate = allDates.last()
                // Geralmente a última data é a de pagamento ou geração do PDF
                if (lastDate != dataAdmissao) {
                    dataPagamento = lastDate
                }
            }
        }

        // 4. Processamento de Itens (V... e D...)
        lines.forEach { line ->
            val trimmed = line.trim()
            val matchItem = "^([VD]\\d{2,})\\s+(.+)$".toRegex().find(trimmed)
            if (matchItem != null) {
                val code = matchItem.groupValues[1]
                val content = matchItem.groupValues[2]
                
                val rIndex = content.lastIndexOf("R$")
                if (rIndex != -1) {
                    val valor = content.substring(rIndex + 2).trim()
                    val resto = content.substring(0, rIndex).trim()
                    
                    val parts = resto.split("\\s+".toRegex())
                    val referencia = if (parts.size > 1 && parts.last().matches("[\\d,.]+".toRegex())) parts.last() else ""
                    
                    val descricao = if (referencia.isNotEmpty()) {
                        resto.substring(0, resto.lastIndexOf(referencia)).trim()
                    } else resto

                    val item = ReciboItem(code, descricao, referencia, valor, getDetailForItem(code, descricao))
                    if (code.startsWith("V")) proventos.add(item) else descontos.add(item)
                }
            }
        }

        // 5. Totais e Líquido
        val valorLiquido = "TOTAL\\s+L[IÍ]QUIDO[\\s\\S]{1,50}?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val totalProv = "TOTAL\\s+PROVENTOS[\\s\\S]{1,50}?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val totalDesc = "TOTAL\\s+DESCONTOS[\\s\\S]{1,50}?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"

        return ReciboPagamento(
            funcionario = funcionario,
            matricula = matricula,
            periodo = periodo,
            dataPagamento = dataPagamento,
            empresa = "MARFRIG GLOBAL FOODS SA",
            proventos = proventos,
            descontos = descontos,
            totalProventos = totalProv,
            totalDescontos = totalDesc,
            valorLiquido = valorLiquido,
            baseInss = "Base INSS.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00",
            fgtsMes = "FGTS.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00",
            baseIrpf = "IRRF.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        )
    }

    private fun getDetailForItem(code: String, description: String): String {
        val desc = description.uppercase()
        return when {
            desc.contains("SALARIO") -> "Salário base mensal conforme contrato."
            desc.contains("DSR") -> "Descanso Semanal Remunerado sobre variáveis."
            desc.contains("ADICIONAL NOT") -> "Adicional de 30% por trabalho noturno."
            desc.contains("SEGURO VIDA") -> "Desconto de seguro de vida em grupo."
            desc.contains("ALIMENT") -> "Desconto de vale alimentação/refeição."
            desc.contains("INSS") -> "Contribuição previdenciária obrigatória."
            desc.contains("EMPRESTIMO") || desc.contains("CONSIGNADO") -> "Parcela de empréstimo descontada em folha."
            desc.contains("ATRASO") -> "Desconto por atrasos ou saídas antecipadas."
            desc.contains("ASSOCIATIVA") -> "Mensalidade do sindicato/associação."
            else -> ""
        }
    }
}
