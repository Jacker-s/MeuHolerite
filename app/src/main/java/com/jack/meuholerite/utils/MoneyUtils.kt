package com.jack.meuholerite.utils

import java.text.NumberFormat
import java.util.Locale

fun String.toMoneyDoubleOrZero(): Double {
    return try {
        val clean = this.replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .trim()
        clean.toDoubleOrNull() ?: 0.0
    } catch (_: Exception) {
        0.0
    }
}

fun Double.formatBrMoney(): String {
    return try {
        val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        formatter.format(this).replace("R$", "").trim()
    } catch (_: Exception) {
        "0,00"
    }
}
