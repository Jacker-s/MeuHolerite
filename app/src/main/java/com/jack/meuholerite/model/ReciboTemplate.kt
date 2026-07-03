package com.jack.meuholerite.model

data class ReciboTemplate(
    val id: String,
    val companyPattern: String? = null,
    val matriculaPattern: String = "(?:MATR[IÍ]CULA|REGISTRO|C[OÓ]DIGO)[:\\s]+(\\d+)",
    val periodoPattern: String = "(?:PER[IÍ]ODO|REFER[ÊE]NCIA|M[ÊE]S\\s*[/]?\\s*ANO|FOLHA_PAGAMENTO)[:\\s]+([A-ZÀ-Ú\\d/\\s]{3,}\\s*\\d{4}|\\d{2}/\\d{4})",
    val funcionarioPattern: String = "(?:NOME|NOME DO FUNCION[ÁA]RIO|EMPREGADO)[:\\s]*",
    val dataPagamentoPatterns: List<String> = listOf(
        "DATA\\s+DE\\s+PAGAMENTO\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})",
        "PAGAMENTO\\s+EM\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})",
        "PAGO\\s+EM\\s*[:|\\s]?\\s*(\\d{2}/\\d{2}/\\d{4})"
    ),
    val cargoPattern: String = "(?:CARGO|FUN[ÇC][ÃA]O|PROFISSAO|OCUPA[ÇC][ÃA]O|ATIVIDADE)[:\\s]+([A-ZÀ-Ú\\d\\s]{3,})",
    val itemPatterns: List<String> = listOf(
        "^([VDvd0-9]{2,})\\s+(.+)$",
        "^(\\d{3,})\\s+(.+)$"
    ),
    val totalProventosPattern: String = "(?:TOTAL\\s+PROVENTOS|PROVENTOS|VENCIMENTOS)[\\s\\S]{0,50}?R\\$\\s*([\\d,.]+)",
    val totalDescontosPattern: String = "(?:TOTAL\\s+DESCONTOS|DESCONTOS)[\\s\\S]{0,50}?R\\$\\s*([\\d,.]+)",
    val totalLiquidoPattern: String = "(?:TOTAL\\s+L[IÍ]QUIDO|L[IÍ]QUIDO\\s+A\\s+RECEBER|VALOR\\s+L[IÍ]QUIDO)[\\s\\S]{0,50}?R\\$\\s*([\\d,.]+)",
    val isDefault: Boolean = false
)
