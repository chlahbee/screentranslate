package com.example.screentranslate

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var floatingButton: FloatingButtonView? = null
    private var windowManager: WindowManager? = null
    private var currentOverlayView: TranslationOverlayView? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val translator: Translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ARABIC)
            .build()
        Translation.getClient(options)
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode == Activity.RESULT_CANCELED || data == null) {
                    Log.e(Constants.LOG_TAG, "ST: capture failed - no permission data")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification()
                setupMediaProjection(resultCode, data)
                showFloatingButton()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = Constants.NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ScreenTranslate", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenTranslate is active")
            .setContentText("Tap floating button to translate")
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
        Log.d(Constants.LOG_TAG, "ST: service started foreground")
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.currentWindowMetrics?.bounds
            if (bounds != null) {
                screenWidth = bounds.width()
                screenHeight = bounds.height()
            } else {
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } else {
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(Constants.LOG_TAG, "ST: MediaProjection stopped")
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenTranslate",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return
        floatingButton = FloatingButtonView(this).apply {
            onTranslateClick = { captureAndTranslate() }
        }
        windowManager?.addView(floatingButton, floatingButton?.layoutParams)
    }

    private fun removeCurrentOverlay() {
        currentOverlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG, "ST: error removing overlay view", e)
            }
        }
        currentOverlayView = null
    }

    private fun captureAndTranslate() {
        removeCurrentOverlay()
        floatingButton?.hideTemporarily()

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image = imageReader?.acquireLatestImage()
                if (image == null) {
                    Toast.makeText(this, "Try again, capturing frame...", Toast.LENGTH_SHORT).show()
                    floatingButton?.showAgain()
                    return@postDelayed
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()

                val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks
                        if (blocks.isEmpty()) {
                            Toast.makeText(this, "No text detected on screen", Toast.LENGTH_SHORT).show()
                            floatingButton?.showAgain()
                            croppedBitmap.recycle()
                            return@addOnSuccessListener
                        }

                        val itemsToTranslate = blocks.mapNotNull { block ->
                            val rect = block.boundingBox
                            // Combine lines into single continuous paragraph for accurate translation
                            val cleanedText = block.text.replace("\n", " ").trim()
                            if (rect != null && cleanedText.isNotBlank() && !TextEnhancer.isMetadata(cleanedText)) {
                                val enhancedText = TextEnhancer.enhance(cleanedText)
                                Pair(rect, enhancedText)
                            } else null
                        }

                        if (itemsToTranslate.isEmpty()) {
                            Toast.makeText(this, "No text detected on screen", Toast.LENGTH_SHORT).show()
                            floatingButton?.showAgain()
                            croppedBitmap.recycle()
                            return@addOnSuccessListener
                        }

                        // Use coroutines to sequence translations and prioritize Cloud Google Translate
                        CoroutineScope(Dispatchers.Main).launch {
                            val translatedItems = mutableListOf<TranslatedItem>()
                            
                            for ((rect, originalText) in itemsToTranslate) {
                                try {
                                    // 1. Try Google Translate API (Cloud)
                                    var arabicText = GoogleTranslateService.translate(originalText)
                                    
                                    // 2. Fallback to ML Kit (Offline)
                                    if (arabicText == null) {
                                        arabicText = translator.translate(originalText).await()
                                    }
                                    
                                    translatedItems.add(TranslatedItem(rect, originalText, arabicText ?: originalText))
                                } catch (e: Exception) {
                                    Log.e(Constants.LOG_TAG, "ST: Translation failed", e)
                                    translatedItems.add(TranslatedItem(rect, originalText, originalText))
                                }
                            }
                            
                            showTranslationOverlay(translatedItems)
                            floatingButton?.showAgain()
                            croppedBitmap.recycle()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(Constants.LOG_TAG, "ST: OCR failed", e)
                        Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        floatingButton?.showAgain()
                        croppedBitmap.recycle()
                    }

            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG, "ST: capture exception", e)
                Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                floatingButton?.showAgain()
            }
        }, 150)
    }

    private fun showTranslationOverlay(items: List<TranslatedItem>) {
        val overlayView = TranslationOverlayView(this, items) {
            removeCurrentOverlay()
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        currentOverlayView = overlayView
        windowManager?.addView(overlayView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeCurrentOverlay()
        try {
            floatingButton?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        textRecognizer.close()
        translator.close()
    }
}
