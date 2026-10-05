package com.example.screentranslate

object TextEnhancer {

    private val slangMap = listOf(
        Regex("(?i)\\bain't never been a fan\\b") to "never liked them",
        Regex("(?i)\\bain't no\\b") to "is no",
        Regex("(?i)\\bain't\\b") to "is not",
        Regex("(?i)\\bsoon as\\b") to "As soon as",
        Regex("(?i)\\ba mf\\b") to "someone",
        Regex("(?i)\\bmf\\b") to "someone",
        Regex("(?i)\\bunfollow both ways\\b") to "unfollow each other",
        Regex("(?i)\\bplug ins\\b") to "air freshener",
        Regex("(?i)\\bwax warmer\\b") to "scented wax warmer",
        Regex("(?i)\\bnoticea\\b") to "notice a",
        Regex("(?i)\\bmake it unfollow\\b") to "forcing an unfollow",
        Regex("(?i)\\bi don't waste time\\b") to "I do not waste time"
    )

    fun isMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("@")) return true
        if (trimmed.matches(Regex("(?i)^(\\d+[smhd]|\\d{1,2}:\\d{2}|Follow|Discover more|Post your reply|Views?)$"))) return true
        if (trimmed.matches(Regex("(?i)^\\d{1,2}:\\d{2}\\s*·\\s*.*"))) return true
        return false
    }

    fun enhance(rawText: String): String {
        var text = rawText.trim()

        // 1. Fix common OCR character merge issues
        text = text.replace(Regex("(?i)\\bnoticea\\b"), "notice a")

        // 2. Normalize Slang and informal social media phrases
        for ((pattern, replacement) in slangMap) {
            text = pattern.replace(text, replacement)
        }

        // 3. Punctuation restoration & casing for AI translation model optimization
        if (text.isNotEmpty()) {
            text = text.substring(0, 1).uppercase() + text.substring(1)
            if (!text.endsWith(".") && !text.endsWith("?") && !text.endsWith("!")) {
                text += "."
            }
        }

        return text
    }
}
