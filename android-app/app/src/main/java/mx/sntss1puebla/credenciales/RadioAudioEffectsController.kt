package mx.sntss1puebla.credenciales

import android.media.AudioFormat
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/** Small injectable boundary: platform engines can be absent or fail independently. */
internal interface RadioAudioEffectEngine {
    fun setEnabled(enabled: Boolean)
    fun release()
    fun isEffective(): Boolean = true
    fun onControlLost(listener: () -> Unit) = Unit
}
internal fun interface RadioAudioEffectFactory {
    fun create(kind: RadioAudioEffectKind, sessionId: Int): RadioAudioEffectEngine?
}

internal class RadioAudioEffectsController(
    private val factory: RadioAudioEffectFactory = AndroidRadioAudioEffectFactory,
    private val onChanged: () -> Unit = {},
) {
    private val engines = mutableMapOf<RadioAudioEffectKind, RadioAudioEffectEngine>()
    private val states = RadioAudioEffectKind.entries.associateWith { RadioAudioEffectState.WAITING }.toMutableMap()
    private var sessionId = 0
    fun state(kind: RadioAudioEffectKind) = states.getValue(kind)
    fun hasActiveEffect() = states.values.any { it == RadioAudioEffectState.ACTIVE }

    fun bind(id: Int, force: Boolean = false) {
        if (!force && id == sessionId) return
        release()
        if (id <= 0) return // Never attach to the device-wide output mix.
        sessionId = id
        RadioAudioEffectKind.entries.forEach { kind ->
            try {
                val engine = factory.create(kind, id)
                if (engine == null) states[kind] = RadioAudioEffectState.UNAVAILABLE
                else {
                    engines[kind] = engine
                    engine.onControlLost {
                        if (engines[kind] === engine) {
                            fail(kind)
                            onChanged()
                        }
                    }
                    engine.setEnabled(false)
                    states[kind] = RadioAudioEffectState.READY
                }
            } catch (_: RuntimeException) { fail(kind) }
        }
    }

    fun apply(settings: RadioAudioSettings, isSong: Boolean, karaoke: Boolean) {
        RadioAudioEffectKind.entries.forEach { kind ->
            val engine = engines[kind] ?: return@forEach
            val enabled = settings.requested(kind) && isSong && !(karaoke && kind == RadioAudioEffectKind.SPATIAL)
            try {
                engine.setEnabled(enabled)
                states[kind] = if (enabled) RadioAudioEffectState.ACTIVE else RadioAudioEffectState.READY
            } catch (_: RuntimeException) { fail(kind) }
        }
    }

    /** Output routing can change without a new session or an added/removed device. */
    fun validateActiveEffects() {
        RadioAudioEffectKind.entries.forEach { kind ->
            if (states[kind] != RadioAudioEffectState.ACTIVE) return@forEach
            val engine = engines[kind] ?: return@forEach
            if (!runCatching { engine.isEffective() }.getOrDefault(false)) {
                fail(kind)
                onChanged()
            }
        }
    }

    private fun fail(kind: RadioAudioEffectKind) {
        engines.remove(kind)?.let { runCatching { it.release() } }
        states[kind] = RadioAudioEffectState.FAILED
    }

    fun release() {
        val previous = engines.values.toList()
        engines.clear()
        previous.forEach { runCatching { it.release() } }
        sessionId = 0
        RadioAudioEffectKind.entries.forEach { states[it] = RadioAudioEffectState.WAITING }
    }
}

@Suppress("DEPRECATION") // Virtualizer is optional; do not change device-wide spatializer settings.
private object AndroidRadioAudioEffectFactory : RadioAudioEffectFactory {
    override fun create(kind: RadioAudioEffectKind, sessionId: Int): RadioAudioEffectEngine? {
        var effect: AudioEffect? = null
        try {
            effect = when (kind) {
                RadioAudioEffectKind.EQUALIZER -> Equalizer(0, sessionId)
                RadioAudioEffectKind.BASS -> BassBoost(0, sessionId)
                RadioAudioEffectKind.SPATIAL -> Virtualizer(0, sessionId)
            }
            check(effect.hasControl())
            check(effect.setEnabled(false) == AudioEffect.SUCCESS)
            when (val engine = effect) {
                is Equalizer -> {
                    check(engine.numberOfBands > 0)
                    val range = engine.bandLevelRange
                    for (band in 0 until engine.numberOfBands.toInt()) {
                        val frequencyHz = engine.getCenterFreq(band.toShort()) / 1000
                        val level = when {
                            frequencyHz < 180 -> 200
                            frequencyHz in 500..2500 -> -100
                            frequencyHz > 5000 -> 100
                            else -> 0
                        }.coerceIn(range[0].toInt(), range[1].toInt())
                        engine.setBandLevel(band.toShort(), level.toShort())
                    }
                }
                is BassBoost -> {
                    check(engine.strengthSupported)
                    engine.setStrength(150.toShort())
                }
                is Virtualizer -> {
                    check(engine.strengthSupported)
                    check(engine.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO, Virtualizer.VIRTUALIZATION_MODE_BINAURAL))
                    engine.setStrength(150.toShort())
                }
            }
            val instance = effect
            return object : RadioAudioEffectEngine {
                override fun setEnabled(enabled: Boolean) {
                    check(instance.hasControl())
                    check(instance.setEnabled(enabled) == AudioEffect.SUCCESS)
                    check(instance.enabled == enabled)
                    if (enabled && instance is Virtualizer) {
                        // Capability is not proof of activation on the current output route.
                        check(instance.virtualizationMode != Virtualizer.VIRTUALIZATION_MODE_OFF)
                    }
                }
                override fun isEffective(): Boolean = instance.hasControl() && instance.enabled &&
                    (instance !is Virtualizer || instance.virtualizationMode != Virtualizer.VIRTUALIZATION_MODE_OFF)
                override fun onControlLost(listener: () -> Unit) {
                    instance.setControlStatusListener { _, hasControl -> if (!hasControl) listener() }
                }
                override fun release() {
                    instance.setControlStatusListener(null)
                    instance.release()
                }
            }
        } catch (_: RuntimeException) {
            effect?.let { runCatching { it.release() } }
            return null
        }
    }
}
