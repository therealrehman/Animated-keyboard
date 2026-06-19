package com.example.animatedkeyboard.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.animatedkeyboard.utils.AnimationEngine
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class KeyState { NORMAL, WHITE, PINK, FADE }

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyListener {
        fun onKey(code: Int, label: String)
    }

    private var keyListener: OnKeyListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null

    fun setOnCustomKeyListener(listener: OnKeyListener) {
        this.keyListener = listener
    }

    private val keyPaint = Paint()
    private val keyBorderPaint = Paint()
    private val textPaint = Paint()
    private val animationEngine = AnimationEngine()
    private var lastFrameTime = 0L
    private var fireGlowAlpha = 0.5f
    private var fireGlowDirection = -1
    private val fireGlowPaint = Paint()
    private val pressedKeys = mutableMapOf<String, Long>()
    private val keyStates = mutableMapOf<String, KeyState>()
    private val ripples = mutableListOf<RippleEffect>()
    private var currentPopup: PopupEffect? = null
    private val popupPaint = Paint()
    private val popupBorderPaint = Paint()
    private val popupTextPaint = Paint()

    // Numbers row added at top
    private val letterLayout = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Del"),
        listOf("123", ",", "Space", ".", "Go")
    )

    private val numberLayout = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "^", "&", "*", "(", ")", "_"),
        listOf("+=", "ABC", ".", ",", "Del"),
        listOf("Space", "Go")
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
    private var isLongPress = false
    private var longPressKey: String? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x00000000)
        keyPaint.color = Color.parseColor("#080808")
        keyPaint.isAntiAlias = true
        keyPaint.style = Paint.Style.FILL
        keyBorderPaint.color = Color.parseColor("#1A1A1A")
        keyBorderPaint.isAntiAlias = true
        keyBorderPaint.style = Paint.Style.STROKE
        keyBorderPaint.strokeWidth = 2f
        textPaint.color = Color.parseColor("#885500")
        textPaint.textSize = 42f
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        fireGlowPaint.isAntiAlias = true
        popupPaint.color = Color.parseColor("#1E1E1E")
        popupPaint.isAntiAlias = true
        popupBorderPaint.color = Color.parseColor("#FFAA00")
        popupBorderPaint.isAntiAlias = true
        popupBorderPaint.style = Paint.Style.STROKE
        popupBorderPaint.strokeWidth = 3f
        popupTextPaint.color = Color.parseColor("#FFCC00")
        popupTextPaint.textSize = 65f // Increased popup text size (was 52f)
        popupTextPaint.isAntiAlias = true
        popupTextPaint.textAlign = Paint.Align.CENTER
        popupTextPaint.isFakeBoldText = true
        keyCodes["Shift"] = -1
        keyCodes["Del"] = -5
        keyCodes["Go"] = -4
        keyCodes["Space"] = 32
        keyCodes["123"] = -2
        keyCodes["ABC"] = -3
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val dm = context.resources.displayMetrics
        val desiredHeight = (dm.heightPixels * 0.24).toInt()
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
        val keyHeight = (height / 5).toInt() // 5 rows now
        val rowHeight = keyHeight + (padding / 2)
        var currentY = padding

        for (row in currentLayout) {
            val rowWidth = width - (padding * 2)
            var totalWeight = 0.0
            for (item in row) {
                totalWeight += getWeight(item).toDouble()
            }
            val tw = totalWeight.toFloat()
            var currentX = padding

            for (keyLabel in row) {
                val kw = (rowWidth * (getWeight(keyLabel) / tw)).roundToInt()
                val skw = minOf(kw, width - currentX - padding)
                keyMap[keyLabel] = Rect(currentX, currentY, currentX + skw, currentY + keyHeight)
                keyStates[keyLabel] = KeyState.NORMAL
                currentX += skw + (padding / 2)
            }
            currentY += rowHeight
        }
    }

    private fun getWeight(label: String): Float {
        return when (label) {
            "Space" -> 3.5f
            "Shift", "Del", "123", "ABC" -> 1.3f
            "Go" -> 1.5f
            else -> 1.0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime == 0L) 16 else now - lastFrameTime
        lastFrameTime = now

        canvas.drawColor(0x00000000)
        drawFireGlow(canvas)
        animationEngine.update(dt)
        animationEngine.draw(canvas)
        updateRipples(canvas, dt)
        updateKeyStates()
        for ((label, rect) in keyMap) {
            drawKey(canvas, label, rect)
        }
        currentPopup?.draw(canvas)
        if (animationEngine.hasActiveAnimations() || ripples.isNotEmpty() || currentPopup != null) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawFireGlow(canvas: Canvas) {
        fireGlowAlpha += fireGlowDirection * 0.005f
        if (fireGlowAlpha <= 0.3f || fireGlowAlpha >= 0.7f) {
            fireGlowDirection *= -1
        }
        val cx = width / 2f
        val cy = height.toFloat()
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
        val pos = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 1f)
        fireGlowPaint.shader = RadialGradient(cx, cy, width * 0.8f, colors, pos, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fireGlowPaint)
    }

    private fun updateRipples(canvas: Canvas, dt: Long) {
        val it = ripples.iterator()
        while (it.hasNext()) {
            val r = it.next()
            r.update(dt)
            r.draw(canvas)
            if (r.finished) {
                it.remove()
            }
        }
    }

    private fun updateKeyStates() {
        val now = System.currentTimeMillis()
        val entries = pressedKeys.entries.toList()
        for (entry in entries) {
            val elapsed = now - entry.value
            val ns = when {
                elapsed < 70 -> KeyState.WHITE
                elapsed < 140 -> KeyState.PINK // Removed CYAN, now WHITE -> PINK
                elapsed < 210 -> KeyState.PINK
                elapsed < 410 -> KeyState.FADE
                else -> KeyState.NORMAL
            }
            keyStates[entry.key] = ns
            if (elapsed >= 410) {
                pressedKeys.remove(entry.key)
            }
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

        val l = rect.left.toFloat()
        val t = rect.top.toFloat()
        val r = rect.right.toFloat()
        val b = rect.bottom.toFloat()

        // Reduced key background size to 70% (was 100%)
        val keyMargin = ((r - l) * 0.15f) // 15% margin on each side = 30% total reduction
        canvas.drawRoundRect(l + keyMargin, t + keyMargin, r - keyMargin, b - keyMargin, 12f, 12f, keyPaint)
        canvas.drawRoundRect(l + keyMargin, t + keyMargin, r - keyMargin, b - keyMargin, 12f, 12f, keyBorderPaint)

        val dl = if (isShifted && label.length == 1 && label[0].isLetter()) label.uppercase() else label
        canvas.drawText(dl, rect.exactCenterX(), rect.exactCenterY() + (textPaint.textSize / 3f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                isSwiping = false
                lastTouchedKey = null
                isLongPress = false
                handleTouchDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (dist > swipeThreshold) {
                    isSwiping = true
                }
                if (!isSwiping) {
                    handleTouchDown(event.x, event.y)
                } else {
                    handleSwipeAnim(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(backspaceRunnable ?: Runnable {})
                if (!isSwiping && lastTouchedKey != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastKeyTime > debounceInterval) {
                        lastKeyTime = now
                        commitKey(lastTouchedKey!!)
                    }
                }
                lastTouchedKey = null
                isSwiping = false
                isLongPress = false
                longPressKey = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(backspaceRunnable ?: Runnable {})
                lastTouchedKey = null
                isSwiping = false
                isLongPress = false
                longPressKey = null
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

                // Handle long press for backspace
                if (label == "Del") {
                    isLongPress = true
                    longPressKey = label
                    backspaceRunnable = object : Runnable {
                        override fun run() {
                            if (isLongPress && longPressKey == "Del") {
                                keyListener?.onKey(-5, "Del")
                                handler.postDelayed(this, 50) // Repeat every 50ms
                            }
                        }
                    }
                    handler.postDelayed(backspaceRunnable!!, 500) // Start after 500ms long press
                }
                break
            }
        }
    }

    private fun handleSwipeAnim(x: Float, y: Float) {
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
            "Del" -> keyListener?.onKey(-5, "Del")
            "Go" -> keyListener?.onKey(-4, "Go")
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
                val fl = if (isShifted && label.length == 1 && label[0].isLetter()) label.uppercase() else label
                keyListener?.onKey(fl.hashCode(), fl)

                if (isShifted && label[0].isLetter()) {
                    isShifted = false
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    private inner class RippleEffect(private val cx: Float, private val cy: Float) {
        private var radius = 0f
        private var alp = 255
        var finished = false
        private val maxR = 100f
        private val dur = 500L
        private val start = System.currentTimeMillis()

        fun update(dt: Long) {
            val p = (System.currentTimeMillis() - start).toFloat() / dur.toFloat()
            if (p >= 1.0f) {
                finished = true
                return
            }
            radius = maxR * p
            alp = (255 * (1 - p)).toInt()
        }

        fun draw(canvas: Canvas) {
            val pt = Paint()
            pt.isAntiAlias = true
            pt.color = Color.argb(alp, 255, 255, 255)
            canvas.drawCircle(cx, cy, radius, pt)
        }
    }

    private inner class PopupEffect(private val lbl: String, private val px: Float, private val py: Float) {
        private var alp = 255
        private var offY = 10f
        var finished = false
        private val dur = 250L
        private val start = System.currentTimeMillis()

        fun draw(canvas: Canvas) {
            val p = (System.currentTimeMillis() - start).toFloat() / dur.toFloat()
            if (p >= 1.0f) {
                finished = true
                return
            }
            if (p < 0.2f) {
                offY = 10f - (10f * (p / 0.2f))
                alp = 255
            } else {
                alp = (255 * (1 - (p - 0.2f) / 0.8f)).toInt()
            }
            val pw = 100f // Increased from 80f
            val ph = 80f // Increased from 60f
            popupPaint.alpha = alp
            canvas.drawRoundRect(px - pw / 2, py + offY, px + pw / 2, py + offY + ph, 15f, 15f, popupPaint)
            popupBorderPaint.alpha = alp
            canvas.drawRoundRect(px - pw / 2, py + offY, px + pw / 2, py + offY + ph, 15f, 15f, popupBorderPaint)
            popupTextPaint.alpha = alp
            canvas.drawText(lbl.uppercase(), px, py + offY + ph / 2 + popupTextPaint.textSize / 3f, popupTextPaint)
        }
    }
}
