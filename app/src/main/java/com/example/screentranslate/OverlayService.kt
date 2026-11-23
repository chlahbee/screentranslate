package com.example.screentranslate

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_BUTTON = "ACTION_TOGGLE_BUTTON"
        const val ACTION_TRANSLATE_NOW = "ACTION_TRANSLATE_NOW"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"

        var isRunning = false
            private set
        var isButtonHidden = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var floatingButton: FloatingButtonView? = null
    private var windowManager: WindowManager? = null
    private var currentOverlayView: TranslationOverlayView? = null
    private var selectionOverlayView: SelectionOverlayView? = null
    private var isFloatingButtonHidden = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeTranslationJob: Job? = null
    private var translationSession = 0L

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

    private val translationRepository by lazy { TranslationRepository(translator) }

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
            ACTION_TOGGLE_BUTTON -> {
                isFloatingButtonHidden = !isFloatingButtonHidden
                isButtonHidden = isFloatingButtonHidden
                if (isFloatingButtonHidden) {
                    floatingButton?.visibility = View.GONE
                    Toast.makeText(this, "Floating button hidden", Toast.LENGTH_SHORT).show()
                } else {
                    if (floatingButton == null) {
                        showFloatingButton()
                    }
                    floatingButton?.visibility = View.VISIBLE
                    Toast.makeText(this, "Floating button active", Toast.LENGTH_SHORT).show()
                }
                updateNotification()
                requestQSTileUpdate()
                return START_STICKY
            }
            ACTION_TRANSLATE_NOW -> {
                closeNotificationShade()
                Handler(Looper.getMainLooper()).postDelayed({
                    captureAndTranslate()
                }, 300)
                return START_STICKY
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
                isRunning = true
                isButtonHidden = false
                isFloatingButtonHidden = false
                startForegroundWithNotification()
                setupMediaProjection(resultCode, data)
                showFloatingButton()
                requestQSTileUpdate()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val channelId = Constants.NOTIFICATION_CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ScreenTranslate", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val translateIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TRANSLATE_NOW }
        val translatePending = PendingIntent.getService(
            this, 2, translateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_BUTTON }
        val togglePending = PendingIntent.getService(
            this, 3, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleLabel = if (isFloatingButtonHidden) "Show Button" else "Hide Button"
        val contentText = if (isFloatingButtonHidden) {
            "Floating button hidden. Tap 'Translate' or 'Show Button'."
        } else {
            "Tap floating button or notification actions to translate"
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenTranslate is active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainPending)
            .setOngoing(true)
            .addAction(0, "Translate", translatePending)
            .addAction(0, toggleLabel, togglePending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        Log.d(Constants.LOG_TAG, "ST: service started foreground")
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(Constants.NOTIFICATION_ID, buildNotification())
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun closeNotificationShade() {
        try {
            @Suppress("DEPRECATION")
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeIntent)
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "ST: error closing notification shade", e)
        }
    }

    private fun restoreFloatingButtonIfNeeded() {
        if (!isFloatingButtonHidden) {
            floatingButton?.showAgain()
        }
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        // Safely cleanup previous displays and image readers before re-creating
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "ST: error releasing existing projection", e)
        }

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)

        // Always query maximumWindowMetrics on Android 11+ to get full physical display resolution
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager?.maximumWindowMetrics?.bounds
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
            onLongPress = { startAreaSelection() }
        }
        windowManager?.addView(floatingButton, floatingButton?.layoutParams)
    }

    private fun startAreaSelection() {
        if (selectionOverlayView != null) return
        invalidateActiveTranslation()
        removeCurrentOverlay()
        floatingButton?.hideTemporarily()
        selectionOverlayView = SelectionOverlayView(
            context = this,
            onSelectionFinished = { selection ->
                removeSelectionOverlay()
                if (selection == null) {
                    Toast.makeText(this, "Select a larger area to translate", Toast.LENGTH_SHORT).show()
                    restoreFloatingButtonIfNeeded()
                } else {
                    captureAndTranslate(selection)
                }
            },
            onSelectionCancelled = {
                removeSelectionOverlay()
                restoreFloatingButtonIfNeeded()
            }
        )
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
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
        windowManager?.addView(selectionOverlayView, layoutParams)
    }

    private fun removeSelectionOverlay() {
        selectionOverlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG, "ST: error removing selection overlay", e)
            }
        }
        selectionOverlayView = null
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

    private fun invalidateActiveTranslation() {
        activeTranslationJob?.cancel()
        activeTranslationJob = null
        translationSession++
    }

    private data class ProcessedBlock(
        val rect: Rect,
        val text: String
    )

    private fun groupAndMergeBlocks(blocks: List<com.google.mlkit.vision.text.Text.TextBlock>): List<ProcessedBlock> {
        if (blocks.isEmpty()) return emptyList()

        val valid = blocks.mapNotNull { b ->
            val rect = b.boundingBox ?: return@mapNotNull null
            val cleaned = b.text.replace("\n", " ").trim()
            if (cleaned.isBlank() || TextEnhancer.isMetadata(cleaned)) return@mapNotNull null
            ProcessedBlock(rect, cleaned)
        }.sortedWith(compareBy<ProcessedBlock> { it.rect.top }.thenBy { it.rect.left })

        if (valid.isEmpty()) return emptyList()

        val merged = mutableListOf<ProcessedBlock>()
        var current = valid[0]

        for (i in 1 until valid.size) {
            val next = valid[i]
            val verticalGap = next.rect.top - current.rect.bottom
            val horizontalOverlap = maxOf(0, minOf(current.rect.right, next.rect.right) - maxOf(current.rect.left, next.rect.left))
            val minWidth = minOf(current.rect.width(), next.rect.width())

            val maxContinuationGap = maxOf(32, current.rect.height() / 2)
            val isVerticallyClose = verticalGap in -25..maxContinuationGap
            val isHorizontallyAligned = minWidth > 0 && (horizontalOverlap.toFloat() / minWidth.toFloat()) > 0.35f
            val currentSentenceIsIncomplete = !current.text.matches(Regex(""".*[.!?…]["'’”)\]]*$"""))

            if (isVerticallyClose && isHorizontallyAligned && currentSentenceIsIncomplete) {
                val combinedRect = Rect(
                    minOf(current.rect.left, next.rect.left),
                    current.rect.top,
                    maxOf(current.rect.right, next.rect.right),
                    maxOf(current.rect.bottom, next.rect.bottom)
                )
                val combinedText = current.text + " " + next.text
                current = ProcessedBlock(combinedRect, combinedText)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private fun captureAndTranslate(selection: RectF? = null) {
        invalidateActiveTranslation()
        val session = translationSession
        removeCurrentOverlay()
        floatingButton?.hideTemporarily()

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (session != translationSession) return@postDelayed
                val image = imageReader?.acquireLatestImage()
                if (image == null) {
                    Toast.makeText(this, "Try again, capturing frame...", Toast.LENGTH_SHORT).show()
                    restoreFloatingButtonIfNeeded()
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

                val screenBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()

                val captureRect = selection?.let {
                    Rect(
                        (it.left * screenWidth).toInt().coerceIn(0, screenWidth),
                        (it.top * screenHeight).toInt().coerceIn(0, screenHeight),
                        (it.right * screenWidth).toInt().coerceIn(0, screenWidth),
                        (it.bottom * screenHeight).toInt().coerceIn(0, screenHeight)
                    )
                } ?: Rect(0, 0, screenWidth, screenHeight)

                if (captureRect.width() <= 0 || captureRect.height() <= 0) {
                    screenBitmap.recycle()
                    Toast.makeText(this, "Selected area is too small", Toast.LENGTH_SHORT).show()
                    restoreFloatingButtonIfNeeded()
                    return@postDelayed
                }

                val capturedBitmap = if (selection == null) {
                    screenBitmap
                } else {
                    Bitmap.createBitmap(
                        screenBitmap,
                        captureRect.left,
                        captureRect.top,
                        captureRect.width(),
                        captureRect.height()
                    ).also { screenBitmap.recycle() }
                }

                val inputImage = InputImage.fromBitmap(capturedBitmap, 0)
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        if (session != translationSession) {
                            capturedBitmap.recycle()
                            return@addOnSuccessListener
                        }
                        val blocks = visionText.textBlocks
                        if (blocks.isEmpty()) {
                            Toast.makeText(this, "No text detected on screen", Toast.LENGTH_SHORT).show()
                            restoreFloatingButtonIfNeeded()
                            capturedBitmap.recycle()
                            return@addOnSuccessListener
                        }

                        val mergedBlocks = groupAndMergeBlocks(blocks)
                        if (mergedBlocks.isEmpty()) {
                            Toast.makeText(this, "No text detected on screen", Toast.LENGTH_SHORT).show()
                            restoreFloatingButtonIfNeeded()
                            capturedBitmap.recycle()
                            return@addOnSuccessListener
                        }

                        val itemsToTranslate = mergedBlocks.map { block ->
                            val enhancedText = TextEnhancer.enhance(block.text)
                            val screenRect = Rect(block.rect).apply {
                                offset(captureRect.left, captureRect.top)
                            }
                            Pair(screenRect, enhancedText)
                        }

                        // Translate independent paragraphs concurrently. A session check prevents a
                        // late result from being shown after the user starts another capture.
                        activeTranslationJob = serviceScope.launch {
                            val translationMode = if (
                                getSharedPreferences(TranslationMode.PREFERENCES_NAME, Context.MODE_PRIVATE)
                                    .getString(TranslationMode.PREFERENCE_MODE, TranslationMode.ONLINE.name) ==
                                TranslationMode.OFFLINE.name
                            ) {
                                TranslationMode.OFFLINE
                            } else {
                                TranslationMode.ONLINE
                            }
                            val translatedItems = itemsToTranslate.map { (rect, originalText) ->
                                async(Dispatchers.IO) {
                                    try {
                                        val result = translationRepository.translate(originalText, translationMode)
                                        TranslatedItem(
                                            rect = rect,
                                            originalText = originalText,
                                            translatedText = result.text,
                                            imageWidth = screenWidth,
                                            imageHeight = screenHeight
                                        )
                                    } catch (e: Exception) {
                                        Log.e(Constants.LOG_TAG, "ST: Translation failed", e)
                                        TranslatedItem(
                                            rect = rect,
                                            originalText = originalText,
                                            translatedText = originalText,
                                            imageWidth = screenWidth,
                                            imageHeight = screenHeight,
                                            translationFailed = true
                                        )
                                    }
                                }
                            }.awaitAll()

                            if (session != translationSession) {
                                capturedBitmap.recycle()
                                return@launch
                            }

                            val hadTranslationFailure = translatedItems.any { it.translationFailed }
                            showTranslationOverlay(translatedItems)
                            if (hadTranslationFailure) {
                                val message = if (translationMode == TranslationMode.OFFLINE) {
                                    "Offline translation unavailable. Download the English-Arabic model."
                                } else {
                                    "Some text could not be translated. Check your connection or offline model."
                                }
                                Toast.makeText(this@OverlayService, message, Toast.LENGTH_LONG).show()
                            }
                            restoreFloatingButtonIfNeeded()
                            capturedBitmap.recycle()
                        }
                    }
                    .addOnFailureListener { e ->
                        if (session != translationSession) return@addOnFailureListener
                        Log.e(Constants.LOG_TAG, "ST: OCR failed", e)
                        Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        restoreFloatingButtonIfNeeded()
                        capturedBitmap.recycle()
                    }

            } catch (e: Exception) {
                Log.e(Constants.LOG_TAG, "ST: capture exception", e)
                Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                restoreFloatingButtonIfNeeded()
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

    private fun requestQSTileUpdate() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, ScreenTranslateTileService::class.java)
            )
        } catch (e: Exception) {
            Log.e(Constants.LOG_TAG, "ST: error updating QS tile", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isButtonHidden = false
        requestQSTileUpdate()
        removeCurrentOverlay()
        removeSelectionOverlay()
        invalidateActiveTranslation()
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
