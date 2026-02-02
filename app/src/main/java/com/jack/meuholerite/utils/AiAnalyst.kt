package com.jack.meuholerite.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.ai.client.generativeai.GenerativeModel
import com.jack.meuholerite.model.ReciboItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiAnalyst(private val context: Context) {
    
    // API Key configurada para o Gemini
    private val apiKey = " SUA API KEY DO GEMINI AQUI" 
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun explainReciboItem(item: ReciboItem, isProvento: Boolean): String = withContext(Dispatchers.IO) {
        // Detecta o idioma selecionado no App via AppCompatDelegate
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val language = if (!appLocales.isEmpty) {
            appLocales[0]?.language ?: "pt"
        } else {
            // Caso não tenha sido alterado manualmente, usa o do sistema
            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
            locale.language
        }
        
        val tipo = if (isProvento) {
            when (language) {
                "es" -> "ingreso (ganancia)"
                "pt" -> "provento (ganho)"
                else -> "earnings"
            }
        } else {
            when (language) {
                "es" -> "descuento"
                "pt" -> "desconto"
                else -> "deduction"
            }
        }

        val prompt = when (language) {
            "pt" -> """
                Você é um especialista em RH e contabilidade brasileira. 
                Explique de forma simples e direta para um trabalhador o que é o seguinte item do seu holerite:
                
                Item: ${item.descricao}
                Tipo: $tipo
                Valor: R$ ${item.valor}
                Referência: ${item.referencia}
                
                Dê uma explicação amigável, em no máximo 3 frases, sobre por que esse valor aparece no holerite e se ele é comum.
                Responda obrigatoriamente em Português do Brasil.
            """.trimIndent()
            
            "es" -> """
                Eres un experto en RRHH y contabilidad brasileña.
                Explica de forma sencilla y directa a un trabajador qué es el siguiente elemento de su recibo de sueldo:
                
                Ítem: ${item.descricao}
                Tipo: $tipo
                Valor: R$ ${item.valor}
                Referencia: ${item.referencia}
                
                Da una explicación amable, en un máximo de 3 frases, sobre por qué este valor aparece en el recibo y si es común.
                Responda obligatoriamente en Español.
            """.trimIndent()

            else -> """
                You are a Brazilian HR and accounting specialist. 
                Explain simply and directly to a worker what the following item on their payslip is:
                
                Item: ${item.descricao}
                Type: $tipo
                Value: R$ ${item.valor}
                Reference: ${item.referencia}
                
                Give a friendly explanation, in a maximum of 3 sentences, about why this value appears on the payslip and if it is common.
                Respond in English.
            """.trimIndent()
        }

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "AI Error: Empty response."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }
}
