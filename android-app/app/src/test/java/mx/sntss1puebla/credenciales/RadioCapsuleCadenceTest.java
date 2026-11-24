package mx.sntss1puebla.credenciales;

import org.junit.Test;
import static org.junit.Assert.*;
import static mx.sntss1puebla.credenciales.RadioCapsuleCadence.Slot.*;

public class RadioCapsuleCadenceTest {
    @Test public void reducedModeRegularEveryFiveFactsEveryNineAndOneSlotPerCollision() {
        RadioCapsuleCadence cadence = new RadioCapsuleCadence();
        cadence.enterSong("song:0", false, false);
        assertNull(cadence.take("song:0", false));
        for (int i = 1; i <= 90; i++) {
            String track = "song:" + i;
            cadence.enterSong(track, true, false);
            RadioCapsuleCadence.Slot expected = i % 45 == 0 ? FACT_OR_REGULAR : i % 9 == 0 ? FACT : i % 5 == 0 ? REGULAR : null;
            assertEquals(expected, cadence.take(track, false));
            assertNull(cadence.take(track, false));
        }
    }

    @Test public void frequentModeStartsOnFirstSongSupportsManualSelectionAndDoesNotCountManualSkips() {
        RadioCapsuleCadence cadence = new RadioCapsuleCadence();
        for (int i = 0; i < 10; i++) {
            cadence.enterSong("song:" + i, false, false);
            assertEquals(REGULAR, cadence.take("song:" + i, true));
            assertNull(cadence.take("song:" + i, true));
        }
        for (int i = 1; i <= 18; i++) {
            cadence.enterSong("song:1", true, false); // includes single-song repeat
            assertEquals(i % 9 == 0 ? FACT_OR_REGULAR : REGULAR, cadence.take("song:1", true));
        }
    }

    @Test public void collisionWithCommercialAndSwitchConsumesOnlyOneSlotAfterIt() {
        RadioCapsuleCadence cadence = new RadioCapsuleCadence();
        for (int i = 1; i <= 9; i++) {
            cadence.enterSong("song:1", true, false);
            if (i < 9) cadence.take("song:1", true);
        }
        assertNull(cadence.take("commercial:7", true));
        cadence.enterSong("song:1", false, true);
        assertEquals(FACT, cadence.take("song:1", false));
        assertNull(cadence.take("song:1", false));
        cadence.enterSong("song:2", true, false);
        assertNull(cadence.take("song:1", false));
        assertEquals(REGULAR, cadence.take("song:2", false));
    }
}
