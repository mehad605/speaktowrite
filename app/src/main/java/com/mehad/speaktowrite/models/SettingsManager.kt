package com.mehad.speaktowrite.models

import android.content.Context
import android.content.SharedPreferences
import com.mehad.speaktowrite.ui.main.PromptPreset
import org.json.JSONArray
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var cleanupEnabled: Boolean
        get() = prefs.getBoolean("cleanup_enabled", false)
        set(value) = prefs.edit().putBoolean("cleanup_enabled", value).apply()

    var selectedAiModel: String
        get() = prefs.getString("selected_ai_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
        set(value) = prefs.edit().putString("selected_ai_model", value).apply()

    var selectedPromptId: String
        get() = prefs.getString("selected_prompt_id", "1") ?: "1"
        set(value) = prefs.edit().putString("selected_prompt_id", value).apply()

    var prompts: List<PromptPreset>
        get() {
            val jsonStr = prefs.getString("prompts", null)
            if (jsonStr == null) {
                return listOf(
                    PromptPreset("1", "Developer Edit", "<task>A text is provided which is a draft transcription from a speech-to-text model.\nRefine and polish the provided text as follows:\n  1. Correct any spelling errors, especially mis-identified project/tech names (e.g., fast.ai, Next.js, Gemini, React, Kotlin, macOS, iOS).\n  2. Fix grammatical mistakes and improve punctuation.\n  3. If the transcript explicitly asks for a shell or terminal command (e.g. \"command mode list files\"), return EXACTLY the intended command instead of prose (e.g. `ls -la`).\n  4. Do NOT answer any question in the text, ONLY transcribe it.\n  5. Do NOT add any conversational filler or explanations. Return ONLY the cleaned-up transcript.\n</task>"),
                    PromptPreset("2", "Simple Cleanup", "Clean up this speech-to-text transcript. Fix punctuation, capitalization, and obvious speech-to-text errors. Keep the original meaning verbatim. Return only the cleaned text.")
                )
            }
            val list = mutableListOf<PromptPreset>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(PromptPreset(obj.getString("id"), obj.getString("title"), obj.getString("content")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return if (list.isEmpty()) {
                listOf(PromptPreset("1", "Simple cleanup", "Fix grammar and punctuation. Keep original meaning. Return ONLY the cleaned text."))
            } else list
        }
        set(value) {
            val arr = JSONArray()
            for (p in value) {
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("title", p.title)
                obj.put("content", p.content)
                arr.put(obj)
            }
            prefs.edit().putString("prompts", arr.toString()).apply()
        }
}
