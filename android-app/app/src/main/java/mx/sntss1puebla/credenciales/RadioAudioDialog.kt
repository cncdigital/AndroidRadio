package mx.sntss1puebla.credenciales

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.ViewCompat

/** Phone-only settings. The service owns hardware and remains authoritative. */
internal object RadioAudioDialog {
    private val modes = linkedMapOf(RadioManualEq.Mode.SOFT to "Ecualización suave",
        RadioManualEq.Mode.INTELLIGENT to "Ecualizador inteligente", RadioManualEq.Mode.MANUAL to "Ecualizador manual")
    private val bandNames = listOf("Graves · 120 Hz", "Medios bajos · 300 Hz", "Medios · 1 kHz",
        "Medios altos · 4 kHz", "Agudos · 8 kHz")

    fun show(context: Context, sessionExtras: () -> Bundle?): AlertDialog {
        var settings = RadioAudioSettings.read(context)
        val handler = Handler(Looper.getMainLooper())
        var serviceUpdatePending = false
        val serviceUpdate = Runnable {
            serviceUpdatePending = false
            settings.save(context)
            updateService(context)
        }
        fun save(debounce: Boolean = false) {
            handler.removeCallbacks(serviceUpdate)
            if (debounce) {
                serviceUpdatePending = true
                handler.postDelayed(serviceUpdate, 90)
            } else serviceUpdate.run()
        }
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
            text = "Se reduce el nivel de la música para dejar margen a los efectos; no garantiza evitar distorsión. El resultado depende de la grabación y la salida."
        })
        val labels = mapOf(RadioAudioEffectKind.EQUALIZER to "Ecualización",
            RadioAudioEffectKind.BASS to "Refuerzo moderado de graves", RadioAudioEffectKind.SPATIAL to "Amplitud estéreo · auriculares")
        val modeGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val modeButtons = modes.mapValues { (_, label) ->
            RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                minHeight = (48 * resources.displayMetrics.density).toInt()
                modeGroup.addView(this)
            }
        }
        modeGroup.check(modeButtons.getValue(settings.eqMode).id)
        val modeStatus = TextView(context)
        val manual = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = "Ajusta cinco bandas de −3 a +3 dB. Las frecuencias son orientativas y los límites de la salida pueden ser menores." })
        }
        val manualSliders = bandNames.mapIndexed { index, label ->
            val caption = TextView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            val slider = SeekBar(context).apply {
                min = RadioManualEq.MIN_DB
                max = RadioManualEq.MAX_DB
                keyProgressIncrement = 1
                minHeight = (48 * resources.displayMetrics.density).toInt()
                contentDescription = label
                progress = settings.manualCurve.db(index)
            }
            fun updateLabel(value: Int) {
                val visual = when {
                    value < 0 -> "−${-value}"
                    value > 0 -> "+$value"
                    else -> "0"
                }
                caption.text = "$label: $visual dB"
                ViewCompat.setStateDescription(slider, when {
                    value < 0 -> "menos ${-value} decibeles"
                    value > 0 -> "más $value decibeles"
                    else -> "0 decibeles"
                })
            }
            updateLabel(slider.progress)
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    updateLabel(progress)
                    if (fromUser && settings.eqMode == RadioManualEq.Mode.MANUAL) {
                        settings = settings.copy(manualCurve = settings.manualCurve.with(index, progress))
                        save(debounce = true)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
            manual.addView(caption)
            manual.addView(slider)
            slider
        }
        val reset = Button(context).apply {
            text = "Centrar bandas en 0 dB"
            setOnClickListener {
                settings = settings.copy(manualCurve = RadioManualEq.FLAT)
                manualSliders.forEach { it.progress = 0 }
                save()
            }
        }
        manual.addView(reset)
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
            if (kind == RadioAudioEffectKind.EQUALIZER) {
                content.addView(modeGroup)
                content.addView(modeStatus)
                content.addView(manual)
            }
            check to status
        }
        content.addView(TextView(context).apply {
            text = "Los efectos solo se aplican a la música, no a anuncios ni cápsulas de voz. Durante karaoke se suspende la amplitud estéreo y el modo inteligente usa el perfil suave."
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
        fun renderMode(extras: Bundle?) {
            val canConfigure = settings.enabled && settings.equalizer
            modeButtons.values.forEach { it.isEnabled = canConfigure }
            manual.visibility = if (settings.eqMode == RadioManualEq.Mode.MANUAL) View.VISIBLE else View.GONE
            manualSliders.forEach { it.isEnabled = canConfigure }
            reset.isEnabled = canConfigure
            val karaoke = extras?.getBoolean("radio.karaoke.active", false) == true
            val eqState = extras?.getString("radio.audio.EQUALIZER")
            val profile = extras?.getString("radio.audio.eq.profile")
            val nextStatus = when {
                !canConfigure -> "Activa Mejoras de audio y Ecualización para elegir un modo."
                eqState == "UNAVAILABLE" || eqState == "FAILED" -> "No disponible en esta salida. Tus ajustes se conservan."
                settings.eqMode == RadioManualEq.Mode.SOFT -> "Perfil suave fijo."
                settings.eqMode == RadioManualEq.Mode.INTELLIGENT && karaoke -> "Se usa el perfil suave durante karaoke; tus ajustes se conservan."
                settings.eqMode == RadioManualEq.Mode.MANUAL && eqState == "ACTIVE" -> "Ecualización manual activa. No se combina con el modo inteligente."
                settings.eqMode == RadioManualEq.Mode.MANUAL -> "Ajustes guardados. Se aplican cuando esté disponible una pista musical."
                eqState == "ACTIVE" && profile == "Ecualización suave" -> "Sin perfil para este género; se usa la ecualización suave."
                eqState == "ACTIVE" && !profile.isNullOrEmpty() -> "Perfil activo: $profile. Ajuste local según el género."
                else -> "Ajuste local según el género del catálogo, sin usar IA en línea."
            }
            if (modeStatus.text != nextStatus) modeStatus.text = nextStatus
        }
        fun saveAndRefresh() { save(); renderMode(sessionExtras()) }
        modeGroup.setOnCheckedChangeListener { _, id ->
            val mode = modeButtons.entries.firstOrNull { it.value.id == id }?.key ?: return@setOnCheckedChangeListener
            settings = settings.copy(eqMode = mode)
            saveAndRefresh()
        }
        master.setOnCheckedChangeListener { _, checked -> settings = settings.copy(enabled = checked); saveAndRefresh() }
        controls.forEach { (kind, pair) ->
            pair.first.setOnCheckedChangeListener { _, checked ->
                settings = when (kind) {
                    RadioAudioEffectKind.EQUALIZER -> settings.copy(equalizer = checked)
                    RadioAudioEffectKind.BASS -> settings.copy(bass = checked)
                    RadioAudioEffectKind.SPATIAL -> settings.copy(spatial = checked)
                }
                saveAndRefresh()
            }
        }
        val dialog = AlertDialog.Builder(context).setTitle("Mejoras de audio")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Audio original") { _, _ ->
                settings = RadioAudioSettings()
                save()
            }.create()
        val update = object : Runnable {
            override fun run() {
                val extras = sessionExtras()
                renderMode(extras)
                val karaoke = extras?.getBoolean("radio.karaoke.active", false) == true
                controls.forEach { (kind, pair) ->
                    val state = runCatching { RadioAudioEffectState.valueOf(extras?.getString("radio.audio.${kind.name}") ?: "WAITING") }
                        .getOrDefault(RadioAudioEffectState.WAITING)
                    val available = state == RadioAudioEffectState.READY || state == RadioAudioEffectState.ACTIVE
                    // Configure the EQ before playback; the service applies it only if the output supports it.
                    pair.first.isEnabled = settings.enabled && (kind == RadioAudioEffectKind.EQUALIZER || available)
                    val status = when (state) {
                        RadioAudioEffectState.WAITING -> "Pendiente de una sesión de reproducción"
                        RadioAudioEffectState.UNAVAILABLE -> "No disponible con este dispositivo o salida"
                        RadioAudioEffectState.FAILED -> "No se pudo activar; se mantiene el audio normal"
                        RadioAudioEffectState.ACTIVE -> "Activo"
                        RadioAudioEffectState.READY -> if (kind == RadioAudioEffectKind.SPATIAL && karaoke) "Suspendido durante karaoke" else "Disponible · inactivo"
                    }
                    if (pair.second.text != status) pair.second.text = status
                }
                handler.postDelayed(this, 500)
            }
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(update)
            if (serviceUpdatePending) {
                handler.removeCallbacks(serviceUpdate)
                serviceUpdate.run()
            }
        }
        dialog.show()
        handler.post(update)
        return dialog
    }

    private fun updateService(context: Context) {
        try {
            context.startService(Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_AUDIO))
        } catch (_: IllegalStateException) {
            // App may have gone to background during slider debounce. Prefs are already saved.
        }
    }
}
