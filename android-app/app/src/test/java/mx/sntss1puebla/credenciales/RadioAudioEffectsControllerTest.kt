package mx.sntss1puebla.credenciales

import org.junit.Assert.*
import org.junit.Test

class RadioAudioEffectsControllerTest {
    private class Engine : RadioAudioEffectEngine {
        var active = false
        var releases = 0
        var failEnable = false
        var effective = true
        override fun isEffective() = effective
        var lost: (() -> Unit)? = null
        override fun setEnabled(enabled: Boolean) {
            if (failEnable && enabled) throw IllegalStateException("unsupported output")
            active = enabled
        }
        override fun release() { releases++; active = false }
        override fun onControlLost(listener: () -> Unit) { lost = listener }
    }
    private class Factory : RadioAudioEffectFactory {
        val sessions = mutableListOf<Int>()
        val engines = mutableMapOf<RadioAudioEffectKind, Engine>()
        var unsupported: RadioAudioEffectKind? = null
        var failing: RadioAudioEffectKind? = null
        override fun create(kind: RadioAudioEffectKind, sessionId: Int): RadioAudioEffectEngine? {
            sessions.add(sessionId)
            if (kind == unsupported) return null
            if (kind == failing) throw IllegalStateException("vendor failure")
            return Engine().also { engines[kind] = it }
        }
    }
    @Test fun defaultsAreBypassAndNeverAmplify() {
        val defaults = RadioAudioSettings()
        assertFalse(defaults.enabled)
        RadioAudioEffectKind.entries.forEach { assertFalse(defaults.requested(it)) }
        assertEquals(1f, radioMusicVolume(false, false), 0f)
        assertEquals(0.4f, radioMusicVolume(false, true), 0f)
        assertEquals(0.5f, radioMusicVolume(true, false), 0f)
        assertEquals(0.2f, radioMusicVolume(true, true), 0f)
    }
    @Test fun neverAttachesToGlobalOrInvalidSession() {
        val factory = Factory()
        val effects = RadioAudioEffectsController(factory)
        effects.bind(0); effects.bind(-1)
        assertTrue(factory.sessions.isEmpty())
        assertEquals(RadioAudioEffectState.WAITING, effects.state(RadioAudioEffectKind.BASS))
    }
    @Test fun sameSessionDoesNotDuplicateAndNewSessionReleasesOldEngines() {
        val factory = Factory()
        val effects = RadioAudioEffectsController(factory)
        effects.bind(12)
        val old = factory.engines.values.toList()
        effects.bind(12)
        assertEquals(3, factory.sessions.size)
        effects.bind(13)
        assertEquals(6, factory.sessions.size)
        old.forEach { assertEquals(1, it.releases) }
        assertEquals(listOf(12, 12, 12, 13, 13, 13), factory.sessions)
    }
    @Test fun forcedRouteRefreshReprobesAndReleaseIsIdempotent() {
        val factory = Factory()
        val effects = RadioAudioEffectsController(factory)
        effects.bind(12)
        val old = factory.engines.values.toList()
        effects.bind(12, force = true)
        old.forEach { assertEquals(1, it.releases) }
        val current = factory.engines.values.toList()
        effects.release(); effects.release()
        current.forEach { assertEquals(1, it.releases) }
        assertFalse(effects.hasActiveEffect())
    }
    @Test fun unsupportedOrThrowingFeatureDoesNotDisableOtherFeatures() {
        val factory = Factory().apply { unsupported = RadioAudioEffectKind.SPATIAL; failing = RadioAudioEffectKind.BASS }
        val effects = RadioAudioEffectsController(factory)
        effects.bind(11)
        effects.apply(RadioAudioSettings(enabled = true, bass = true, spatial = true), true, false)
        assertEquals(RadioAudioEffectState.UNAVAILABLE, effects.state(RadioAudioEffectKind.SPATIAL))
        assertEquals(RadioAudioEffectState.FAILED, effects.state(RadioAudioEffectKind.BASS))
        assertEquals(RadioAudioEffectState.ACTIVE, effects.state(RadioAudioEffectKind.EQUALIZER))
    }
    @Test fun activationFailureReleasesOnlyFailedFeatureAndDoesNotRetryInLoop() {
        val factory = Factory()
        val effects = RadioAudioEffectsController(factory)
        effects.bind(11)
        val bass = factory.engines.getValue(RadioAudioEffectKind.BASS).apply { failEnable = true }
        repeat(3) { effects.apply(RadioAudioSettings(enabled = true, bass = true), true, false) }
        assertEquals(1, bass.releases)
        assertEquals(RadioAudioEffectState.FAILED, effects.state(RadioAudioEffectKind.BASS))
        assertTrue(effects.hasActiveEffect())
        assertEquals(3, factory.sessions.size)
    }
    @Test fun karaokeSuspendsOnlySpatialAndCommercialsBypassEverything() {
        val factory = Factory()
        val effects = RadioAudioEffectsController(factory)
        val settings = RadioAudioSettings(enabled = true, bass = true, spatial = true)
        effects.bind(11)
        effects.apply(settings, true, true)
        assertTrue(factory.engines.getValue(RadioAudioEffectKind.EQUALIZER).active)
        assertTrue(factory.engines.getValue(RadioAudioEffectKind.BASS).active)
        assertFalse(factory.engines.getValue(RadioAudioEffectKind.SPATIAL).active)
        effects.apply(settings, false, false)
        assertFalse(effects.hasActiveEffect())
        effects.apply(settings, true, false)
        assertTrue(factory.engines.values.all { it.active })
        effects.apply(settings.copy(enabled = false), true, false)
        assertFalse(effects.hasActiveEffect())
    }
    @Test fun lostControlFailsSafelyAndLateCallbacksCannotReleaseReplacement() {
        val factory = Factory()
        var notifications = 0
        val effects = RadioAudioEffectsController(factory) { notifications++ }
        effects.bind(11)
        val old = factory.engines.getValue(RadioAudioEffectKind.EQUALIZER)
        old.lost!!.invoke()
        assertEquals(1, old.releases)
        assertEquals(1, notifications)
        assertEquals(RadioAudioEffectState.FAILED, effects.state(RadioAudioEffectKind.EQUALIZER))
        effects.bind(12)
        old.lost!!.invoke()
        assertEquals(0, factory.engines.getValue(RadioAudioEffectKind.EQUALIZER).releases)
        assertEquals(1, notifications)
    }
    @Test fun disablingRetainsMarginUntilEffectsHaveBeenDisabled() {
        assertTrue(radioAudioNeedsMarginBeforeChange(false, true))
        assertTrue(radioAudioNeedsMarginBeforeChange(true, false))
        assertTrue(radioAudioNeedsMarginBeforeChange(true, true))
        assertFalse(radioAudioNeedsMarginBeforeChange(false, false))
    }
    @Test fun sameSessionRouteLossClearsActiveStateAndExplicitRecheckRecovers() {
        val factory = Factory()
        var changes = 0
        val effects = RadioAudioEffectsController(factory) { changes++ }
        val settings = RadioAudioSettings(enabled = true, bass = true, spatial = true)
        effects.bind(11)
        effects.apply(settings, true, false)
        val old = factory.engines.getValue(RadioAudioEffectKind.SPATIAL)
        old.effective = false
        effects.validateActiveEffects()
        assertEquals(RadioAudioEffectState.FAILED, effects.state(RadioAudioEffectKind.SPATIAL))
        assertEquals(1, old.releases)
        assertEquals(1, changes)
        assertEquals(RadioAudioEffectState.ACTIVE, effects.state(RadioAudioEffectKind.BASS))
        repeat(3) { effects.validateActiveEffects() }
        assertEquals(3, factory.sessions.size) // Never recreate failed engines on each tick.
        effects.bind(11, force = true)
        effects.apply(settings, true, false)
        assertEquals(RadioAudioEffectState.ACTIVE, effects.state(RadioAudioEffectKind.SPATIAL))
        assertNotSame(old, factory.engines.getValue(RadioAudioEffectKind.SPATIAL))
    }

}
