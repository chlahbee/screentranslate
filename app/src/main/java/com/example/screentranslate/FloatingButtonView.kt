package com.example.screentranslate

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class FloatingButtonView(context: Context) : View(context) {

    var onTranslateClick: (() -> Unit)? = null

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val layoutParams: WindowManager.LayoutParams

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 26, 35, 126)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val iconDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_translate)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        isClickable = true
        isFocusable = false

        val buttonSize = (48 * context.resources.displayMetrics.density).toInt()
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }
    }

    fun hideTemporarily() {
        visibility = INVISIBLE
    }

    fun showAgain() {
        visibility = VISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - dpToPx(3f)

        // Drop shadow circle
        canvas.drawCircle(cx, cy + dpToPx(2f), radius, shadowPaint)

        // Main circular background
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // Subtle glowing border
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Centered translation icon
        iconDrawable?.let { icon ->
            val iconSize = (radius * 1.15f).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            icon.setBounds(left, top, left + iconSize, top + iconSize)
            icon.draw(canvas)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                }

                if (isDragging) {
                    layoutParams.x = (initialX + dx).toInt()
                    layoutParams.y = (initialY + dy).toInt()
                    safeUpdateLayout()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                    Log.d(Constants.TAG, "Floating button tapped")
                    onTranslateClick?.invoke()
                } else {
                    snapToEdge()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    snapToEdge()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge() {
        val screenWidth = getScreenWidth()
        val currentX = layoutParams.x
        val buttonWidth = width.takeIf { it > 0 } ?: dpToPx(48f).toInt()
        val margin = dpToPx(12f).toInt()

        val targetX = if (currentX + buttonWidth / 2 < screenWidth / 2) {
            margin // Snap to left edge
        } else {
            screenWidth - buttonWidth - margin // Snap to right edge
        }

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                layoutParams.x = animation.animatedValue as Int
                safeUpdateLayout()
            }
        }
        animator.start()
    }

    private fun safeUpdateLayout() {
        try {
            if (isAttachedToWindow) {
                windowManager.updateViewLayout(this, layoutParams)
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Error updating floating button layout: ${e.message}")
        }
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            metrics.bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
