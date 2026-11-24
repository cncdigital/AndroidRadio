package mx.sntss1puebla.credenciales;

import static org.junit.Assert.*;
import static mx.sntss1puebla.credenciales.RadioIntelligentEq.Profile.*;
import org.junit.Test;

public class RadioIntelligentEqTest {
    @Test public void selectsOnlyKnownNormalizedCatalogLabels() {
        assertEquals(CLASSICAL, RadioIntelligentEq.forGenre("  Música Clásica "));
        assertEquals(ROCK, RadioIntelligentEq.forGenre("Rock en ESPAÑOL; Pop"));
        assertEquals(LATIN, RadioIntelligentEq.forGenre("Sin género / Reggaetón"));
        assertEquals(ACOUSTIC, RadioIntelligentEq.forGenre("Balada_romántica"));
        assertEquals(FLAT, RadioIntelligentEq.forGenre("No es Rock"));
        assertEquals(FLAT, RadioIntelligentEq.forGenre(null));
        assertEquals(FLAT, RadioIntelligentEq.forGenre("x".repeat(200)));
    }

    @Test public void automaticProfilesNeverBoostAndRespectHardwareRange() {
        for (var p : RadioIntelligentEq.Profile.values()) {
            if (p == MANUAL) continue;
            for (int hz : new int[] { 30, 179, 180, 499, 500, 1000, 2500, 2501, 5000, 5001, 16000 }) {
                int gain = RadioIntelligentEq.gain(p, hz, -1000, 1000);
                assertTrue(gain >= -150 && gain <= 0);
                assertTrue(RadioIntelligentEq.gain(p, hz, -40, 10) >= -40);
                assertEquals(0, RadioIntelligentEq.gain(p, hz, 2, 8));
            }
        }
        assertEquals(200, RadioIntelligentEq.gain(MANUAL, 140, -800, 800));
        assertEquals(-100, RadioIntelligentEq.gain(MANUAL, 1000, -800, 800));
        assertEquals(100, RadioIntelligentEq.gain(MANUAL, 6500, -800, 800));
        assertEquals(50, RadioIntelligentEq.gain(MANUAL, 6500, -30, 50));
    }

    @Test public void profileChangesMoveGraduallyInEitherDirectionWithoutOvershoot() {
        int current = 200;
        int steps = 0;
        while (current != -125 && steps++ < 12) {
            int next = RadioIntelligentEq.nextGain(current, -125);
            assertTrue(next < current && current - next <= 40 && next >= -125);
            current = next;
        }
        assertEquals(-125, current);
        assertEquals(-85, RadioIntelligentEq.nextGain(current, 100));
        assertEquals(-15, RadioIntelligentEq.nextGain(-5, -15));
    }
}
