package com.jack.meuholerite.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Candidatura(
    val id: String = "",
    val nome: String = "",
    val cidade: String = "",
    val telefoneDigits: String = "",
    val cargo: String = "",
    val status: String = "RECEBIDO",
    val exportado: Boolean = false,
    val origem: String = "APP",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)

data class AdminUser(
    val uid: String = "",
    val ativo: Boolean = false
)
