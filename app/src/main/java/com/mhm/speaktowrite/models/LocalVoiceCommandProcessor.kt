package com.mhm.speaktowrite.models

object LocalVoiceCommandProcessor {
    fun process(input: String, customWords: List<CustomWord> = emptyList()): String {
        var text = input

        // Custom Words
        for (word in customWords) {
            if (word.spokenPhrase.isNotBlank() && word.replacementText.isNotBlank()) {
                // Use regex with word boundaries to match exact phrase, case insensitive
                val escapedPhrase = Regex.escape(word.spokenPhrase.trim())
                text = text.replace(Regex("(?i)\\b$escapedPhrase\\b"), word.replacementText)
            }
        }

        // Punctuation
        text = text.replace(Regex("(?i)\\bcomma\\b"), ",")
        text = text.replace(Regex("(?i)\\bperiod\\b"), ".")
        text = text.replace(Regex("(?i)\\bfull stop\\b"), ".")
        text = text.replace(Regex("(?i)\\bquestion mark\\b"), "?")
        text = text.replace(Regex("(?i)\\bexclamation point\\b"), "!")
        text = text.replace(Regex("(?i)\\bexclamation mark\\b"), "!")
        
        // Formatting
        text = text.replace(Regex("(?i)\\bnew line\\b"), "\n")
        text = text.replace(Regex("(?i)\\bnext line\\b"), "\n")
        text = text.replace(Regex("(?i)\\bnew paragraph\\b"), "\n\n")

        // Clean up spaces before punctuation that might result from replacement or dictate
        text = text.replace(Regex(" \\,"), ",")
        text = text.replace(Regex(" \\."), ".")
        text = text.replace(Regex(" \\?"), "?")
        text = text.replace(Regex(" \\!"), "!")

        return text
    }
}
