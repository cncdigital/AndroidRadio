package mx.sntss1puebla.credenciales;

/** Editorially verified pilot. No network search, fuzzy matching, metadata URLs or inline player. */
final class RadioYoutubePilot {
    static final String PAUSE_COMMAND = "radio.youtube.pause_for_verified_video";
    static final String SONG_ARG = "songId";
    private static final Video RAYMIX = new Video("song:75", "Oye Mujer", "Raymix", "qqqMyxv5al4", false);
    private static final Video JOSE_JOSE = new Video("song:384", "Voy a Llenarte Toda", "José José", "m89KMftUVjI", true);

    record Video(String mediaId, String title, String artist, String youtubeId, boolean liveVersion) {
        String watchUrl() { return "https://www.youtube.com/watch?v=" + youtubeId; }
    }

    static Video verified(String mediaId, String title, String artist) {
        for (Video video : new Video[] {RAYMIX, JOSE_JOSE}) {
            if (video.mediaId().equals(mediaId) && video.title().equals(title) && video.artist().equals(artist)) return video;
        }
        return null;
    }

    /** The appended option is transient. Invalid/stale positions fall back to the cover. */
    static int shiftedVisualIndex(int index, int direction, int normalCount, boolean hasVideo) {
        if (normalCount <= 0) return 0;
        int count = normalCount + (hasVideo ? 1 : 0);
        int base = index >= 0 && index < count ? index : 0;
        return Math.floorMod(base + Integer.signum(direction), count);
    }
}
