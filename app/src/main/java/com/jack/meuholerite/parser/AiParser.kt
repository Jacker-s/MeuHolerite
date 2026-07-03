package com.jack.meuholerite.parser

import android.util.Log
import com.google.gson.Gson
import com.jack.meuholerite.BuildConfig
import com.jack.meuholerite.model.ReciboPagamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GROQ_API_KEY
    private val gson = Gson()

    suspend fun parseRecibo(text: String): ReciboPagamento? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.e("AiParser", "GROQ_API_KEY não configurada")
            return@withContext null
        }

        val prompt = """
            Extraia os dados deste holerite (recibo de pagamento) e retorne APENAS um objeto JSON puro.
            Importante: Normalize valores monetários para o formato "0.000,00".
            Estrutura do JSON:
            {
              "funcionario": "Nome Completo",
              "matricula": "123",
              "periodo": "Mês/Ano",
              "empresa": "Nome da Empresa",
              "dataPagamento": "DD/MM/AAAA",
              "dataAdmissao": "DD/MM/AAAA",
              "totalProventos": "0,00",
              "totalDescontos": "0,00",
              "valorLiquido": "0,00",
              "cargo": "Nome do Cargo",
              "salarioBase": "0,00",
              "proventos": [{"codigo":"", "descricao":"", "referencia":"", "valor":""}],
              "descontos": [{"codigo":"", "descricao":"", "referencia":"", "valor":""}],
              "baseInss": "0,00",
              "fgtsMes": "0,00",
              "baseIrpf": "0,00"
            }
            Texto extraído do PDF:
            $text
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                val json = JSONObject(content)
                
                // Función auxiliar para extrair listas de itens
                fun parseItems(key: String): List<com.jack.meuholerite.model.ReciboItem> {
                    val list = mutableListOf<com.jack.meuholerite.model.ReciboItem>()
                    val arr = json.optJSONArray(key) ?: return list
                    for (i in 0 until arr.length()) {
                        val itemJson = arr.getJSONObject(i)
                        list.add(com.jack.meuholerite.model.ReciboItem(
                            codigo = itemJson.optString("codigo"),
                            descricao = itemJson.optString("descricao"),
                            referencia = itemJson.optString("referencia"),
                            valor = itemJson.optString("valor")
                        ))
                    }
                    return list
                }

                return@withContext ReciboPagamento(
                    funcionario = json.optString("funcionario", "Não identificado"),
                    matricula = json.optString("matricula", ""),
                    periodo = json.optString("periodo", "Não identificado"),
                    dataPagamento = json.optString("dataPagamento", ""),
                    dataAdmissao = json.optString("dataAdmissao", ""),
                    empresa = json.optString("empresa", "Empresa não identificada"),
                    proventos = parseItems("proventos"),
                    descontos = parseItems("descontos"),
                    totalProventos = json.optString("totalProventos", "0,00"),
                    totalDescontos = json.optString("totalDescontos", "0,00"),
                    valorLiquido = json.optString("valorLiquido", "0,00"),
                    cargo = json.optString("cargo", ""),
                    salarioBase = json.optString("salarioBase", "0,00"),
                    baseInss = json.optString("baseInss", "0,00"),
                    fgtsMes = json.optString("fgtsMes", "0,00"),
                    baseIrpf = json.optString("baseIrpf", "0,00"),
                    tipo = com.jack.meuholerite.model.ReciboTipo.MENSAL
                )
            } else {
                Log.e("AiParser", "Erro na API: ${response.code} - $responseBody")
            }
        } catch (e: Exception) {
            Log.e("AiParser", "Falha ao processar IA", e)
        }
        null
    }

    suspend fun getAiAnalysis(contextText: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Você é um assistente inteligente. Analise os dados ou responda à pergunta do usuário abaixo. 
            Seja útil, preciso e mantenha um tom profissional e amigável.
            Responda em Português do Brasil.
            
            Dados/Pergunta:
            $contextText
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                return@withContext jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            Log.e("AiParser", "Erro análise IA", e)
        }
        "Não foi possível gerar uma análise no momento."
    }

    suspend fun getPredictions(recibosContext: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            Com base nos seguintes dados de holerites (período, proventos, descontos, data de admissão), 
            gere uma previsão estimada de FÉRIAS (valor bruto e líquido aproximado para 30 dias) 
            e uma previsão de RESCISÃO (estimativa básica para demissão sem justa causa, incluindo aviso prévio e multa FGTS se possível).
            
            Seja claro, organizado em tópicos e use Português do Brasil. 
            Aviso: Deixe claro que são valores ESTIMADOS.
            
            Dados dos Holerites:
            $recibosContext
        """.trimIndent()

        val jsonRequest = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }

        val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                return@withContext jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            Log.e("AiParser", "Erro previsões IA", e)
        }
        "Não foi possível gerar as previsões no momento."
    }
}
