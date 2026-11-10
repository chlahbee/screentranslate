package com.example.screentranslate

object Constants {
    const val TAG = "ST"
    const val LOG_TAG = "ST"

    // Supported language pair
    const val SOURCE_LANG = "en"
    const val TARGET_LANG = "ar"

    // Notification constants
    const val CHANNEL_ID = "screen_translate_channel"
    const val NOTIFICATION_CHANNEL_ID = "screen_translate_channel"
    const val NOTIFICATION_ID = 1001

    // Service actions & intent extras
    const val ACTION_START = OverlayService.ACTION_START
    const val ACTION_STOP = OverlayService.ACTION_STOP
    const val ACTION_TOGGLE_BUTTON = OverlayService.ACTION_TOGGLE_BUTTON
    const val ACTION_TRANSLATE_NOW = OverlayService.ACTION_TRANSLATE_NOW
    const val EXTRA_RESULT_CODE = OverlayService.EXTRA_RESULT_CODE
    const val EXTRA_RESULT_DATA = OverlayService.EXTRA_DATA
    const val EXTRA_DATA = OverlayService.EXTRA_DATA
}

enum class TranslationMode {
    ONLINE,
    OFFLINE;

    companion object {
        const val PREFERENCES_NAME = "screen_translate_settings"
        const val PREFERENCE_MODE = "translation_mode"
    }
}
