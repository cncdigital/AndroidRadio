package mx.sntss1puebla.credenciales;

import java.util.regex.Pattern;

/** Editorially verified links only. Never accepts URLs from catalog or opens an inline player. */
final class RadioYoutubePilot {
    static final String PAUSE_COMMAND = "radio.youtube.pause_for_verified_video";
    static final String SONG_ARG = "songId";
    static final String VIDEO_ARG = "videoId";
    static final String VERSION_ARG = "videoVersion";
    static final String CATALOG_VIDEO_ID = "youtubeVideoId";
    static final String CATALOG_VIDEO_VERSION = "youtubeVideoVersion";
    static final String CATALOG_DISABLED = "youtubeVideoDisabled";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern SAFE_SONG = Pattern.compile("song:[1-9][0-9]{0,9}");
    private static final Video RAYMIX = new Video("song:75", "Oye Mujer", "Raymix", "qqqMyxv5al4", false);
    private static final Video JOSE_JOSE = new Video("song:384", "Voy a Llenarte Toda", "José José", "m89KMftUVjI", true);

    record Video(String mediaId, String title, String artist, String youtubeId, boolean liveVersion) {
        String watchUrl() { return "https://www.youtube.com/watch?v=" + youtubeId; }
        String version() { return liveVersion ? "live" : "official"; }
        boolean matchesLaunch(String requestedSong, String requestedVideo, String requestedVersion) {
            return mediaId.equals(requestedSong) && youtubeId.equals(requestedVideo) && version().equals(requestedVersion);
        }
    }

    static Video verified(String mediaId, String title, String artist) {
        return verified(mediaId, title, artist, null, null, false);
    }

    static Video verified(String mediaId, String title, String artist, String videoId, String version, boolean disabled) {
        if (disabled || mediaId == null || !SAFE_SONG.matcher(mediaId).matches()) return null;
        boolean live = "live".equals(version);
        if (videoId != null && SAFE_ID.matcher(videoId).matches() && (live || "official".equals(version)) &&
            title != null && !title.trim().isEmpty() && artist != null && !artist.trim().isEmpty()) {
            return new Video(mediaId, title, artist, videoId, live);
        }
        for (Video video : new Video[] {RAYMIX, JOSE_JOSE}) {
            if (video.mediaId().equals(mediaId) && video.title().equals(title) && video.artist().equals(artist)) return video;
        }
        return null;
    }

    static boolean allowsLaunch(Video queued, Video latestReview, String song, String id, String version) {
        return queued != null && queued.equals(latestReview) && queued.matchesLaunch(song, id, version);
    }

    /** The appended option is transient. Invalid/stale positions fall back to the cover. */
    static int shiftedVisualIndex(int index, int direction, int normalCount, boolean hasVideo) {
        if (normalCount <= 0) return 0;
        int count = normalCount + (hasVideo ? 1 : 0);
        int base = index >= 0 && index < count ? index : 0;
        return Math.floorMod(base + Integer.signum(direction), count);
    }
}
