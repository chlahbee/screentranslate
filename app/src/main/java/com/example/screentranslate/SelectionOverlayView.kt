package com.example.screentranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class SelectionOverlayView(
    context: Context,
    private val onSelectionFinished: (RectF?) -> Unit,
    private val onSelectionCancelled: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val instructionBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE61E293B.toInt()
    }
    private val instructionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6B91C1C.toInt()
    }
    private val cancelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }
    private val confirmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE62563EB.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isSelecting = false
    private var isAdjusting = false
    private var cancelBounds = RectF()
    private var confirmBounds = RectF()
    private var activeHandle: Handle? = null

    private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val selection = currentSelection()
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)
        if (isSelecting && selection.width() > 0f && selection.height() > 0f) {
            canvas.drawRect(selection, selectionPaint)
        }
        canvas.restoreToCount(layer)
        if (isSelecting && selection.width() > 0f && selection.height() > 0f) {
            canvas.drawRect(selection, borderPaint)
        }

        val label = if (isAdjusting) "Adjust the corners, then tap Translate" else "Draw around the text, then release"
        val horizontalPadding = 16f * density
        val labelWidth = instructionPaint.measureText(label) + horizontalPadding * 2f
        val labelHeight = 44f * density
        val left = (width - labelWidth) / 2f
        val top = 48f * density
        canvas.drawRoundRect(
            left,
            top,
            left + labelWidth,
            top + labelHeight,
            12f * density,
            12f * density,
            instructionBackgroundPaint
        )
        canvas.drawText(label, width / 2f, top + labelHeight / 2f + instructionPaint.textSize / 3f, instructionPaint)

        val cancelWidth = 86f * density
        val cancelHeight = 40f * density
        cancelBounds = RectF(
            width - cancelWidth - 16f * density,
            16f * density,
            width - 16f * density,
            16f * density + cancelHeight
        )
        canvas.drawRoundRect(cancelBounds, 20f * density, 20f * density, cancelPaint)
        canvas.drawText("Cancel", cancelBounds.centerX(), cancelBounds.centerY() + cancelTextPaint.textSize / 3f, cancelTextPaint)

        if (isAdjusting) {
            val handleRadius = 11f * density
            val rect = currentSelection()
            listOf(
                rect.left to rect.top,
                rect.right to rect.top,
                rect.left to rect.bottom,
                rect.right to rect.bottom
            ).forEach { (x, y) ->
                canvas.drawCircle(x, y, handleRadius, handlePaint)
                canvas.drawCircle(x, y, handleRadius, handleBorderPaint)
            }

            val confirmWidth = 132f * density
            val confirmHeight = 48f * density
            confirmBounds = RectF(
                (width - confirmWidth) / 2f,
                height - confirmHeight - 28f * density,
                (width + confirmWidth) / 2f,
                height - 28f * density
            )
            canvas.drawRoundRect(confirmBounds, 24f * density, 24f * density, confirmPaint)
            canvas.drawText("Translate", confirmBounds.centerX(), confirmBounds.centerY() + cancelTextPaint.textSize / 3f, cancelTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (cancelBounds.contains(event.x, event.y)) {
                    isSelecting = false
                    onSelectionCancelled()
                    return true
                }
                if (isAdjusting) {
                    if (confirmBounds.contains(event.x, event.y)) {
                        onSelectionFinished(normalizedSelection())
                        return true
                    }
                    activeHandle = nearestHandle(event.x, event.y)
                    return true
                }
                startX = event.x.coerceIn(0f, width.toFloat())
                startY = event.y.coerceIn(0f, height.toFloat())
                currentX = startX
                currentY = startY
                isSelecting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x.coerceIn(0f, width.toFloat())
                val y = event.y.coerceIn(0f, height.toFloat())
                when (activeHandle) {
                    Handle.TOP_LEFT -> { startX = x; startY = y }
                    Handle.TOP_RIGHT -> { currentX = x; startY = y }
                    Handle.BOTTOM_LEFT -> { startX = x; currentY = y }
                    Handle.BOTTOM_RIGHT -> { currentX = x; currentY = y }
                    null -> { currentX = x; currentY = y }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isAdjusting) {
                    activeHandle = null
                    invalidate()
                    return true
                }
                currentX = event.x.coerceIn(0f, width.toFloat())
                currentY = event.y.coerceIn(0f, height.toFloat())
                val selection = currentSelection()
                if (normalizedSelection() == null) {
                    isSelecting = false
                    onSelectionFinished(null)
                    return true
                }
                // Store corners in a stable order so each handle can be moved independently.
                startX = selection.left
                startY = selection.top
                currentX = selection.right
                currentY = selection.bottom
                isAdjusting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isSelecting = false
                onSelectionFinished(null)
                return true
            }
        }
        return true
    }

    private fun currentSelection() = RectF(
        min(startX, currentX),
        min(startY, currentY),
        maxOf(startX, currentX),
        maxOf(startY, currentY)
    )

    private fun normalizedSelection(): RectF? {
        val selection = currentSelection()
        val minimumSize = 8f * density
        if (selection.width() < min(minimumSize, width.toFloat()) ||
            selection.height() < min(minimumSize, height.toFloat()) || width <= 0 || height <= 0
        ) return null
        return RectF(
            selection.left / width,
            selection.top / height,
            selection.right / width,
            selection.bottom / height
        )
    }

    private fun nearestHandle(x: Float, y: Float): Handle? {
        val rect = currentSelection()
        val hitRadius = 30f * density
        val handles = listOf(
            Handle.TOP_LEFT to (rect.left to rect.top),
            Handle.TOP_RIGHT to (rect.right to rect.top),
            Handle.BOTTOM_LEFT to (rect.left to rect.bottom),
            Handle.BOTTOM_RIGHT to (rect.right to rect.bottom)
        )
        return handles.minByOrNull { (_, point) ->
            val dx = point.first - x
            val dy = point.second - y
            dx * dx + dy * dy
        }?.takeIf { (_, point) ->
            val dx = point.first - x
            val dy = point.second - y
            dx * dx + dy * dy <= hitRadius * hitRadius
        }?.first
    }
}
