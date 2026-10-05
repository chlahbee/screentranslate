package com.example.screentranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View

data class TranslatedItem(
    val rect: Rect,
    val originalText: String,
    val translatedText: String
)

class TranslationOverlayView(
    context: Context,
    private val items: List<TranslatedItem>,
    private val onDismiss: () -> Unit
) : View(context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0F172A") // Solid dark background to completely cover English text
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
    }

    private val location = IntArray(2)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onDismiss()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Get exact screen location offset (to account for status bar/camera cutout offsets)
        getLocationOnScreen(location)
        val offsetX = location[0].toFloat()
        val offsetY = location[1].toFloat()

        for (item in items) {
            val rect = item.rect
            val padding = 8f

            // Align 1:1 with original text bounds on screen
            val drawRect = RectF(
                (rect.left - offsetX - padding).coerceAtLeast(0f),
                (rect.top - offsetY - padding).coerceAtLeast(0f),
                (rect.right - offsetX + padding).coerceAtMost(width.toFloat()),
                (rect.bottom - offsetY + padding).coerceAtMost(height.toFloat())
            )

            // Draw clean background box over original text
            canvas.drawRoundRect(drawRect, 12f, 12f, bgPaint)

            val availableWidth = (drawRect.width() - (padding * 2)).toInt()
            if (availableWidth <= 0) continue

            // Build Arabic text layout with RTL alignment
            val builder = StaticLayout.Builder.obtain(
                item.translatedText,
                0,
                item.translatedText.length,
                textPaint,
                availableWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(true)

            val staticLayout = builder.build()

            canvas.save()
            canvas.translate(
                drawRect.left + padding,
                drawRect.top + padding
            )
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }
}
