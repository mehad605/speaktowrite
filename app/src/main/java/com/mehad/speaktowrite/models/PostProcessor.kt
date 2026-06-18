package com.mehad.speaktowrite.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PostProcessor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun process(text: String, prompt: String, apiKey: String, model: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Gemini API structure
                val bodyJson = JSONObject().apply {
                    val contentsArray = JSONArray()
                    
                    val userRole = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray()
                        parts.put(JSONObject().apply {
                            put("text", "Instruction: $prompt\n\nText to process: $text")
                        })
                        put("parts", parts)
                    }
                    
                    contentsArray.put(userRole)
                    put("contents", contentsArray)

                    // Provide generation config to reduce temperature and ensure clean output
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                    })
                }

                val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
                
                // Construct URL
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    System.err.println("Gemini API Error: HTTP ${response.code} - $responseBody")
                    return@withContext null
                }

                // Parse response
                val obj = JSONObject(responseBody)
                if (obj.has("candidates")) {
                    val candidates = obj.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        if (content.has("parts")) {
                            val parts = content.getJSONArray("parts")
                            if (parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).getString("text").trim()
                            }
                        }
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
