package com.example.animatedkeyboard.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.pow
import kotlin.random.Random

class AnimationEngine {
    private val activeAnimations = mutableListOf<GradientAnimation>()
    private val random = Random(System.currentTimeMillis())

    fun triggerAnimation(x: Float, y: Float, keyLabel: String) {
        val colors = getGradientColorsForKey(keyLabel)
        activeAnimations.add(GradientAnimation(x, y, colors))
    }

    fun update(elapsedTimeMs: Long) {
        activeAnimations.removeAll { anim ->
            anim.update(elapsedTimeMs)
            anim.isFinished
        }
    }

    fun draw(canvas: Canvas) {
        for (animation in activeAnimations) {
            animation.draw(canvas)
        }
    }
    
    fun hasActiveAnimations(): Boolean {
        return activeAnimations.isNotEmpty()
    }

    private fun getGradientColorsForKey(key: String): IntArray {
        // Exact colors from your HTML CSS
        return when(key.lowercase()) {
            "a" -> intArrayOf(
                Color.parseColor("#FF5050"),
                Color.parseColor("#FF6432"),
                Color.parseColor("#FF9600"),
                Color.parseColor("#FF6400"),
                Color.TRANSPARENT
            )
            "b" -> intArrayOf(
                Color.parseColor("#3296FF"),
                Color.parseColor("#32C8FF"),
                Color.parseColor("#0096FF"),
                Color.parseColor("#0064C8"),
                Color.TRANSPARENT
            )
            "c" -> intArrayOf(
                Color.parseColor("#FFDC00"),
                Color.parseColor("#FFF032"),
                Color.parseColor("#FFB400"),
                Color.parseColor("#C89600"),
                Color.TRANSPARENT
            )
            "d" -> intArrayOf(
                Color.parseColor("#00FF96"),
                Color.parseColor("#32FFB4"),
                Color.parseColor("#00C896"),
                Color.parseColor("#009664"),
                Color.TRANSPARENT
            )
            "e" -> intArrayOf(
                Color.parseColor("#FF00DC"),
                Color.parseColor("#FF32F0"),
                Color.parseColor("#C800C8"),
                Color.parseColor("#960096"),
                Color.TRANSPARENT
            )
            "f" -> intArrayOf(
                Color.parseColor("#FFA000"),
                Color.parseColor("#FFC832"),
                Color.parseColor("#FF7800"),
                Color.parseColor("#C86400"),
                Color.TRANSPARENT
            )
            "g" -> intArrayOf(
                Color.parseColor("#B432FF"),
                Color.parseColor("#C864FF"),
                Color.parseColor("#9600FF"),
                Color.parseColor("#6400C8"),
                Color.TRANSPARENT
            )
            "h" -> intArrayOf(
                Color.parseColor("#00FFFF"),
                Color.parseColor("#32FFFF"),
                Color.parseColor("#00C8C8"),
                Color.parseColor("#009696"),
                Color.TRANSPARENT
            )
            "i" -> intArrayOf(
                Color.parseColor("#FF6464"),
                Color.parseColor("#FF9696"),
                Color.parseColor("#C83232"),
                Color.parseColor("#963232"),
                Color.TRANSPARENT
            )
            "j" -> intArrayOf(
                Color.parseColor("#64FF64"),
                Color.parseColor("#96FF96"),
                Color.parseColor("#32C832"),
                Color.parseColor("#329632"),
                Color.TRANSPARENT
            )
            "k" -> intArrayOf(
                Color.parseColor("#FFFF32"),
                Color.parseColor("#FFFF78"),
                Color.parseColor("#C8C800"),
                Color.parseColor("#969600"),
                Color.TRANSPARENT
            )
            "l" -> intArrayOf(
                Color.parseColor("#FF64C8"),
                Color.parseColor("#FF96DC"),
                Color.parseColor("#C83296"),
                Color.parseColor("#963264"),
                Color.TRANSPARENT
            )
            "m" -> intArrayOf(
                Color.parseColor("#64C8FF"),
                Color.parseColor("#96DCFF"),
                Color.parseColor("#3296C8"),
                Color.parseColor("#326496"),
                Color.TRANSPARENT
            )
            "n" -> intArrayOf(
                Color.parseColor("#FFC832"),
                Color.parseColor("#FFDC64"),
                Color.parseColor("#C89632"),
                Color.parseColor("#966400"),
                Color.TRANSPARENT
            )
            "o" -> intArrayOf(
                Color.parseColor("#DC64FF"),
                Color.parseColor("#F096FF"),
                Color.parseColor("#B432DC"),
                Color.parseColor("#8200B4"),
                Color.TRANSPARENT
            )
            "p" -> intArrayOf(
                Color.parseColor("#32DCFF"),
                Color.parseColor("#64F0FF"),
                Color.parseColor("#00B4DC"),
                Color.parseColor("#0082B4"),
                Color.TRANSPARENT
            )
            "q" -> intArrayOf(
                Color.parseColor("#FF5050"),
                Color.parseColor("#FF8282"),
                Color.parseColor("#C83232"),
                Color.parseColor("#960000"),
                Color.TRANSPARENT
            )
            "r" -> intArrayOf(
                Color.parseColor("#50FF96"),
                Color.parseColor("#82FFBE"),
                Color.parseColor("#32C864"),
                Color.parseColor("#009650"),
                Color.TRANSPARENT
            )
            "s" -> intArrayOf(
                Color.parseColor("#FFF032"),
                Color.parseColor("#FFFF78"),
                Color.parseColor("#C8C800"),
                Color.parseColor("#968200"),
                Color.TRANSPARENT
            )
            "t" -> intArrayOf(
                Color.parseColor("#C864FF"),
                Color.parseColor("#DC96FF"),
                Color.parseColor("#A032DC"),
                Color.parseColor("#6E00AA"),
                Color.TRANSPARENT
            )
            "u" -> intArrayOf(
                Color.parseColor("#64FFC8"),
                Color.parseColor("#96FFE6"),
                Color.parseColor("#32C8A0"),
                Color.parseColor("#00966E"),
                Color.TRANSPARENT
            )
            "v" -> intArrayOf(
                Color.parseColor("#FFA064"),
                Color.parseColor("#FFC896"),
                Color.parseColor("#C86432"),
                Color.parseColor("#963C00"),
                Color.TRANSPARENT
            )
            "w" -> intArrayOf(
                Color.parseColor("#64A0FF"),
                Color.parseColor("#96C8FF"),
                Color.parseColor("#3278DC"),
                Color.parseColor("#0050B4"),
                Color.TRANSPARENT
            )
            "x" -> intArrayOf(
                Color.parseColor("#FFFF96"),
                Color.parseColor("#FFFFDC"),
                Color.parseColor("#DCDC32"),
                Color.parseColor("#AAAA00"),
                Color.TRANSPARENT
            )
            "y" -> intArrayOf(
                Color.parseColor("#FF5096"),
                Color.parseColor("#FF82BE"),
                Color.parseColor("#C81E78"),
                Color.parseColor("#960050"),
                Color.TRANSPARENT
            )
            "z" -> intArrayOf(
                Color.parseColor("#50FFDC"),
                Color.parseColor("#82FFF0"),
                Color.parseColor("#1EC8B4"),
                Color.parseColor("#009682"),
                Color.TRANSPARENT
            )
            "0" -> intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#DCE0FF"),
                Color.parseColor("#B4B4DC"),
                Color.parseColor("#8282B4"),
                Color.TRANSPARENT
            )
            "1" -> intArrayOf(
                Color.parseColor("#FF6432"),
                Color.parseColor("#FF9664"),
                Color.parseColor("#DC3200"),
                Color.parseColor("#AA0000"),
                Color.TRANSPARENT
            )
            "2" -> intArrayOf(
                Color.parseColor("#32DC64"),
                Color.parseColor("#64FF96"),
                Color.parseColor("#00B432"),
                Color.parseColor("#008200"),
                Color.TRANSPARENT
            )
            "3" -> intArrayOf(
                Color.parseColor("#FFDC32"),
                Color.parseColor("#FFF064"),
                Color.parseColor("#DCB400"),
                Color.parseColor("#AA8200"),
                Color.TRANSPARENT
            )
            "4" -> intArrayOf(
                Color.parseColor("#7878FF"),
                Color.parseColor("#AAAAFF"),
                Color.parseColor("#5050DC"),
                Color.parseColor("#1E1EB4"),
                Color.TRANSPARENT
            )
            "5" -> intArrayOf(
                Color.parseColor("#FFB4DC"),
                Color.parseColor("#FFDCF0"),
                Color.parseColor("#DC78B4"),
                Color.parseColor("#AA3C82"),
                Color.TRANSPARENT
            )
            "6" -> intArrayOf(
                Color.parseColor("#B4FF64"),
                Color.parseColor("#D2FF96"),
                Color.parseColor("#82DC32"),
                Color.parseColor("#50AA00"),
                Color.TRANSPARENT
            )
            "7" -> intArrayOf(
                Color.parseColor("#FFC896"),
                Color.parseColor("#FFE6C8"),
                Color.parseColor("#DCA064"),
                Color.parseColor("#AA6E32"),
                Color.TRANSPARENT
            )
            "8" -> intArrayOf(
                Color.parseColor("#64E6FF"),
                Color.parseColor("#96FAFF"),
                Color.parseColor("#32C8E6"),
                Color.parseColor("#0096B4"),
                Color.TRANSPARENT
            )
            "9" -> intArrayOf(
                Color.parseColor("#E664FF"),
                Color.parseColor("#FA96FF"),
                Color.parseColor("#C832E6"),
                Color.parseColor("#9600B4"),
                Color.TRANSPARENT
            )
            "shift" -> intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#DCDCDC"),
                Color.parseColor("#B4B4B4"),
                Color.parseColor("#828282"),
                Color.TRANSPARENT
            )
            "backspace", "back" -> intArrayOf(
                Color.parseColor("#FF6464"),
                Color.parseColor("#FF9696"),
                Color.parseColor("#DC3232"),
                Color.parseColor("#AA0000"),
                Color.TRANSPARENT
            )
            "enter" -> intArrayOf(
                Color.parseColor("#64FF96"),
                Color.parseColor("#96FFBE"),
                Color.parseColor("#32DC64"),
                Color.parseColor("#00AA3C"),
                Color.TRANSPARENT
            )
            "space" -> intArrayOf(
                Color.parseColor("#FFC864"),
                Color.parseColor("#FFDC96"),
                Color.parseColor("#DCA032"),
                Color.parseColor("#AA6E00"),
                Color.TRANSPARENT
            )
            "symbols", "123" -> intArrayOf(
                Color.parseColor("#C8C8C8"),
                Color.parseColor("#B4B4B4"),
                Color.parseColor("#969696"),
                Color.parseColor("#646464"),
                Color.TRANSPARENT
            )
            ",", "." -> intArrayOf(
                Color.parseColor("#C8C8C8"),
                Color.parseColor("#B4B4B4"),
                Color.parseColor("#969696"),
                Color.parseColor("#646464"),
                Color.TRANSPARENT
            )
            else -> intArrayOf(
                Color.parseColor("#FFAA32"),
                Color.parseColor("#FFC864"),
                Color.parseColor("#DC9600"),
                Color.parseColor("#AA6400"),
                Color.TRANSPARENT
            )
        }
    }

    private class GradientAnimation(
        private val centerX: Float,
        private val centerY: Float,
        private val colors: IntArray
    ) {
        var radius = 0f
            private set
        var isFinished = false
            private set

        private val maxRadius = 800f 
        private val durationMs = 800L
        private var startTime = System.currentTimeMillis()

        fun update(elapsedTimeMs: Long): Boolean {
            val progress = (System.currentTimeMillis() - startTime).toFloat() / durationMs.toFloat()
            if (progress >= 1.0f) {
                isFinished = true
                return false
            }

            radius = maxRadius * (1 - (1 - progress).toDouble().pow(2.0)).toFloat()
            return true
        }

        fun draw(canvas: Canvas) {
            if (radius <= 0) return
            val paint = Paint().apply {
                isAntiAlias = true
                shader = RadialGradient(
                    centerX, centerY, radius,
                    colors,
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(centerX, centerY, radius, paint)
        }
    }
}
