package com.example.animatedkeyboard.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.animatedkeyboard.utils.AnimationEngine
import kotlin.math.roundToInt
import kotlin.math.sqrt

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyListener {
        fun onKey(code: Int, label: String)
    }

    private var keyListener: OnKeyListener? = null

    fun setOnCustomKeyListener(listener: OnKeyListener) {
        this.keyListener = listener
    }

    private val keyPaint = Paint().apply {
        color = Color.parseColor("#080808")
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val keyBorderPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#885500")
        textSize = 42f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val animationEngine = AnimationEngine()
    private var lastFrameTime = 0L
    private var fireGlowAlpha = 0.5f
    private var fireGlowDirection = -1
    private val fireGlowPaint = Paint().apply { isAntiAlias = true }
    private val pressedKeys = mutableMapOf<String, Long>()
    private val keyStates = mutableMapOf<String, KeyState>()
    enum class KeyState { NORMAL, WHITE, CYAN, PINK, FADE }
    private val ripples = mutableListOf<RippleEffect>()
    private var currentPopup: PopupEffect? = null

    private val popupPaint = Paint().apply {
        color = Color.parseColor("#1E1E1E")
        isAntiAlias = true
    }
    private val popupBorderPaint = Paint().apply {
        color = Color.parseColor("#FFAA00")
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val popupTextPaint = Paint().apply {
        color = Color.parseColor("#FFCC00")
        textSize = 52f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val letterLayout = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Back"),
        listOf("123", ",", "Space", ".", "Enter")    )

    private val numberLayout = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "'"),
        listOf("#+=?", "ABC", ".", ",", "Back"),
        listOf("Space", "Enter")
    )

    private var currentLayout = letterLayout
    private var isShifted = false
    private val keyMap = mutableMapOf<String, Rect>()
    private val keyCodes = mutableMapOf<String, Int>()
    private var lastKeyTime = 0L
    private val debounceInterval = 100L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val swipeThreshold = 50f
    private var isSwiping = false
    private var lastTouchedKey: String? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x00000000)
        keyCodes["Shift"] = -1
        keyCodes["Back"] = -5
        keyCodes["Enter"] = -4
        keyCodes["Space"] = 32
        keyCodes["123"] = -2
        keyCodes["ABC"] = -3
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val desiredHeight = (screenHeight * 0.24).toInt()
        super.onMeasure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(desiredHeight, View.MeasureSpec.EXACTLY)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        createKeyMap(w, h)
    }

    private fun createKeyMap(width: Int, height: Int) {
        keyMap.clear()
        val padding = (width * 0.01).toInt()
        val keyHeight = (height / 4).toInt()
        val rowHeight = keyHeight + (padding / 2)
        var currentY = padding
        for ((rowIndex, row) in currentLayout.withIndex()) {
            val rowWidth = width - (padding * 2)
            var totalWeight = 0.0
            for (item in row) { totalWeight += getWeight(item).toDouble() }
            val totalKeyWeight = totalWeight.toFloat()
            var currentX = padding
            for (keyLabel in row) {
                val keyWidth = (rowWidth * (getWeight(keyLabel) / totalKeyWeight)).roundToInt()
                val safeKeyWidth = minOf(keyWidth, width - currentX - padding)
                keyMap[keyLabel] = Rect(currentX, currentY, currentX + safeKeyWidth, currentY + keyHeight)
                keyStates[keyLabel] = KeyState.NORMAL
                currentX += safeKeyWidth + (padding / 2)
            }
            currentY += rowHeight
        }
    }

    private fun getWeight(label: String): Float {
        return when (label) {
            "Space" -> 3.5f
            "Shift", "Back", "123", "ABC" -> 1.3f
            "Enter" -> 1.5f
            else -> 1.0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentTime = System.currentTimeMillis()
        val elapsedTime = if (lastFrameTime == 0L) 16 else currentTime - lastFrameTime
        lastFrameTime = currentTime
        canvas.drawColor(0x00000000)
        updateAndDrawFireGlow(canvas, elapsedTime)
        animationEngine.update(elapsedTime)
        animationEngine.draw(canvas)
        updateAndDrawRipples(canvas, elapsedTime)
        updateKeyStates(elapsedTime)
        for ((label, rect) in keyMap) { drawKey(canvas, label, rect) }
        currentPopup?.draw(canvas)
        if (animationEngine.hasActiveAnimations() || ripples.isNotEmpty() || currentPopup != null) {
            postInvalidateOnAnimation()
        }
    }

    private fun updateAndDrawFireGlow(canvas: Canvas, deltaTime: Long) {
        fireGlowAlpha += fireGlowDirection * 0.005f
        if (fireGlowAlpha <= 0.3f || fireGlowAlpha >= 0.7f) { fireGlowDirection *= -1 }
        val centerX = width / 2f
        val centerY = height.toFloat()
        val a1 = (255 * fireGlowAlpha).toInt()
        val a2 = (180 * fireGlowAlpha).toInt()
        val a3 = (100 * fireGlowAlpha).toInt()
        val a4 = (40 * fireGlowAlpha).toInt()
        val colors = intArrayOf(
            Color.argb(a1, 255, 80, 0),
            Color.argb(a2, 255, 140, 0),
            Color.argb(a3, 255, 200, 0),
            Color.argb(a4, 255, 160, 0),
            Color.TRANSPARENT
        )
        val positions = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 1f)
        fireGlowPaint.shader = RadialGradient(centerX, centerY, width * 0.8f, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fireGlowPaint)
    }

    private fun updateAndDrawRipples(canvas: Canvas, deltaTime: Long) {
        ripples.removeAll { ripple ->
            ripple.update(deltaTime)
            ripple.draw(canvas)
            ripple.isFinished
        }
    }

    private fun updateKeyStates(deltaTime: Long) {
        val currentTime = System.currentTimeMillis()
        for ((label, pressTime) in pressedKeys.toMap()) {
            val elapsed = currentTime - pressTime
            val newState = when {
                elapsed < 70 -> KeyState.WHITE
                elapsed < 140 -> KeyState.CYAN
                elapsed < 210 -> KeyState.PINK
                elapsed < 410 -> KeyState.FADE
                else -> KeyState.NORMAL
            }
            keyStates[label] = newState
            if (elapsed >= 410) { pressedKeys.remove(label) }
        }
    }

    private fun drawKey(canvas: Canvas, label: String, rect: Rect) {
        val state = keyStates[label] ?: KeyState.NORMAL
        when (state) {
            KeyState.WHITE -> {
                keyPaint.color = Color.WHITE
                textPaint.color = Color.BLACK
                keyPaint.setShadowLayer(35f, 0f, 0f, Color.WHITE)
            }
            KeyState.CYAN -> {
                keyPaint.color = Color.CYAN
                textPaint.color = Color.BLACK
                keyPaint.setShadowLayer(30f, 0f, 0f, Color.CYAN)
            }
            KeyState.PINK -> {
                keyPaint.color = Color.MAGENTA
                textPaint.color = Color.WHITE
                keyPaint.setShadowLayer(28f, 0f, 0f, Color.MAGENTA)
            }
            KeyState.FADE -> {
                keyPaint.color = Color.parseColor("#FF6400")
                textPaint.color = Color.WHITE
                keyPaint.setShadowLayer(22f, 0f, 0f, Color.parseColor("#FF6400"))
            }
            KeyState.NORMAL -> {
                keyPaint.color = Color.parseColor("#080808")
                textPaint.color = Color.parseColor("#885500")
                keyPaint.clearShadowLayer()
            }
        }
        canvas.drawRoundRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), 12f, 12f, keyPaint)
        canvas.drawRoundRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), 12f, 12f, keyBorderPaint)
        val displayLabel = when (label) {
            "Back" -> "\u232B"
            "Enter" -> "\u21B5"
            "Shift" -> "\u21E7"
            else -> if (isShifted && label.length == 1) label.uppercase() else label
        }
        canvas.drawText(displayLabel, rect.exactCenterX(), rect.exactCenterY() + (textPaint.textSize / 3f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                isSwiping = false
                lastTouchedKey = null
                handleTouchDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (distance > swipeThreshold) { isSwiping = true }
                if (!isSwiping) { handleTouchDown(event.x, event.y) }
                else { handleSwipeAnimation(event.x, event.y) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isSwiping && lastTouchedKey != null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastKeyTime > debounceInterval) {
                        lastKeyTime = currentTime
                        commitKey(lastTouchedKey!!)
                    }
                }
                lastTouchedKey = null
                isSwiping = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        for ((label, rect) in keyMap) {
            if (rect.contains(x.toInt(), y.toInt())) {
                lastTouchedKey = label
                animationEngine.triggerAnimation(rect.exactCenterX(), rect.exactCenterY(), label)
                ripples.add(RippleEffect(rect.exactCenterX(), rect.exactCenterY()))
                currentPopup = PopupEffect(label, rect.exactCenterX(), rect.top.toFloat() - 55f)
                pressedKeys[label] = System.currentTimeMillis()
                postInvalidateOnAnimation()
                break
            }
        }
    }

    private fun handleSwipeAnimation(x: Float, y: Float) {
        for ((label, rect) in keyMap) {
            if (rect.contains(x.toInt(), y.toInt())) {
                animationEngine.triggerAnimation(rect.exactCenterX(), rect.exactCenterY(), label)
                pressedKeys[label] = System.currentTimeMillis()
                postInvalidateOnAnimation()
                break
            }
        }
    }

    private fun commitKey(label: String) {
        when (label) {
            "Shift" -> {
                isShifted = !isShifted
                postInvalidateOnAnimation()
            }
            "Back" -> keyListener?.onKey(-5, "Back")
            "Enter" -> keyListener?.onKey(-4, "Enter")
            "Space" -> keyListener?.onKey(32, "Space")
            "123" -> {
                currentLayout = numberLayout
                createKeyMap(width, height)
                postInvalidateOnAnimation()
            }
            "ABC" -> {
                currentLayout = letterLayout
                createKeyMap(width, height)
                postInvalidateOnAnimation()
            }
            else -> {
                val finalLabel = if (isShifted && label.length == 1) label.uppercase() else label
                keyListener?.onKey(finalLabel.hashCode(), finalLabel)
                if (isShifted) {
                    isShifted = false
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    private inner class RippleEffect(private val cx: Float, private val cy: Float) {
        private var radius = 0f
        private var alpha = 255
        var isFinished = false
            private set
        private val maxRadius = 100f
        private val durationMs = 500L
        private var startTime = System.currentTimeMillis()
        fun update(deltaTime: Long): Boolean {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / durationMs.toFloat()
            if (progress >= 1.0f) { isFinished = true; return false }
            radius = maxRadius * progress
            alpha = (255 * (1 - progress)).toInt()
            return true
        }
        fun draw(canvas: Canvas) {
            val p = Paint().apply {
                isAntiAlias = true
                color = Color.argb(alpha, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, radius, p)
        }
    }

    private inner class PopupEffect(private val label: String, private val px: Float, private val py: Float) {
        private var alpha = 255
        private var offsetY = 10f
        var isFinished = false
            private set
        private val durationMs = 250L
        private var startTime = System.currentTimeMillis()
        fun draw(canvas: Canvas) {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / durationMs.toFloat()
            if (progress >= 1.0f) { isFinished = true; return }
            if (progress < 0.2f) {
                offsetY = 10f - (10f * (progress / 0.2f))
                alpha = 255
            } else {
                alpha = (255 * (1 - (progress - 0.2f) / 0.8f)).toInt()
            }
            val pw = 80f
            val ph = 60f
            popupPaint.alpha = alpha
            canvas.drawRoundRect(px - pw / 2, py + offsetY, px + pw / 2, py + offsetY + ph, 10f, 10f, popupPaint)
            popupBorderPaint.alpha = alpha
            canvas.drawRoundRect(px - pw / 2, py + offsetY, px + pw / 2, py + offsetY + ph, 10f, 10f, popupBorderPaint)
            popupTextPaint.alpha = alpha
            canvas.drawText(label.uppercase(), px, py + offsetY + ph / 2 + popupTextPaint.textSize / 3f, popupTextPaint)
        }
    }
}
