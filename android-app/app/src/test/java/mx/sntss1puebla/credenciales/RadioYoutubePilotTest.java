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

    @Test public void currentReviewedCatalogCanAddOfficialAndLabelLiveWithoutAcceptingAnyUrl() {
        var official = RadioYoutubePilot.verified("song:800", "Título", "Artista", "AbCdEf_-012", "official", false);
        assertNotNull(official);
        assertFalse(official.liveVersion());
        assertEquals("https://www.youtube.com/watch?v=AbCdEf_-012", official.watchUrl());
        var live = RadioYoutubePilot.verified("song:801", "Título", "Artista", "123456789_A", "live", false);
        assertNotNull(live);
        assertTrue(live.liveVersion());
        assertEquals("live", live.version());
        for (String invalid : new String[] {"https://youtu.be/AbCdEf_-012", "AbCdEf_-012&x=1",
                "AbCdEf_-012\n", " AbCdEf_-012", "AbCdEf012", "AbCdEf_ñ012", "AbCdEf_-0122"}) {
            assertNull(RadioYoutubePilot.verified("song:800", "Título", "Artista", invalid, "official", false));
        }
        assertNull(RadioYoutubePilot.verified("commercial:800", "Título", "Artista", "AbCdEf_-012", "official", false));
        assertNull(RadioYoutubePilot.verified("song:000", "Título", "Artista", "AbCdEf_-012", "official", false));
        assertNull(RadioYoutubePilot.verified("song:800", "Título", " ", "AbCdEf_-012", "official", false));
        assertNull(RadioYoutubePilot.verified("song:800", "Título", "Artista", "AbCdEf_-012", null, false));
        assertNull(RadioYoutubePilot.verified("song:800", "Título", "Artista", "AbCdEf_-012", "LIVE", false));
    }

    @Test public void reviewedSuppressionOverridesBothFallbackAndRemote() {
        assertNull(RadioYoutubePilot.verified("song:75", "Oye Mujer", "Raymix", "AbCdEf_-012", "official", true));
        assertNull(RadioYoutubePilot.verified("song:384", "Voy a Llenarte Toda", "José José", null, null, true));
        assertEquals("qqqMyxv5al4", RadioYoutubePilot.verified("song:75", "Oye Mujer", "Raymix",
            "https://example.test/irrelevant", "official", false).youtubeId());
    }

    @Test public void launchRequiresSameTrackIdAndVersionEvenIfQueueHasNotYetRefreshed() {
        var queued = RadioYoutubePilot.verified("song:800", "Título", "Artista", "AbCdEf_-012", "official", false);
        var replacement = RadioYoutubePilot.verified("song:800", "Título", "Artista", "123456789_A", "official", false);
        var relabelled = RadioYoutubePilot.verified("song:800", "Título", "Artista", "AbCdEf_-012", "live", false);
        assertTrue(RadioYoutubePilot.allowsLaunch(queued, queued, "song:800", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(null, queued, "song:800", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, null, "song:800", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, replacement, "song:800", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, relabelled, "song:800", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, queued, "song:801", "AbCdEf_-012", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, queued, "song:800", "123456789_A", "official"));
        assertFalse(RadioYoutubePilot.allowsLaunch(queued, queued, "song:800", "AbCdEf_-012", "live"));
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
