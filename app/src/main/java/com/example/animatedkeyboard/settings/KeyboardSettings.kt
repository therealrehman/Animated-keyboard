package com.example.animatedkeyboard.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * KeyboardSettings — persists user preferences across app/keyboard restarts.
 *
 * WHY THIS EXISTS:
 * Without persistence, every preference (haptic, sound, key-press-animation strength)
 * would reset to default each time the keyboard service restarts (which Android does
 * frequently to save memory). SharedPreferences is the simplest, most reliable storage
 * for small key-value settings like this — no extra library/dependency needed.
 *
 * WHAT WOULD BREAK IF REMOVED:
 * Settings screen (if added later) would have nothing to read from or write to;
 * every toggle the user flips would be forgotten on the next keyboard session.
 */
class KeyboardSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Haptic feedback ---
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true) // default: ON
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    // --- Key click sound ---
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, false) // default: OFF (most users prefer silent)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    // --- Long-press repeat speed for backspace (ms between repeats) ---
    var backspaceRepeatIntervalMs: Long
        get() = prefs.getLong(KEY_BACKSPACE_REPEAT_MS, 50L)
        set(value) = prefs.edit().putLong(KEY_BACKSPACE_REPEAT_MS, value).apply()

    // --- Key press animation enabled (the colored White->Pink flash + ripple + popup) ---
    // NOTE: This toggles the EXISTING colored animation system on/off; it does not
    // change its colors or timing. Default stays ON so behavior is unchanged unless
    // the user explicitly disables it in a future settings screen.
    var keyAnimationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "chromatap_keyboard_settings"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_BACKSPACE_REPEAT_MS = "backspace_repeat_ms"
        private const val KEY_ANIMATIONS_ENABLED = "key_animations_enabled"

        @Volatile
        private var INSTANCE: KeyboardSettings? = null

        /** Singleton accessor — avoids creating multiple SharedPreferences instances. */
        fun getInstance(context: Context): KeyboardSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KeyboardSettings(context).also { INSTANCE = it }
            }
        }
    }
}
