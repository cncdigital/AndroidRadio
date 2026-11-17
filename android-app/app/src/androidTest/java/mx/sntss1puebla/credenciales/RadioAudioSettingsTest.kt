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
            val enabled = RadioAudioSettings(enabled = true, equalizer = false, bass = true, spatial = true)
            enabled.save(context)
            assertEquals(enabled, RadioAudioSettings.read(context))
            prefs.edit().putString("enabled", "invalid").commit()
            assertFalse(RadioAudioSettings.read(context).enabled)
            RadioAudioSettings().save(context)
            assertEquals(RadioAudioSettings(), RadioAudioSettings.read(context))
        } finally { original.save(context) }
    }
}
