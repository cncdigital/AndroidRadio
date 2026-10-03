package mx.sntss1puebla.credenciales

import androidx.media3.common.Player

internal object RadioPlaybackActions {
    // External controllers should skip songs rather than offer podcast-style 10-second jumps.
    fun externalCommands(): Player.Commands = Player.Commands.Builder()
        .addAllCommands()
        .remove(Player.COMMAND_SEEK_BACK)
        .remove(Player.COMMAND_SEEK_FORWARD)
        .build()

    fun resume(player: Player) {
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
    }
}
