package com.jack.meuholerite.model

data class Promocao(
    val id: String = "",
    val titulo: String = "",
    val descricao: String = "",
    val imagemUrl: String = "",
    val precoAntes: Double = 0.0,
    val precoDepois: Double = 0.0,
    val link: String = "",
    val loja: String = "",
    val cupom: String = "",
    val verificado: Boolean = true,
    val expirada: Boolean = false,
    val expiraEm: Long = 0L,
    val curtidas: Long = 0,
    val timestamp: Long = 0L
)
