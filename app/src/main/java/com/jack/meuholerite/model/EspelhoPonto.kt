package com.jack.meuholerite.model

data class EspelhoItem(
    val label: String,
    val value: String,
    val isNegative: Boolean = false
)

data class EspelhoPonto(
    val funcionario: String,
    val matricula: String = "",
    val cargo: String = "",
    val empresa: String = "",
    val periodo: String,
    val jornada: String = "",
    val jornadaRealizada: String = "",
    val resumoItens: List<EspelhoItem>,
    val saldoFinalBH: String,
    val saldoAnteriorBH: String = "0:00",
    val totalAjustesBH: String = "0:00",
    val saldoPeriodoBH: String = "0:00",
    val detalhesSaldoBH: String,
    val hasAbsences: Boolean = false,
    val diasFaltas: List<String> = emptyList(),
    val pdfFilePath: String? = null
)
