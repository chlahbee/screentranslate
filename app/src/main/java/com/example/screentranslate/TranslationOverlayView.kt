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
import android.view.ViewConfiguration
import kotlin.math.abs

data class TranslatedItem(
    val rect: Rect,
    val originalText: String,
    val translatedText: String,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
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

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4060A5FA") // Subtle accent border for translated boxes
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8060A5FA")
        style = Paint.Style.FILL
    }

    private val location = IntArray(2)

    private var currentScrollY = 0f
    private var maxScrollY = 0f
    private var initialTouchY = 0f
    private var initialScrollY = 0f
    private var isScrolling = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchY = event.rawY
                initialScrollY = currentScrollY
                isScrolling = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - initialTouchY
                if (!isScrolling && abs(deltaY) > touchSlop) {
                    isScrolling = true
                }
                if (isScrolling && maxScrollY > 0) {
                    currentScrollY = (initialScrollY - deltaY).coerceIn(0f, maxScrollY)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaY = event.rawY - initialTouchY
                if (!isScrolling && abs(deltaY) <= touchSlop) {
                    onDismiss()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        getLocationOnScreen(location)
        val offsetX = location[0].toFloat()
        val offsetY = location[1].toFloat()

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val density = context.resources.displayMetrics.density
        val padding = 8f * density

        canvas.save()
        canvas.translate(0f, -currentScrollY)

        var lastBottomY = 0f

        for (item in items) {
            val imgW = if (item.imageWidth > 0) item.imageWidth.toFloat() else viewWidth
            val imgH = if (item.imageHeight > 0) item.imageHeight.toFloat() else viewHeight

            val scaleX = viewWidth / imgW
            val scaleY = viewHeight / imgH

            val rawLeft = item.rect.left.toFloat() * scaleX - offsetX
            val rawTop = item.rect.top.toFloat() * scaleY - offsetY
            val rawRight = item.rect.right.toFloat() * scaleX - offsetX
            val rawBottom = item.rect.bottom.toFloat() * scaleY - offsetY

            var drawLeft = (rawLeft - padding).coerceAtLeast(0f)
            var drawTop = (rawTop - padding).coerceAtLeast(0f)
            var drawRight = (rawRight + padding).coerceAtMost(viewWidth)
            var drawBottom = rawBottom + padding

            // Ensure card has a comfortable minimum width for text wrapping
            val minCardWidth = 140f * density
            if ((drawRight - drawLeft) < minCardWidth) {
                drawRight = (drawLeft + minCardWidth).coerceAtMost(viewWidth)
            }

            // Prevent vertical overlap with preceding translated card
            if (drawTop < lastBottomY + (4f * density)) {
                drawTop = lastBottomY + (4f * density)
            }

            val availableWidth = (drawRight - drawLeft - (padding * 2)).toInt()
            if (availableWidth <= 0) continue

            // Adapt font size dynamically based on available card width
            val fontSizeDp = when {
                availableWidth >= 280 * density -> 15f
                availableWidth >= 180 * density -> 14f
                else -> 13f
            }
            textPaint.textSize = fontSizeDp * density

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
                .setLineSpacing(3f * density, 1.15f)
                .setIncludePad(true)

            val staticLayout = builder.build()

            // Expand background card vertically so all translated text lines fit completely
            val requiredHeight = staticLayout.height + (padding * 2)
            if ((drawBottom - drawTop) < requiredHeight) {
                drawBottom = drawTop + requiredHeight
            }

            // If card extends below screen bottom, attempt to shift drawTop upwards if space from previous card allows
            if (drawBottom > viewHeight && drawTop > (lastBottomY + 4f * density)) {
                val overflow = drawBottom - viewHeight
                val maxShiftUp = drawTop - (lastBottomY + 4f * density)
                val shiftUp = overflow.coerceAtMost(maxShiftUp).coerceAtLeast(0f)
                drawTop -= shiftUp
                drawBottom -= shiftUp
            }

            val drawRect = RectF(drawLeft, drawTop, drawRight, drawBottom)

            // Draw clean background box and border
            canvas.drawRoundRect(drawRect, 10f * density, 10f * density, bgPaint)
            canvas.drawRoundRect(drawRect, 10f * density, 10f * density, borderPaint)

            canvas.save()
            canvas.translate(
                drawRect.left + padding,
                drawRect.top + padding
            )
            staticLayout.draw(canvas)
            canvas.restore()

            lastBottomY = drawBottom
        }

        canvas.restore()

        // Calculate max vertical scroll range based on total rendered content height.
        // Add extra bottom padding when content reaches lower screen portion so text near bottom
        // can be easily scrolled up into the middle of the screen for clear reading.
        val extraBottomPadding = if (lastBottomY > viewHeight * 0.6f) {
            (viewHeight * 0.5f).coerceAtLeast(250f * density)
        } else {
            16f * density
        }
        val totalContentHeight = lastBottomY + extraBottomPadding
        maxScrollY = (totalContentHeight - viewHeight).coerceAtLeast(0f)
        currentScrollY = currentScrollY.coerceIn(0f, maxScrollY)

        // Draw visual scrollbar indicator when content exceeds view height
        if (maxScrollY > 0f) {
            val barWidth = 4f * density
            val barMargin = 4f * density
            val visibleRatio = (viewHeight / (lastBottomY + 16f * density)).coerceIn(0.1f, 1f)
            val barHeight = (viewHeight * visibleRatio).coerceAtLeast(36f * density)
            val barTop = if (maxScrollY > 0) (currentScrollY / maxScrollY) * (viewHeight - barHeight) else 0f
            val scrollbarRect = RectF(
                viewWidth - barMargin - barWidth,
                barTop,
                viewWidth - barMargin,
                barTop + barHeight
            )
            canvas.drawRoundRect(scrollbarRect, 2f * density, 2f * density, scrollbarPaint)
        }
    }
}


