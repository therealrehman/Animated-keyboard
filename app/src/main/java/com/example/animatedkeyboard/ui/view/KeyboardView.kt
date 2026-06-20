package com.example.animatedkeyboard.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.animatedkeyboard.audio.KeySoundEngine
import com.example.animatedkeyboard.settings.KeyboardSettings
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

    // Settings + sound engine — wired in but fully optional; keyboard works
    // identically if these are never touched (defaults preserve current behavior).
    private val settings by lazy { KeyboardSettings.getInstance(context) }
    private val soundEngine by lazy { KeySoundEngine(context) }

    fun setOnCustomKeyListener(listener: OnKeyListener) {
        this.keyListener = listener
    }

    /**
     * Releases native resources (sound engine) and cancels any pending callbacks.
     * Must be called from the hosting IME's onDestroy() — see AnimatedKeyboardIME.
     */
    fun release() {
        handler.removeCallbacks(backspaceRunnable ?: Runnable {})
        soundEngine.release()
    }

    companion object {
        private const val TAG = "KeyboardView"
    }

    // --- DP-based sizing system ---
    // All spacing/sizing constants are defined in dp and converted to px using
    // the device's display density, so the keyboard looks correctly
    // proportioned on every screen size/density (mdpi, hdpi, xhdpi, etc.)
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val horizontalKeyGapDp = 4f   // gap between keys in the same row
    private val verticalRowGapDp = 6f     // gap between rows
    private val sideMarginDp = 3f         // left/right edge margin of keyboard
    private val topBottomMarginDp = 4f    // top/bottom edge margin of keyboard
    private val keyCornerRadiusDp = 5f    // rounded corner radius on keys
    private val keyboardHeightFraction = 0.304 // 80% of previous 38% height
    private val spaceRowHeightFactor = 0.88f  // bottom row slightly shorter than others

    private val keyPaint = Paint()
    private val keyBorderPaint = Paint()
    private val textPaint = Paint()
    private val animationEngine = AnimationEngine()
    private var lastFrameTime = 0L
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
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("Shift", "z", "x", "c", "v", "b", "n", "m", "Del"),
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
        setBackgroundColor(Color.BLACK)

        // ACCESSIBILITY LAYER (basic level):
        // Marks this view as accessibility-relevant so TalkBack announces it exists.
        // Full per-key accessibility (individual focusable nodes for each key) would
        // require implementing AccessibilityNodeProvider — a significantly larger
        // undertaking. This basic level ensures TalkBack users at least know a
        // keyboard is present and can identify it, which is the minimum viable
        // accessibility support. See "MISSING OPTIONAL RESOURCES" notes for the
        // full per-key AccessibilityNodeProvider upgrade path.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "ChromaTap keyboard"

        keyPaint.color = Color.parseColor("#080808")
        keyPaint.isAntiAlias = true
        keyPaint.style = Paint.Style.FILL
        keyBorderPaint.color = Color.parseColor("#1A1A1A")
        keyBorderPaint.isAntiAlias = true
        keyBorderPaint.style = Paint.Style.STROKE
        keyBorderPaint.strokeWidth = dp(1f)
        textPaint.color = Color.parseColor("#885500")
        textPaint.textSize = dp(15f)
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        popupPaint.color = Color.parseColor("#1E1E1E")
        popupPaint.isAntiAlias = true
        popupBorderPaint.color = Color.WHITE
        popupBorderPaint.isAntiAlias = true
        popupBorderPaint.style = Paint.Style.STROKE
        popupBorderPaint.strokeWidth = dp(1.5f)
        popupTextPaint.color = Color.WHITE
        popupTextPaint.textSize = dp(22f) // Magnified popup text, density-independent
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
        val desiredHeight = (dm.heightPixels * keyboardHeightFraction).toInt()
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
        // ERROR HANDLING LAYER: Orientation changes, split-screen resizing, and
        // foldable screen-fold events can deliver unusual (even momentarily zero)
        // width/height values. Guard against degenerate input so we never divide
        // by zero or produce a corrupt key map that would make the keyboard
        // unusable until the next size change.
        if (width <= 0 || height <= 0 || currentLayout.isEmpty()) {
            Log.w(TAG, "createKeyMap skipped: invalid dimensions ($width x $height)")
            return
        }

        try {
            buildKeyMapInternal(width, height)
        } catch (e: Exception) {
            Log.e(TAG, "createKeyMap failed, keyMap may be incomplete: ${e.message}")
        }
    }

    private fun buildKeyMapInternal(width: Int, height: Int) {
        keyMap.clear()

        val sideMargin = dp(sideMarginDp).toInt()
        val topBottomMargin = dp(topBottomMarginDp).toInt()
        val hGap = dp(horizontalKeyGapDp).toInt()
        val vGap = dp(verticalRowGapDp).toInt()

        val rowCount = currentLayout.size
        val availableHeight = height - (topBottomMargin * 2) - (vGap * (rowCount - 1))

        // Bottom row (space bar row) is slightly shorter; other rows share the rest equally.
        // We compute a "unit" height so total height accounting for the shrink factor fits exactly.
        val unitHeight = (availableHeight / (rowCount - 1 + spaceRowHeightFactor)).toInt()
        val normalRowHeight = unitHeight
        val lastRowHeight = (unitHeight * spaceRowHeightFactor).toInt()

        var currentY = topBottomMargin

        for ((rowIndex, row) in currentLayout.withIndex()) {
            val isLastRow = rowIndex == currentLayout.lastIndex
            val rowKeyHeight = if (isLastRow) lastRowHeight else normalRowHeight

            val availableRowWidth = width - (sideMargin * 2) - (hGap * (row.size - 1))
            var totalWeight = 0.0
            for (item in row) {
                totalWeight += getWeight(item).toDouble()
            }
            val tw = totalWeight.toFloat()
            var currentX = sideMargin

            for ((keyIndex, keyLabel) in row.withIndex()) {
                val isLastKeyInRow = keyIndex == row.lastIndex
                val kw = (availableRowWidth * (getWeight(keyLabel) / tw)).roundToInt()
                val safeRight = if (isLastKeyInRow) (width - sideMargin) else (currentX + kw)
                keyMap[keyLabel] = Rect(currentX, currentY, safeRight, currentY + rowKeyHeight)
                keyStates[keyLabel] = KeyState.NORMAL
                currentX = safeRight + hGap
            }
            currentY += rowKeyHeight + vGap
        }
    }

    private fun getWeight(label: String): Float {
        return when (label) {
            "Space" -> 3.5f
            "Shift", "Del", "123", "ABC" -> 1.4f
            "Go" -> 1.4f
            else -> 1.0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime == 0L) 16 else now - lastFrameTime
        lastFrameTime = now

        canvas.drawColor(Color.BLACK)

        // ERROR HANDLING LAYER:
        // Why this exists: onDraw runs on every single frame. Any uncaught exception
        // here (e.g. a malformed Rect, a null Paint shader, an animation edge case)
        // would crash the IME process, which force-closes the keyboard mid-typing in
        // WHATEVER app the user currently has open (WhatsApp, browser, etc.) — a much
        // worse experience than just losing the fancy animation for one frame.
        // Recovery strategy: if the animated/decorated rendering path fails, we fall
        // back to drawing plain, undecorated keys so the keyboard STAYS USABLE.
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Rendering error in onDraw, falling back to plain keys: ${e.message}")
            drawFallbackKeys(canvas)
        }
    }

    /**
     * Minimal, animation-free rendering of the keyboard grid.
     * Used only as a crash-recovery fallback — see onDraw's try/catch above.
     * Guarantees the user can still see and tap keys even if the decorated
     * rendering pipeline (animations/ripples/popups) hits an unexpected error.
     */
    private fun drawFallbackKeys(canvas: Canvas) {
        try {
            for ((label, rect) in keyMap) {
                canvas.drawRect(rect, keyPaint)
                canvas.drawText(
                    label,
                    rect.exactCenterX(),
                    rect.exactCenterY() + (textPaint.textSize / 3f),
                    textPaint
                )
            }
        } catch (e: Exception) {
            // If even the fallback fails, there is nothing safe left to draw this frame.
            // We deliberately swallow this rather than rethrow, since throwing here
            // would still crash the IME — the one outcome this layer exists to prevent.
            Log.e(TAG, "Fallback rendering also failed: ${e.message}")
        }
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

        // Small inner shrink (5%) — visual breathing room within each key's own cell.
        // Actual spacing between keys is handled by hGap/vGap in createKeyMap, not this shrink.
        val keyMargin = ((r - l) * 0.05f)
        val cornerRadius = dp(keyCornerRadiusDp)
        canvas.drawRoundRect(l + keyMargin, t + keyMargin, r - keyMargin, b - keyMargin, cornerRadius, cornerRadius, keyPaint)
        canvas.drawRoundRect(l + keyMargin, t + keyMargin, r - keyMargin, b - keyMargin, cornerRadius, cornerRadius, keyBorderPaint)

        val dl = if (isShifted && label.length == 1 && label[0].isLetter()) label.uppercase() else label

        when (label) {
            "Shift" -> drawShiftIcon(canvas, rect, textPaint.color)
            "Del" -> drawBackspaceIcon(canvas, rect, textPaint.color)
            else -> canvas.drawText(dl, rect.exactCenterX(), rect.exactCenterY() + (textPaint.textSize / 3f), textPaint)
        }
    }

    private val iconPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Shift key icon: simple upward-pointing arrow (caps-lock style arrow)
    private fun drawShiftIcon(canvas: Canvas, rect: Rect, color: Int) {
        iconPaint.color = if (isShifted) Color.parseColor("#FFCC00") else color
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        val s = minOf(rect.width(), rect.height()) * 0.22f // icon scale

        val path = android.graphics.Path()
        // Arrow head (triangle)
        path.moveTo(cx, cy - s * 1.3f)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        canvas.drawPath(path, iconPaint)
    }

    // Backspace/Del key icon: simple left-pointing arrow with an X
    private fun drawBackspaceIcon(canvas: Canvas, rect: Rect, color: Int) {
        iconPaint.color = color
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(1.8f)
        iconPaint.strokeCap = Paint.Cap.ROUND

        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        val s = minOf(rect.width(), rect.height()) * 0.20f

        // Arrow body (pointed left, like a backspace key shape)
        val bodyPath = android.graphics.Path()
        bodyPath.moveTo(cx - s * 1.3f, cy)
        bodyPath.lineTo(cx - s * 0.5f, cy - s)
        bodyPath.lineTo(cx + s * 1.1f, cy - s)
        bodyPath.lineTo(cx + s * 1.1f, cy + s)
        bodyPath.lineTo(cx - s * 0.5f, cy + s)
        bodyPath.close()
        canvas.drawPath(bodyPath, iconPaint)

        // X mark inside
        val xOffset = s * 0.35f
        canvas.drawLine(cx - xOffset, cy - xOffset * 0.7f, cx + xOffset, cy + xOffset * 0.7f, iconPaint)
        canvas.drawLine(cx + xOffset, cy - xOffset * 0.7f, cx - xOffset, cy + xOffset * 0.7f, iconPaint)

        iconPaint.style = Paint.Style.FILL // reset for next use
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

                // Haptic + sound feedback — both respect user settings and are
                // purely additive; they do not affect the colored animation system.
                if (settings.hapticEnabled) {
                    performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }
                soundEngine.playClick()

                animationEngine.triggerAnimation(rect.exactCenterX(), rect.exactCenterY(), label)
                ripples.add(RippleEffect(rect.exactCenterX(), rect.exactCenterY()))
                currentPopup = PopupEffect(
                    label,
                    rect.exactCenterX(),
                    rect.top.toFloat() - dp(20f),
                    rect.width().toFloat(),
                    rect.height().toFloat()
                )
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
                                handler.postDelayed(this, settings.backspaceRepeatIntervalMs)
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
        announceKeyForAccessibility(label)
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

    /**
     * Announces the pressed key's label to TalkBack/screen readers, so visually
     * impaired users get audio confirmation of what was typed — without this,
     * a screen-reader user would have no feedback that their tap registered.
     * Uses friendly names for control keys instead of raw internal labels.
     */
    private fun announceKeyForAccessibility(label: String) {
        if (!isAccessibilityLiveRegionRelevant()) return
        val spoken = when (label) {
            "Del" -> "Backspace"
            "Go" -> "Enter"
            "Space" -> "Space"
            "Shift" -> if (isShifted) "Shift off" else "Shift on"
            "123" -> "Numbers"
            "ABC" -> "Letters"
            else -> label
        }
        try {
            announceForAccessibility(spoken)
        } catch (e: Exception) {
            // Non-fatal: missing accessibility announcement should never block typing.
            Log.w(TAG, "Accessibility announcement failed: ${e.message}")
        }
    }

    private fun isAccessibilityLiveRegionRelevant(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager
        return am?.isEnabled == true
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

    private inner class PopupEffect(
        private val lbl: String,
        private val px: Float,
        private val py: Float,
        private val keyWidth: Float,
        private val keyHeight: Float
    ) {
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
            // Popup is 20% larger than the actual key button size
            val pw = keyWidth * 1.2f
            val ph = keyHeight * 1.2f
            popupPaint.alpha = alp
            canvas.drawRoundRect(px - pw / 2, py + offY, px + pw / 2, py + offY + ph, 15f, 15f, popupPaint)
            popupBorderPaint.alpha = alp
            canvas.drawRoundRect(px - pw / 2, py + offY, px + pw / 2, py + offY + ph, 15f, 15f, popupBorderPaint)
            popupTextPaint.alpha = alp
            canvas.drawText(lbl.uppercase(), px, py + offY + ph / 2 + popupTextPaint.textSize / 3f, popupTextPaint)
        }
    }
}
