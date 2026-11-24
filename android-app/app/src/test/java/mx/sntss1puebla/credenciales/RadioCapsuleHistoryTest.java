package mx.sntss1puebla.credenciales;

import org.junit.Test;
import static org.junit.Assert.*;

public class RadioCapsuleHistoryTest {
    @Test public void stableKeysPassThroughAndOnlyMarkAudibleRecords() {
        var history = new RadioCapsuleHistory();
        var lifecycle = new RadioCapsulePlayback();
        var first = RadioCapsuleHistory.Entry.fromHeaders("414", "0za9");
        assertNotNull(first);
        long canceledId = lifecycle.start();
        lifecycle.stop();
        if (lifecycle.markAudible(canceledId)) history.recordAudible(first);
        assertEquals("", history.recentIds());
        long active = lifecycle.start();
        if (lifecycle.markAudible(active)) history.recordAudible(first);
        assertEquals("414", history.recentIds());
        assertEquals("0za9", history.recentTemplates());
        assertTrue(history.repeatsImmediate(new RadioCapsuleHistory.Entry("415", "0za9")));
        assertTrue(history.repeatsImmediate(new RadioCapsuleHistory.Entry("414", "another")));
        assertFalse(history.repeatsImmediate(new RadioCapsuleHistory.Entry("415", "fresh")));
    }

    @Test public void historyIsBoundedOrderedAndRejectsMalformedKeys() {
        var history = new RadioCapsuleHistory();
        for (int i = 1; i <= 14; i++) history.recordAudible(new RadioCapsuleHistory.Entry("" + i, "key" + i));
        assertEquals("14,13,12,11,10,9,8,7,6,5,4,3", history.recentIds());
        history.recordAudible(new RadioCapsuleHistory.Entry("3", "updated"));
        assertEquals("3,14,13,12,11,10,9,8,7,6,5,4", history.recentIds());
        assertEquals("updated,key14,key13,key12,key11,key10,key9,key8,key7,key6,key5,key4", history.recentTemplates());
        assertNull(RadioCapsuleHistory.Entry.fromHeaders("1,2", "abcd"));
        assertNull(RadioCapsuleHistory.Entry.fromHeaders("5", "ABC"));
        assertNull(RadioCapsuleHistory.Entry.fromHeaders("5", "x".repeat(11)));
    }
}
