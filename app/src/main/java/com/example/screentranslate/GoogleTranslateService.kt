package com.example.screentranslate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleTranslateService {
    suspend fun translate(text: String, sourceLang: String = "en", targetLang: String = "ar"): String? = withContext(Dispatchers.IO) {
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&q=$encodedText"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                // Response is a JSON array containing the translated text string at index 0
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    return@withContext jsonArray.getString(0)
                }
            } else {
                Log.e(Constants.LOG_TAG, "ST: Google Translate API Error \${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "ST: Google Translate Request Failed", e)
        }
        return@withContext null
    }
}
