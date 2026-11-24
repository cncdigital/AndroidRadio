package mx.sntss1puebla.credenciales;

/** On-device EQ choices. Compute a single target curve per music session, never combine presets. */
final class RadioManualEq {
    static final int MIN_DB = -3;
    static final int MAX_DB = 3;
    static final int BANDS = 5;
    static final Curve FLAT = new Curve(0, 0, 0, 0, 0);

    enum Mode {
        SOFT("soft"), INTELLIGENT("intelligent"), MANUAL("manual");
        final String key;
        Mode(String key) { this.key = key; }
    }

    static Mode readMode(String stored, boolean legacyIntelligent) {
        for (Mode mode : Mode.values()) if (mode.key.equals(stored)) return mode;
        return legacyIntelligent ? Mode.INTELLIGENT : Mode.SOFT;
    }

    record Curve(int low, int lowMid, int mid, int highMid, int high) {
        Curve {
            low = safeDb(low); lowMid = safeDb(lowMid); mid = safeDb(mid);
            highMid = safeDb(highMid); high = safeDb(high);
        }

        int db(int index) {
            return switch (index) {
                case 0 -> low; case 1 -> lowMid; case 2 -> mid; case 3 -> highMid; case 4 -> high;
                default -> throw new IndexOutOfBoundsException();
            };
        }

        Curve with(int index, int db) {
            return switch (index) {
                case 0 -> new Curve(db, lowMid, mid, highMid, high);
                case 1 -> new Curve(low, db, mid, highMid, high);
                case 2 -> new Curve(low, lowMid, db, highMid, high);
                case 3 -> new Curve(low, lowMid, mid, db, high);
                case 4 -> new Curve(low, lowMid, mid, highMid, db);
                default -> throw new IndexOutOfBoundsException();
            };
        }
    }

    record Tuning(RadioIntelligentEq.Profile profile, Curve custom) {
        int gain(int hz, int hardwareMin, int hardwareMax) {
            if (custom == null) return RadioIntelligentEq.gain(profile, hz, hardwareMin, hardwareMax);
            if (hardwareMin > 0 || hardwareMax < 0 || hz <= 0) return 0;
            return Math.max(hardwareMin, Math.min(hardwareMax, 100 * custom.db(RadioIntelligentEq.bandIndex(hz))));
        }
    }

    static Tuning tuning(Mode mode, Curve custom, String genre, boolean karaoke) {
        if (mode == Mode.MANUAL) return new Tuning(RadioIntelligentEq.Profile.FLAT, custom);
        if (mode == Mode.INTELLIGENT && !karaoke) {
            var recommended = RadioIntelligentEq.forGenre(genre);
            if (recommended != RadioIntelligentEq.Profile.FLAT) return new Tuning(recommended, null);
        }
        return new Tuning(RadioIntelligentEq.Profile.MANUAL, null); // Existing soft preset (also during karaoke if intelligent was selected).
    }

    private static int safeDb(int db) { return Math.max(MIN_DB, Math.min(MAX_DB, db)); }
    private RadioManualEq() {}
}
