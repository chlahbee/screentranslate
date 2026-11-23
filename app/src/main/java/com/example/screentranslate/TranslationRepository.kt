package com.example.screentranslate

import android.util.LruCache
import com.google.mlkit.nl.translate.Translator
import kotlinx.coroutines.tasks.await

/**
 * Keeps the translation policy out of the capture flow.  This makes it possible to replace the
 * online provider later without touching OCR, selection, or rendering code.
 */
class TranslationRepository(private val offlineTranslator: Translator) {
    private val cache = LruCache<String, TranslationResult>(150)

    suspend fun translate(text: String, mode: TranslationMode): TranslationResult {
        val normalized = TextEnhancer.enhance(text)
        val key = "${mode.name}:$normalized"
        cache.get(key)?.let { return it }

        val result = if (mode == TranslationMode.ONLINE) {
            GoogleTranslateService.translate(normalized)?.let {
                TranslationResult(it, usedOnlineService = true)
            } ?: TranslationResult(offlineTranslator.translate(normalized).await(), usedOnlineService = false)
        } else {
            TranslationResult(offlineTranslator.translate(normalized).await(), usedOnlineService = false)
        }
        cache.put(key, result)
        return result
    }
}
