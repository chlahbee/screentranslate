package com.example.screentranslate

object TextEnhancer {

    fun isMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("@")) return true
        if (trimmed.matches(Regex("(?i)^(\\d+[smhd]|\\d{1,2}:\\d{2}|Follow|Discover more|Post your reply|Views?)$"))) return true
        if (trimmed.matches(Regex("(?i)^\\d{1,2}:\\d{2}\\s*·\\s*.*"))) return true
        return false
    }

    fun enhance(rawText: String): String {
        return rawText.trim().replace(Regex("\\s+"), " ")
    }
}
