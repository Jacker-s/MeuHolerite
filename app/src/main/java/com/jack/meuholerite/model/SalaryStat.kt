package com.jack.meuholerite.model

data class SalaryStat(
    val cargo: String,
    val estado: String,
    val salarioBruto: Double,
    val setor: String,
    val dataContribuicao: Long = System.currentTimeMillis()
)
