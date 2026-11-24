package mx.sntss1puebla.credenciales

import android.content.Context

/** Modest fixed profiles; no loudness gain or claim of clipping prevention. */
internal data class RadioAudioSettings(
    val enabled: Boolean = false,
    val equalizer: Boolean = true,
    val bass: Boolean = false,
    val spatial: Boolean = false,
    val eqMode: RadioManualEq.Mode = RadioManualEq.Mode.SOFT,
    val manualCurve: RadioManualEq.Curve = RadioManualEq.FLAT,
) {
    fun requested(kind: RadioAudioEffectKind): Boolean = enabled && when (kind) {
        RadioAudioEffectKind.EQUALIZER -> equalizer
        RadioAudioEffectKind.BASS -> bass
        RadioAudioEffectKind.SPATIAL -> spatial
    }

    fun equalizerTuning(genre: String?, karaoke: Boolean): RadioManualEq.Tuning =
        RadioManualEq.tuning(eqMode, manualCurve, genre, karaoke)

    companion object {
        fun read(context: Context): RadioAudioSettings {
            val prefs = context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE)
            fun flag(key: String, fallback: Boolean) = runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
            fun band(index: Int) = runCatching { prefs.getInt("eq_band_$index", 0) }.getOrDefault(0)
            val mode = RadioManualEq.readMode(runCatching { prefs.getString("eq_mode", null) }.getOrNull(), flag("intelligent_eq", false))
            return RadioAudioSettings(flag("enabled", false), flag("eq", true), flag("bass", false), flag("spatial", false),
                mode, RadioManualEq.Curve(band(0), band(1), band(2), band(3), band(4)))
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putBoolean("eq", equalizer)
            .putBoolean("bass", bass).putBoolean("spatial", spatial)
            .putString("eq_mode", eqMode.key)
            .putBoolean("intelligent_eq", eqMode == RadioManualEq.Mode.INTELLIGENT)
            .also { edit -> repeat(RadioManualEq.BANDS) { edit.putInt("eq_band_$it", manualCurve.db(it)) } }.apply()
    }
}

internal enum class RadioAudioEffectKind { EQUALIZER, BASS, SPATIAL }
internal enum class RadioAudioEffectState { WAITING, UNAVAILABLE, READY, ACTIVE, FAILED }

/** Unit gain when bypassed. Keep announcement ducking independent from the effects margin. */
internal fun radioMusicVolume(effectsRequested: Boolean, announcementStarted: Boolean): Float =
    ((if (effectsRequested) 0.5f else 1f) * (if (announcementStarted) 0.25f else 1f)).coerceIn(0f, 1f)

/** Disabling must finish before removing the existing gain margin. */
internal fun radioAudioNeedsMarginBeforeChange(requested: Boolean, currentlyActive: Boolean) = requested || currentlyActive
