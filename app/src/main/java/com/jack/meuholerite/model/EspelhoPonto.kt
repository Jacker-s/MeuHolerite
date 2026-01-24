package com.jack.meuholerite.model

data class EspelhoItem(
    val label: String,
    val value: String,
    val isNegative: Boolean = false
)

data class EspelhoPonto(
    val funcionario: String,
    val periodo: String,
    val resumoItens: List<EspelhoItem>,
    val saldoFinalBH: String,
    val saldoPeriodoBH: String = "0:00",
    val detalhesSaldoBH: String,
    val hasAbsences: Boolean = false,
    val diasFaltas: List<String> = emptyList(),
    val pdfFilePath: String? = null
)
