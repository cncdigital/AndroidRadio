package mx.sntss1puebla.credenciales

import android.content.Context

/** Modest fixed profiles; no loudness gain or claim of clipping prevention. */
internal data class RadioAudioSettings(
    val enabled: Boolean = false,
    val equalizer: Boolean = true,
    val bass: Boolean = false,
    val spatial: Boolean = false,
) {
    fun requested(kind: RadioAudioEffectKind): Boolean = enabled && when (kind) {
        RadioAudioEffectKind.EQUALIZER -> equalizer
        RadioAudioEffectKind.BASS -> bass
        RadioAudioEffectKind.SPATIAL -> spatial
    }

    companion object {
        fun read(context: Context): RadioAudioSettings {
            val prefs = context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE)
            fun flag(key: String, fallback: Boolean) = runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
            return RadioAudioSettings(flag("enabled", false), flag("eq", true), flag("bass", false), flag("spatial", false))
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putBoolean("eq", equalizer)
            .putBoolean("bass", bass).putBoolean("spatial", spatial).apply()
    }
}

internal enum class RadioAudioEffectKind { EQUALIZER, BASS, SPATIAL }
internal enum class RadioAudioEffectState { WAITING, UNAVAILABLE, READY, ACTIVE, FAILED }

/** Unit gain when bypassed. Keep announcement ducking independent from the effects margin. */
internal fun radioMusicVolume(effectsRequested: Boolean, announcementStarted: Boolean): Float =
    ((if (effectsRequested) 0.5f else 1f) * (if (announcementStarted) 0.4f else 1f)).coerceIn(0f, 1f)

/** Disabling must finish before removing the existing gain margin. */
internal fun radioAudioNeedsMarginBeforeChange(requested: Boolean, currentlyActive: Boolean) = requested || currentlyActive
