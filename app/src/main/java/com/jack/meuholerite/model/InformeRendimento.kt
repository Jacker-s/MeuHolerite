package com.jack.meuholerite.model

data class InformeRendimento(
    val anoCalendario: String = "",
    val exercicio: String = "",
    val cnpjFontePagadora: String = "",
    val nomeFontePagadora: String = "",
    val cpfBeneficiario: String = "",
    val nomeBeneficiario: String = "",
    val rendimentosTributaveis: String = "0,00",
    val previdenciaOficial: String = "0,00",
    val impostoRetido: String = "0,00",
    val decimoTerceiro: String = "0,00",
    val impostoDecimoTerceiro: String = "0,00",
    val plr: String = "0,00",
    val pdfFilePath: String? = null
)
