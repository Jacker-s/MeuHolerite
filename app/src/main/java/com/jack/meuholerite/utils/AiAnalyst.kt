package com.jack.meuholerite.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.jack.meuholerite.BuildConfig
import com.google.gson.Gson
import com.jack.meuholerite.model.ReciboItem
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiAnalyst(context: Context) {
    
    private val appContext = context.applicationContext
    private val apiKey = BuildConfig.GROQ_API_KEY
    private val gson = Gson()
    
    // Cliente Ktor simples para chamadas HTTP
    private val client = HttpClient(OkHttp)

    // Classes para o Gson
    private data class GroqRequest(
        val model: String,
        val messages: List<GroqMessage>,
        val temperature: Double = 0.7
    )
    private data class GroqMessage(val role: String, val content: String)
    private data class GroqResponse(val choices: List<Choice>)
    private data class Choice(val message: GroqMessage)

    suspend fun explainReciboItem(item: ReciboItem, isProvento: Boolean): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Chave da IA não configurada."
        }

        // Detecta o idioma
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val language = if (!appLocales.isEmpty) appLocales[0]?.language ?: "pt" else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val locales = appContext.resources.configuration.locales
                if (!locales.isEmpty) locales[0].language else "pt"
            } else {
                @Suppress("DEPRECATION")
                appContext.resources.configuration.locale.language
            }
        }
        
        val tipo = if (isProvento) {
            if (language == "pt") "provento (ganho)" else "earnings"
        } else {
            if (language == "pt") "desconto" else "deduction"
        }

        val prompt = """
            Você é um especialista em RH e contabilidade brasileira. Explique este item do holerite para um trabalhador de forma amigável em no máximo 3 frases:
            Item: ${item.descricao ?: "N/A"}
            Tipo: $tipo
            Valor: R$ ${item.valor ?: "0,00"}
            Referência: ${item.referencia ?: "N/A"}
            
            Dê uma explicação amigável sobre por que esse valor aparece e se ele é comum.
            Responda obrigatoriamente em: ${if (language == "pt") "Português do Brasil" else "Inglês"}.
        """.trimIndent()

        try {
            val requestBody = GroqRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(GroqMessage(role = "user", content = prompt))
            )

            val response: HttpResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(gson.toJson(requestBody))
            }

            if (response.status.isSuccess()) {
                val jsonResponse = response.bodyAsText()
                val groqResponse = gson.fromJson(jsonResponse, GroqResponse::class.java)
                groqResponse.choices.firstOrNull()?.message?.content ?: "Erro: Resposta vazia da IA."
            } else {
                "Erro na API (${response.status.value}): Tente novamente mais tarde."
            }
        } catch (e: Exception) {
            "Erro ao analisar: ${e.localizedMessage}"
        }
    }
}
