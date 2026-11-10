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
    private val onSelectionFinished: (RectF?) -> Unit
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

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isSelecting = false

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

        val label = "Drag to select an area, then release"
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x.coerceIn(0f, width.toFloat())
                startY = event.y.coerceIn(0f, height.toFloat())
                currentX = startX
                currentY = startY
                isSelecting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x.coerceIn(0f, width.toFloat())
                currentY = event.y.coerceIn(0f, height.toFloat())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentX = event.x.coerceIn(0f, width.toFloat())
                currentY = event.y.coerceIn(0f, height.toFloat())
                val selection = currentSelection()
                val minimumSize = 8f * density
                val normalizedSelection = if (
                    selection.width() >= min(minimumSize, width.toFloat()) &&
                    selection.height() >= min(minimumSize, height.toFloat()) &&
                    width > 0 && height > 0
                ) {
                    RectF(
                        selection.left / width,
                        selection.top / height,
                        selection.right / width,
                        selection.bottom / height
                    )
                } else {
                    null
                }
                isSelecting = false
                onSelectionFinished(normalizedSelection)
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
}
