package com.mehad.speaktowrite.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getAvailableModels(apiKey: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) return@withContext Result.failure(Exception("API Key is empty"))
                
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                val request = Request.Builder().url(url).get().build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val obj = JSONObject(body)
                val modelsArray = obj.getJSONArray("models")
                val resultList = mutableListOf<String>()
                
                for (i in 0 until modelsArray.length()) {
                    val model = modelsArray.getJSONObject(i)
                    val name = model.getString("name").removePrefix("models/")
                    
                    // Filter logic: must support generateContent, shouldn't be embeddings, veo, etc
                    var supportsText = false
                    if (model.has("supportedGenerationMethods")) {
                        val methods = model.getJSONArray("supportedGenerationMethods")
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                supportsText = true
                                break
                            }
                        }
                    }
                    
                    val exclusions = listOf("embedding", "vision", "veo", "aqa", "imagen", "image", "customtools", "nano-banana", "lyria", "video", "tts", "robotics", "computer", "antigravity", "deep-research")
                    var isExcluded = false
                    for (ex in exclusions) {
                        if (name.contains(ex, ignoreCase = true)) {
                            isExcluded = true
                            break
                        }
                    }
                    
                    if (supportsText && !isExcluded) {
                        resultList.add(name)
                    }
                }
                Result.success(resultList)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
