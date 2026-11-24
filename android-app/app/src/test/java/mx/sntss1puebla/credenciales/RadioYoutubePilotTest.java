package mx.sntss1puebla.credenciales;

import org.junit.Test;
import static org.junit.Assert.*;

public class RadioYoutubePilotTest {
    @Test public void onlyExactCatalogIdentityCanProduceAnOfficiallyVerifiedWatchUrl() {
        var raymix = RadioYoutubePilot.verified("song:75", "Oye Mujer", "Raymix");
        assertEquals("https://www.youtube.com/watch?v=qqqMyxv5al4", raymix.watchUrl());
        assertFalse(raymix.liveVersion());
        var jose = RadioYoutubePilot.verified("song:384", "Voy a Llenarte Toda", "José José");
        assertEquals("https://www.youtube.com/watch?v=m89KMftUVjI", jose.watchUrl());
        assertTrue(jose.liveVersion());
        assertNull(RadioYoutubePilot.verified("song:76", "Oye Mujer", "Raymix"));
        assertNull(RadioYoutubePilot.verified("song:75", "Oye Mujer", "Otro artista"));
        assertNull(RadioYoutubePilot.verified("song:384", "Otro título", "José José"));
        assertNull(RadioYoutubePilot.verified("commercial:75", "Oye Mujer", "Raymix"));
        assertNull(RadioYoutubePilot.verified(null, null, null));
    }

    @Test public void previousAndNextPreserveOrderAndWrapThroughOnlyEligibleVideo() {
        for (boolean eligible : new boolean[] { false, true }) {
            int last = eligible ? 6 : 5;
            assertEquals(last, RadioYoutubePilot.shiftedVisualIndex(0, -1, 6, eligible));
            assertEquals(0, RadioYoutubePilot.shiftedVisualIndex(last, 1, 6, eligible));
            assertEquals(4, RadioYoutubePilot.shiftedVisualIndex(5, -1, 6, eligible));
            assertEquals(5, RadioYoutubePilot.shiftedVisualIndex(4, 1, 6, eligible));
        }
        assertEquals(1, RadioYoutubePilot.shiftedVisualIndex(6, 1, 6, false));
    }
}
