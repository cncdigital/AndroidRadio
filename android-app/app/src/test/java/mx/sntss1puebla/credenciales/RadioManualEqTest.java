package mx.sntss1puebla.credenciales;

import org.junit.Test;
import static org.junit.Assert.*;
import static mx.sntss1puebla.credenciales.RadioManualEq.Mode.*;

public class RadioManualEqTest {
    @Test public void legacyProfileSurvivesMigrationAndInvalidNewPreference() {
        assertEquals(INTELLIGENT, RadioManualEq.readMode(null, true));
        assertEquals(SOFT, RadioManualEq.readMode(null, false));
        assertEquals(INTELLIGENT, RadioManualEq.readMode("not_a_mode", true));
        assertEquals(MANUAL, RadioManualEq.readMode("manual", true));
        assertEquals(SOFT, RadioManualEq.readMode("soft", true));
    }

    @Test public void singleModeNeverStacksAndKaraokeKeepsLegacyIntelligentFallback() {
        var manual = new RadioManualEq.Curve(3, -3, 0, 1, 0);
        assertEquals(300, RadioManualEq.tuning(MANUAL, manual, "Rock", false).gain(120, -1500, 1500));
        assertEquals(-40, RadioManualEq.tuning(INTELLIGENT, manual, "Rock", false).gain(120, -1500, 1500));
        assertNull(RadioManualEq.tuning(INTELLIGENT, manual, "Rock", false).custom());
        assertEquals(200, RadioManualEq.tuning(INTELLIGENT, manual, "Rock", true).gain(120, -1500, 1500));
        assertEquals(200, RadioManualEq.tuning(INTELLIGENT, manual, "unknown", false).gain(120, -1500, 1500));
        assertEquals(200, RadioManualEq.tuning(SOFT, manual, "Rock", false).gain(120, -1500, 1500));
        assertEquals(300, RadioManualEq.tuning(MANUAL, manual, "Rock", true).gain(120, -1500, 1500));
        assertEquals(0, RadioManualEq.tuning(MANUAL, RadioManualEq.FLAT, "Rock", false).gain(120, -1500, 1500));
    }

    @Test public void everyBandMapsByHardwareCenterAndBoundsEvenCorruptIntegers() {
        var curve = new RadioManualEq.Curve(Integer.MAX_VALUE, Integer.MIN_VALUE, 1, -2, 2);
        var tuning = RadioManualEq.tuning(MANUAL, curve, null, false);
        assertEquals(3, curve.db(0));
        assertEquals(-3, curve.db(1));
        assertEquals(100, tuning.gain(179, -100, 100));
        assertEquals(-100, tuning.gain(180, -100, 100));
        assertEquals(100, tuning.gain(500, -1000, 1000));
        assertEquals(100, tuning.gain(2500, -1000, 1000));
        assertEquals(-200, tuning.gain(2501, -1000, 1000));
        assertEquals(200, tuning.gain(5001, -1000, 1000));
        assertEquals(0, tuning.gain(-1, -1000, 1000));
        assertEquals(0, tuning.gain(120, 20, 100));
        assertEquals(0, tuning.gain(120, -100, -20));
        assertEquals(3, curve.with(2, Integer.MAX_VALUE).db(2));
        assertEquals(1, curve.db(2));
    }
}
