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

        // 1. Busca por Matrícula (Topo direito)
        matricula = "MATR[IÍ]CULA\\s+(\\d+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: ""

        // 2. Busca por Período (Ex: DEZ 2025)
        val periodoMatch = "FOLHA_PAGAMENTO\\s+([A-Z]{3,}\\s+\\d{4})".toRegex().find(text)
        if (periodoMatch != null) {
            periodo = periodoMatch.groupValues[1]
        }

        // 3. Busca por Nome (Lógica aprimorada para colunas)
        val nomeLineIndex = lines.indexOfFirst { it.contains("NOME", true) }
        if (nomeLineIndex != -1) {
            for (k in 1..2) {
                if (nomeLineIndex + k < lines.size) {
                    val line = lines[nomeLineIndex + k].trim()
                    if (line.isNotEmpty() && !line.contains("MATR", true) && !line.contains("DADOS", true)) {
                        funcionario = line.split(Regex("\\s{2,}|\\t|CPF", RegexOption.IGNORE_CASE))[0]
                            .replace("*", "").trim()
                        if (funcionario.length > 3) break
                    }
                }
            }
        }

        // 4. Busca por Data de Pagamento
        val dataPagamentoRegex = "(\\d{2}/\\d{2}/\\d{4})\\s+data.*?pagamento".toRegex(RegexOption.IGNORE_CASE)
        dataPagamento = dataPagamentoRegex.find(text)?.groupValues?.get(1) ?: ""
        if (dataPagamento.isEmpty()) {
            val dataHeaderIndex = lines.indexOfFirst { it.contains("DATA DE PAGAMENTO", true) }
            if (dataHeaderIndex != -1 && dataHeaderIndex + 1 < lines.size) {
                dataPagamento = "(\\d{2}/\\d{2}/\\d{4})".toRegex().find(lines[dataHeaderIndex + 1])?.value ?: ""
            }
        }

        // 5. Processamento de Itens (V... ou D...)
        lines.forEach { line ->
            val trimmed = line.trim()
            val matchItem = "^([VD]\\d+)\\s+(.+)$".toRegex().find(trimmed)
            if (matchItem != null) {
                val code = matchItem.groupValues[1]
                val content = matchItem.groupValues[2]
                val rIndex = content.lastIndexOf("R$")
                if (rIndex != -1) {
                    val valor = content.substring(rIndex + 2).trim()
                    val middle = content.substring(0, rIndex).trim()
                    val lastPart = middle.split("\\s+".toRegex()).last()
                    val hasReference = lastPart.matches("[\\d,.]+".toRegex())
                    val referencia = if (hasReference) lastPart else ""
                    val descricao = if (hasReference) middle.substring(0, middle.lastIndexOf(lastPart)).trim() else middle
                    
                    val detail = getDetailForItem(code, descricao)
                    val item = ReciboItem(code, descricao, referencia, valor, detail)
                    if (code.startsWith("V")) proventos.add(item)
                    else if (code.startsWith("D")) descontos.add(item)
                }
            }
        }

        // 6. Extração de Totais e Bases
        val valorLiquido = "TOTAL L[IÍ]QUIDO.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val baseInss = "Base INSS:\\s*R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val fgtsMes = "Valor FGTS:\\s*R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"
        val baseIrpf = "Base IRRF:\\s*R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00"

        return ReciboPagamento(
            funcionario = funcionario,
            matricula = matricula,
            periodo = periodo,
            dataPagamento = dataPagamento,
            empresa = "MARFRIG GLOBAL FOODS SA",
            proventos = proventos,
            descontos = descontos,
            totalProventos = "Total.*?Proventos.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00",
            totalDescontos = "Total.*?Desconto.*?R\\$\\s*([\\d,.]+)".toRegex(RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1) ?: "0,00",
            valorLiquido = valorLiquido,
            baseInss = baseInss,
            fgtsMes = fgtsMes,
            baseIrpf = baseIrpf
        )
    }

    private fun getDetailForItem(code: String, description: String): String {
        val desc = description.uppercase()
        return when {
            desc.contains("SALARIO") || desc.contains("SALÁRIO") -> "Salário base mensal conforme contrato de trabalho."
            desc.contains("ADICIONAL NOT") -> "Adicional pago por horas trabalhadas entre as 22h e 05h."
            desc.contains("DSR") -> "Descanso Semanal Remunerado calculado sobre seus ganhos variáveis."
            desc.contains("INSS") -> "Contribuição previdenciária obrigatória (aposentadoria e benefícios)."
            desc.contains("IRRF") -> "Imposto de Renda Retido na Fonte conforme tabela da Receita Federal."
            desc.contains("FGTS") -> "Depósito em sua conta vinculada (8% do salário bruto)."
            desc.contains("REFEICAO") || desc.contains("REFEIÇÃO") -> "Desconto referente ao benefício de alimentação/refeição."
            desc.contains("VALE") || desc.contains("ALIMENT") -> "Desconto referente ao benefício de cesta básica ou vale alimentação."
            desc.contains("SEGURO VIDA") -> "Desconto para apólice de seguro de vida em grupo."
            desc.contains("SIND") || desc.contains("ASSOCIATIVA") -> "Mensalidade ou contribuição para o sindicato da categoria."
            desc.contains("CONSIGNADO") || desc.contains("EMPRESTIMO") -> "Parcela de empréstimo descontada diretamente em folha."
            desc.contains("ATRASO") || desc.contains("SAIDA ANTEC") -> "Desconto por minutos ou horas não trabalhadas."
            desc.contains("DROG") || desc.contains("FARM") -> "Desconto referente a compras realizadas em farmácias conveniadas."
            else -> ""
        }
    }
}
