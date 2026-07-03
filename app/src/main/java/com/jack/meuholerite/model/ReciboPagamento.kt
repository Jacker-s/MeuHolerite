package com.jack.meuholerite.model

enum class ReciboTipo(val descricao: String) {
    MENSAL("Mensal"),
    ADIANTAMENTO("Adiantamento"),
    FERIAS("Férias"),
    DECIMO_TERCEIRO("13º Salário"),
    RESCISAO("Rescisão"),
    PRO_LABORE("Pró-Labore"),
    ESTAGIO("Bolsa Estágio"),
    RPA("RPA (Autônomo)")
}

data class ReciboPagamento(
    val funcionario: String = "",
    val matricula: String = "",
    val periodo: String = "",
    val dataPagamento: String = "",
    val dataAdmissao: String = "",
    val empresa: String = "",
    val proventos: List<ReciboItem> = emptyList(),
    val descontos: List<ReciboItem> = emptyList(),
    val totalProventos: String = "0,00",
    val totalDescontos: String = "0,00",
    val valorLiquido: String = "0,00",
    val cargo: String = "",
    val salarioBase: String = "0,00",
    val baseInss: String = "0,00",
    val fgtsMes: String = "0,00",
    val valorFgts: String = "0,00",
    val baseIrpf: String = "0,00",
    val tipo: ReciboTipo = ReciboTipo.MENSAL,
    val pdfFilePath: String? = null
)

data class ReciboItem(
    val codigo: String = "",
    val descricao: String = "",
    val referencia: String = "",
    val valor: String = "",
    val detalhe: String = ""
)
