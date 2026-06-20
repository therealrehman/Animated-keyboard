package com.example.animatedkeyboard.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import com.example.animatedkeyboard.settings.KeyboardSettings

/**
 * KeySoundEngine — plays a short click sound on key press, using SoundPool.
 *
 * WHY SoundPool (not MediaPlayer):
 * MediaPlayer has noticeable startup latency (50-200ms) which is unacceptable for a
 * keyboard where every key press needs to feel instant. SoundPool pre-loads short
 * sound clips into memory and plays them with near-zero latency (<10ms), which is
 * the standard approach used by Gboard/SwiftKey for key click sounds.
 *
 * GRACEFUL DEGRADATION:
 * This project currently has NO bundled sound resource file (no res/raw/click.mp3).
 * Rather than crash or require one, this engine checks if a sound resource ID is
 * available and silently no-ops if not. This means:
 *   - The keyboard works perfectly fine with sound permanently "unavailable".
 *   - Sound becomes active the moment a real click.mp3/ogg is added to res/raw
 *     and its resource ID is wired into `loadDefaultClickSound()`.
 *
 * MISSING OPTIONAL RESOURCE:
 * A short (<100ms) click/tap sound file at res/raw/key_click.ogg (or .mp3) would
 * enable this feature. Recommended: a soft "tick" sound, normalized to avoid being
 * jarring. Until provided, sound stays disabled with zero crash risk.
 */
class KeySoundEngine(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = INVALID_SOUND_ID
    private var isLoaded = false

    private val settings by lazy { KeyboardSettings.getInstance(context) }

    init {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4) // allow a few overlapping clicks during fast typing
                .setAudioAttributes(attributes)
                .build()

            loadDefaultClickSound()
        } catch (e: Exception) {
            // Error case: device/OS-level audio initialization failure.
            // Recovery: keyboard continues to function silently; this is non-fatal.
            Log.w(TAG, "SoundPool initialization failed, sound disabled: ${e.message}")
            soundPool = null
        }
    }

    /**
     * Attempts to load a bundled click sound resource.
     * Currently a no-op placeholder since no res/raw sound file exists yet —
     * see class doc "MISSING OPTIONAL RESOURCE" above for how to wire one in.
     */
    private fun loadDefaultClickSound() {
        // Example of how this would look once a resource is added:
        //
        //   clickSoundId = soundPool?.load(context, R.raw.key_click, 1) ?: INVALID_SOUND_ID
        //   soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
        //       isLoaded = (status == 0 && sampleId == clickSoundId)
        //   }
        //
        // Left disabled until a real sound asset is provided.
        clickSoundId = INVALID_SOUND_ID
        isLoaded = false
    }

    /**
     * Plays the key click sound if: sound is enabled in settings AND a sound is loaded.
     * Safe to call on every key press — it's a fast no-op when sound isn't available.
     */
    fun playClick() {
        if (!settings.soundEnabled) return
        if (!isLoaded || clickSoundId == INVALID_SOUND_ID) return

        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val volume = am?.let {
                val current = it.getStreamVolume(AudioManager.STREAM_SYSTEM)
                val max = it.getStreamVolume(AudioManager.STREAM_SYSTEM).coerceAtLeast(1)
                (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            } ?: 0.3f

            soundPool?.play(clickSoundId, volume, volume, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
        } catch (e: Exception) {
            // Non-fatal: a single missed click sound should never disrupt typing.
            Log.w(TAG, "playClick failed: ${e.message}")
        }
    }

    /**
     * Releases native SoundPool resources. MUST be called from the IME's onDestroy()
     * to avoid leaking native audio memory across keyboard service restarts.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }

    companion object {
        private const val TAG = "KeySoundEngine"
        private const val INVALID_SOUND_ID = -1
    }
}
