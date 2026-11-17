package mx.sntss1puebla.credenciales

import android.widget.ImageButton

/** Uses actual playback state, never the last tapped action. */
internal object RadioControlIcons {
    fun renderPlayback(button: ImageButton, playing: Boolean) {
        button.setImageResource(if (playing) R.drawable.ic_control_pause else R.drawable.ic_control_play)
        val action = button.context.getString(if (playing) R.string.radio_paused else R.string.play_radio_car)
        button.contentDescription = action
        button.tooltipText = action
    }
}
