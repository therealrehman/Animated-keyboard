package com.example.animatedkeyboard.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.example.animatedkeyboard.ui.view.KeyboardView

class AnimatedKeyboardIME : InputMethodService() {

    private lateinit var keyboardView: KeyboardView

    // FIX #4: Force keyboard to stay at bottom, never fullscreen
    override fun onEvaluateFullscreenMode(): Boolean = false
    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        keyboardView.setBackgroundColor(0x00000000)
        keyboardView.setOnCustomKeyListener(object : KeyboardView.OnKeyListener {
            override fun onKey(code: Int, label: String) {
                val ic = currentInputConnection ?: return
                when (code) {
                    -1 -> {} // Shift handled in view
                    -5 -> ic.deleteSurroundingText(1, 0)
                    -4 -> ic.commitText("\n", 1)
                    else -> {
                        if (label == "Space") ic.commitText(" ", 1)
                        else ic.commitText(label, 1)
                    }
                }
            }
        })
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // ORIENTATION HANDLING: When the device rotates (or enters/exits split-screen
        // / multi-window mode), the IME's width changes but onCreateInputView() is NOT
        // guaranteed to be called again. Without this, the keyboard could keep stale
        // key positions sized for the old orientation until forcibly recreated.
        // requestLayout() + invalidate() force KeyboardView's onMeasure/onSizeChanged
        // to re-run with the new dimensions.
        if (::keyboardView.isInitialized) {
            keyboardView.requestLayout()
            keyboardView.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // PERFORMANCE LAYER: Releases native SoundPool resources when the IME
        // service is torn down. Without this, repeated keyboard show/hide cycles
        // (which Android does often to save memory) would leak native audio
        // buffers over time, eventually causing OutOfMemoryError in long sessions.
        if (::keyboardView.isInitialized) {
            keyboardView.release()
        }
    }
}
