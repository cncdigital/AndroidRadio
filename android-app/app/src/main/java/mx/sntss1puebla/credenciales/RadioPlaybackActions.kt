package mx.sntss1puebla.credenciales

import androidx.media3.common.Player

internal object RadioPlaybackActions {
    fun resume(player: Player) {
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
    }
}
