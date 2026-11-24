package mx.sntss1puebla.credenciales;

import static org.junit.Assert.*;
import org.junit.Test;

public class RadioCapsulePlaybackTest {
    @Test public void onlyAStartedLocalClipDucksMusic() {
        RadioCapsulePlayback capsule = new RadioCapsulePlayback();
        long id = capsule.start();
        assertTrue(capsule.waitingToStart(id));
        assertFalse(capsule.isAudible());
        assertTrue(capsule.markAudible(id));
        assertTrue(capsule.isAudible());
        assertFalse(capsule.waitingToStart(id));
        assertTrue(capsule.stop());
        assertFalse(capsule.isAudible());
        assertFalse(capsule.markAudible(id)); // An interrupted clip cannot resume.
    }

    @Test public void canceledOrSupersededCapsuleCannotBegin() {
        RadioCapsulePlayback capsule = new RadioCapsulePlayback();
        long earlier = capsule.start();
        assertTrue(capsule.stop());
        assertFalse(capsule.stop());
        long later = capsule.start();
        assertNotEquals(later, earlier);
        assertFalse(capsule.waitingToStart(earlier));
        assertFalse(capsule.markAudible(earlier));
        assertTrue(capsule.waitingToStart(later));
        assertEquals(later, capsule.getActiveId());
    }
}
