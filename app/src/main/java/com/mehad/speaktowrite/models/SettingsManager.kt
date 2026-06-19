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
            // Built-in presets. New default presets are added here; the
            // migration block below injects any that an existing user is
            // missing (matched by id) so upgrades pick them up too.
            val defaults = listOf(
                PromptPreset("1", "Developer Edit", "<task>A text is provided which is a draft transcription from a speech-to-text model.\nRefine and polish the provided text as follows:\n  1. Correct any spelling errors, especially mis-identified project/tech names (e.g., fast.ai, Next.js, Gemini, React, Kotlin, macOS, iOS).\n  2. Fix grammatical mistakes and improve punctuation.\n  3. If the transcript explicitly asks for a shell or terminal command (e.g. \"command mode list files\"), return EXACTLY the intended command instead of prose (e.g. `ls -la`).\n  4. Do NOT answer any question in the text, ONLY transcribe it.\n  5. Do NOT add any conversational filler or explanations. Return ONLY the cleaned-up transcript.\n</task>"),
                PromptPreset("2", "Simple Cleanup", "Clean up this speech-to-text transcript. Fix punctuation, capitalization, and obvious speech-to-text errors. Keep the original meaning verbatim. Return only the cleaned text."),
                PromptPreset("3", "Bangla", "নিচে একটি টেক্সট দেওয়া হলো যা একটি স্পিচ-টু-টেক্সট মডেল থেকে নেওয়া একটি খসড়া ট্রান্সক্রিপশন।\nঅনুগ্রহ করে নিচের নিয়ম অনুসরণ করে টেক্সটটি পরিমার্জন ও পরিষ্কার করুন:\n  ১. বানানের ভুল সংশোধন করুন, বিশেষ করে ভুলভাবে চেনা প্রজেক্ট/প্রযুক্তির নাম (যেমন— Android, Kotlin, GitHub, React, ঢাকা, কক্সবাজার)।\n  ২. ব্যাকরণগত ভুল ঠিক করুন এবং যতিচিহ্ন (দাঁড়ি, কমা, প্রশ্নবোধক চিহ্ন ইত্যাদি) যথাযথভাবে বসান।\n  ৩. টেক্সটে যদি কোনো প্রশ্ন থাকে তবে তার উত্তর দেবেন না, শুধু ট্রান্সক্রিপশন করবেন।\n  ৪. কোনো রকম অতিরিক্ত কথা বা ব্যাখ্যা যোগ করবেন না। শুধুমাত্র পরিষ্কার করা টেক্সটটি ফেরত দিন।")
            )

            val jsonStr = prefs.getString("prompts", null)
            if (jsonStr == null) return defaults

            val saved = mutableListOf<PromptPreset>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    saved.add(PromptPreset(obj.getString("id"), obj.getString("title"), obj.getString("content")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Migration: inject any built-in preset the user is missing.
            val savedIds = saved.map { it.id }.toMutableSet()
            var changed = false
            for (d in defaults) {
                if (d.id !in savedIds) {
                    saved.add(d)
                    savedIds.add(d.id)
                    changed = true
                }
            }
            if (changed) {
                // Persist the merged list so we only migrate once per preset.
                prefs.edit().putString("prompts", encodePrompts(saved)).apply()
            }

            return if (saved.isEmpty()) defaults else saved
        }
        set(value) {
            prefs.edit().putString("prompts", encodePrompts(value)).apply()
        }

    private fun encodePrompts(list: List<PromptPreset>): String {
        val arr = JSONArray()
        for (p in list) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("content", p.content)
            arr.put(obj)
        }
        return arr.toString()
    }
}
