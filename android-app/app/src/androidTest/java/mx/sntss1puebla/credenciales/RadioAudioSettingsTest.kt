package mx.sntss1puebla.credenciales

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioAudioSettingsTest {
    @Test fun defaultsPersistenceResetAndMalformedValuesRemainSafe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = RadioAudioSettings.read(context)
        val prefs = context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().commit()
            assertFalse(RadioAudioSettings.read(context).enabled)
            val enabled = RadioAudioSettings(enabled = true, equalizer = false, bass = true, spatial = true, eqMode = RadioManualEq.Mode.INTELLIGENT)
            enabled.save(context)
            assertEquals(enabled, RadioAudioSettings.read(context))
            prefs.edit().putString("enabled", "invalid").commit()
            prefs.edit().remove("eq_mode").putString("intelligent_eq", "invalid").commit()
            assertEquals(RadioManualEq.Mode.SOFT, RadioAudioSettings.read(context).eqMode)
            assertFalse(RadioAudioSettings.read(context).enabled)
            RadioAudioSettings().save(context)
            assertEquals(RadioAudioSettings(), RadioAudioSettings.read(context))
        } finally { original.save(context) }
    }

    @Test fun legacyModesMigrateAndSavedManualGainsAreBounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("radio_audio", Context.MODE_PRIVATE)
        val original = RadioAudioSettings.read(context)
        try {
            prefs.edit().clear().putBoolean("enabled", true).putBoolean("intelligent_eq", true).commit()
            val legacy = RadioAudioSettings.read(context)
            assertTrue(legacy.enabled)
            assertEquals(RadioManualEq.Mode.INTELLIGENT, legacy.eqMode)
            assertEquals(RadioManualEq.FLAT, legacy.manualCurve)
            prefs.edit().putString("eq_mode", "manual").putInt("eq_band_0", Int.MAX_VALUE)
                .putInt("eq_band_1", Int.MIN_VALUE).putString("eq_band_2", "crashed").commit()
            val manual = RadioAudioSettings.read(context)
            assertEquals(RadioManualEq.Mode.MANUAL, manual.eqMode)
            assertEquals(RadioManualEq.Curve(3, -3, 0, 0, 0), manual.manualCurve)
            manual.copy(eqMode = RadioManualEq.Mode.INTELLIGENT).save(context)
            val retained = RadioAudioSettings.read(context)
            assertEquals(manual.manualCurve, retained.manualCurve)
            retained.copy(eqMode = RadioManualEq.Mode.MANUAL, manualCurve = RadioManualEq.FLAT).save(context)
            assertEquals(RadioManualEq.Mode.MANUAL, RadioAudioSettings.read(context).eqMode)
            assertEquals(RadioManualEq.FLAT, RadioAudioSettings.read(context).manualCurve)
        } finally { original.save(context) }
    }
}
