package com.jack.meuholerite.model

data class ReciboPagamento(
    val funcionario: String,
    val matricula: String = "",
    val periodo: String,
    val dataPagamento: String = "",
    val empresa: String,
    val proventos: List<ReciboItem>,
    val descontos: List<ReciboItem>,
    val totalProventos: String,
    val totalDescontos: String,
    val valorLiquido: String,
    val baseInss: String,
    val fgtsMes: String,
    val baseIrpf: String,
    val pdfFilePath: String? = null
)

data class ReciboItem(
    val codigo: String,
    val descricao: String,
    val referencia: String,
    val valor: String,
    val detalhe: String = ""
)
