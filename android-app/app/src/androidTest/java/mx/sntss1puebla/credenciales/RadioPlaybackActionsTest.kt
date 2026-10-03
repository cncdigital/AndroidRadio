package mx.sntss1puebla.credenciales

import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioPlaybackActionsTest {
    @Test fun externalControlsSkipSongsWithoutTenSecondJumps() {
        val commands = RadioPlaybackActions.externalCommands()
        assertFalse(commands.contains(Player.COMMAND_SEEK_BACK))
        assertFalse(commands.contains(Player.COMMAND_SEEK_FORWARD))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }
}
