package com.mehad.speaktowrite.models

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object TranscriberManager {
    var transcriber: LocalTranscriber? = null
    val isLoading = MutableStateFlow(false)
    val currentModel = MutableStateFlow<String?>(null)
    
    fun init(context: Context) {
        val saved = context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE).getString("selected_model", null)
        if (saved != null) {
            loadModel(context, saved)
        }
    }

    fun loadModel(context: Context, modelName: String) {
        if (currentModel.value == modelName && transcriber != null) return
        
        // Save to prefs
        context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE).edit().putString("selected_model", modelName).apply()
        currentModel.value = modelName
        isLoading.value = true
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val old = transcriber
                transcriber = LocalTranscriber.create(context, modelName)
                old?.release() // Ensure old model is freed
            } catch (e: Exception) {
                e.printStackTrace()
                transcriber = null
            } finally {
                isLoading.value = false
            }
        }
    }
}
