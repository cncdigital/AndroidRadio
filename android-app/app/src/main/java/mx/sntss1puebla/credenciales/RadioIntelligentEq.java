package mx.sntss1puebla.credenciales;

import java.text.Normalizer;
import java.util.Locale;

/** Conservative genre-based recommendations. Inputs are catalog labels, never inferred from audio or sent off-device. */
final class RadioIntelligentEq {
    enum Profile {
        FLAT("Sin ajuste por género", 0, 0, 0, 0, 0),
        MANUAL("Ecualización suave", 200, 0, -100, 0, 100),
        POP("Pop", -110, -70, 0, -30, -70),
        ROCK("Rock", -40, -130, -35, 0, -120),
        ELECTRONIC("Electrónica", 0, -140, -90, -65, -90),
        LATIN("Ritmos latinos", -90, -110, -30, 0, -95),
        JAZZ("Jazz", -45, -85, 0, -25, -70),
        CLASSICAL("Clásica", -45, -65, 0, -35, -55),
        ACOUSTIC("Acústica y baladas", -125, -75, 0, -45, -65);

        final String label;
        private final int[] gains;
        Profile(String label, int... gains) { this.label = label; this.gains = gains; }
    }

    private static String normalize(String genre) {
        return Normalizer.normalize(genre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .trim().toLowerCase(Locale.ROOT).replaceAll("[_\\s]+", " ");
    }

    static Profile forGenre(String genre) {
        if (genre == null || genre.length() > 160) return Profile.FLAT;
        String value = normalize(genre);
        Profile primary = namedProfile(value);
        if (primary != Profile.FLAT) return primary;
        // Select the first recognized catalog label, not a substring or guess.
        for (String alternative : value.split("[,/;|]+")) {
            Profile profile = namedProfile(alternative.trim());
            if (profile != Profile.FLAT) return profile;
        }
        return Profile.FLAT;
    }

    private static Profile namedProfile(String label) {
        return switch (label) {
            case "pop", "pop latino", "pop en espanol", "pop rock", "synth-pop", "synth pop", "k-pop" -> Profile.POP;
            case "rock", "rock en espanol", "rock alternativo", "rock clasico", "metal", "hard rock", "indie rock", "punk" -> Profile.ROCK;
            case "electronica", "electronic", "edm", "dance", "house", "techno", "trance", "disco", "hip hop", "hip-hop", "rap" -> Profile.ELECTRONIC;
            case "latina", "latino", "musica latina", "latin", "salsa", "cumbia", "bachata", "merengue",
                "reggaeton", "regional mexicano", "banda", "norteno", "ranchera", "tropical" -> Profile.LATIN;
            case "jazz", "blues", "smooth jazz", "swing", "soul", "r&b" -> Profile.JAZZ;
            case "clasica", "musica clasica", "classical", "opera", "barroca", "instrumental clasica" -> Profile.CLASSICAL;
            case "acustica", "acustico", "acoustic", "balada", "baladas", "balada romantica", "folk", "trova", "bolero" -> Profile.ACOUSTIC;
            default -> Profile.FLAT;
        };
    }

    /** Five fixed frequency ranges; use a native session band's own center and clamp to hardware bounds. */
    static int gain(Profile profile, int centerHz, int hardwareMin, int hardwareMax) {
        if (hardwareMin > 0 || hardwareMax < 0 || centerHz <= 0) return 0;
        int gain = profile.gains[bandIndex(centerHz)];
        return Math.max(hardwareMin, Math.min(hardwareMax, gain));
    }

    static int bandIndex(int centerHz) {
        return centerHz < 180 ? 0 : centerHz < 500 ? 1 : centerHz <= 2500 ? 2 : centerHz <= 5000 ? 3 : 4;
    }

    /** 40 mB / 35 ms: ramps through intermediate levels, including when a setting changes mid-ramp. */
    static int nextGain(int current, int target) {
        return current + Math.max(-40, Math.min(40, target - current));
    }
}
