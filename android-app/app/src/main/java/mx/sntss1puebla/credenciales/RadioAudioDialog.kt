package mx.sntss1puebla.credenciales

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Phone-only settings. The service owns hardware and remains authoritative. */
internal object RadioAudioDialog {
    fun show(context: Context, sessionExtras: () -> Bundle?): AlertDialog {
        var settings = RadioAudioSettings.read(context)
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val master = CheckBox(context).apply {
            text = "Activar mejoras de audio"
            isChecked = settings.enabled
        }
        content.addView(master)
        content.addView(TextView(context).apply {
            text = "Perfiles suaves y opcionales. Se reduce el nivel para dejar margen a los efectos; no garantiza evitar distorsión. No crea un subwoofer físico ni sonido 5D. La calidad depende de la grabación, la salida y el dispositivo."
        })
        val labels = mapOf(RadioAudioEffectKind.EQUALIZER to "Ecualización suave", RadioAudioEffectKind.BASS to "Refuerzo moderado de graves", RadioAudioEffectKind.SPATIAL to "Amplitud estéreo · auriculares")
        val controls = RadioAudioEffectKind.entries.associateWith { kind ->
            val check = CheckBox(context).apply {
                text = labels.getValue(kind)
                isChecked = when (kind) {
                    RadioAudioEffectKind.EQUALIZER -> settings.equalizer
                    RadioAudioEffectKind.BASS -> settings.bass
                    RadioAudioEffectKind.SPATIAL -> settings.spatial
                }
            }
            val status = TextView(context)
            content.addView(check)
            content.addView(status)
            check to status
        }
        content.addView(TextView(context).apply {
            text = "Reproduce una canción para comprobar disponibilidad. Los efectos se omiten en anuncios y voz. La amplitud estéreo se suspende con karaoke. Si no están disponibles, se conserva el audio normal."
            setPadding(0, padding / 2, 0, 0)
        })
        content.addView(Button(context).apply {
            text = "Comprobar salida"
            setOnClickListener {
                context.startService(Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_RECHECK_AUDIO))
            }
        })
        content.addView(TextView(context).apply {
            text = "Si cambias entre salidas ya conectadas, pulsa Comprobar salida para volver a intentar los efectos disponibles."
        })
        fun save() {
            settings = RadioAudioSettings(master.isChecked,
                controls.getValue(RadioAudioEffectKind.EQUALIZER).first.isChecked,
                controls.getValue(RadioAudioEffectKind.BASS).first.isChecked,
                controls.getValue(RadioAudioEffectKind.SPATIAL).first.isChecked)
            settings.save(context)
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_AUDIO))
        }
        master.setOnCheckedChangeListener { _, _ -> save() }
        controls.values.forEach { (check, _) -> check.setOnCheckedChangeListener { _, _ -> save() } }
        val dialog = AlertDialog.Builder(context).setTitle("Mejoras de audio")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Audio original") { _, _ ->
                RadioAudioSettings().save(context)
                context.startService(Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_AUDIO))
            }.create()
        val handler = Handler(Looper.getMainLooper())
        val update = object : Runnable {
            override fun run() {
                val extras = sessionExtras()
                val karaoke = extras?.getBoolean("radio.karaoke.active", false) == true
                controls.forEach { (kind, pair) ->
                    val state = runCatching { RadioAudioEffectState.valueOf(extras?.getString("radio.audio.${kind.name}") ?: "WAITING") }
                        .getOrDefault(RadioAudioEffectState.WAITING)
                    val available = state == RadioAudioEffectState.READY || state == RadioAudioEffectState.ACTIVE
                    pair.first.isEnabled = master.isChecked && available
                    pair.second.text = when (state) {
                        RadioAudioEffectState.WAITING -> "Pendiente de una sesión de reproducción"
                        RadioAudioEffectState.UNAVAILABLE -> "No disponible con este dispositivo o salida"
                        RadioAudioEffectState.FAILED -> "No se pudo activar; se mantiene el audio normal"
                        RadioAudioEffectState.ACTIVE -> "Activo"
                        RadioAudioEffectState.READY -> if (kind == RadioAudioEffectKind.SPATIAL && karaoke) "Suspendido durante karaoke" else "Disponible · inactivo"
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(update) }
        dialog.show()
        handler.post(update)
        return dialog
    }
}
