package com.mhm.speaktowrite.models

object LocalVoiceCommandProcessor {
    fun process(input: String, customWords: List<CustomWord> = emptyList()): String {
        var text = input

        // Custom Words (User defined, highest priority)
        for (word in customWords) {
            if (word.spokenPhrase.isNotBlank() && word.replacementText.isNotBlank()) {
                val escapedPhrase = Regex.escape(word.spokenPhrase.trim())
                text = text.replace(Regex("(?i)\\b$escapedPhrase\\b"), word.replacementText)
            }
        }

        // Formatting
        val formatting = mapOf(
            "new line" to "\n",
            "next line" to "\n",
            "new paragraph" to "\n\n"
        )
        for ((word, symbol) in formatting) {
            text = text.replace(Regex("(?i)\\b$word\\b"), symbol)
        }

        // Symbols and Punctuation
        val symbols = mapOf(
            "comma" to ",",
            "period" to ".",
            "full stop" to ".",
            "question mark" to "?",
            "exclamation point" to "!",
            "exclamation mark" to "!",
            "colon" to ":",
            "semicolon" to ";",
            "hyphen" to "-",
            "dash" to "-",
            "underscore" to "_",
            "asterisk" to "*",
            "hash" to "#",
            "hashtag" to "#",
            "pound sign" to "£",
            "ampersand" to "&",
            "and sign" to "&",
            "dollar sign" to "$",
            "dollar" to "$",
            "yen sign" to "¥",
            "yen" to "¥",
            "euro sign" to "€",
            "euro" to "€",
            "percent sign" to "%",
            "percent" to "%",
            "at sign" to "@",
            "plus sign" to "+",
            "equals sign" to "=",
            "tilde" to "~",
            "forward slash" to "/",
            "backslash" to "\\",
            "vertical bar" to "|",
            "pipe" to "|",
            "open parenthesis" to "(",
            "close parenthesis" to ")",
            "left parenthesis" to "(",
            "right parenthesis" to ")",
            "open bracket" to "[",
            "close bracket" to "]",
            "open brace" to "{",
            "close brace" to "}",
            "apostrophe" to "'",
            "quotation mark" to "\"",
            "quote" to "\""
        )
        for ((word, symbol) in symbols) {
            val escapedSymbol = Regex.escapeReplacement(symbol)
            text = text.replace(Regex("(?i)\\b$word\\b"), escapedSymbol)
        }

        // Clean up spaces before punctuation
        text = text.replace(Regex(" +([.,?!:;])"), "$1")

        // Deduplicate redundant trailing punctuation (e.g., "?." -> "?", "!,." -> "!")
        // 1. Question or exclamation marks absorb following commas or periods
        text = text.replace(Regex("([?!])[.,]+"), "$1")
        
        // 2. Periods after commas, or commas after periods absorb into the first one
        text = text.replace(Regex(",\\.+"), ",")
        text = text.replace(Regex("\\.,+"), ".")
        
        // 3. Collapse multiple identical commas or periods
        text = text.replace(Regex(",{2,}"), ",")
        text = text.replace(Regex("\\.{2,}"), ".")

        return text
    }
}

