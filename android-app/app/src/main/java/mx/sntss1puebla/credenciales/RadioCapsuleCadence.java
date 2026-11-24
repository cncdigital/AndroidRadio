package mx.sntss1puebla.credenciales;

/** One consumable slot per music entry; commercials, pause/resume and stale callbacks do not consume it. */
final class RadioCapsuleCadence {
    enum Slot {
        REGULAR("regular"), FACT("fact"), FACT_OR_REGULAR("fact-or-regular");
        final String query;
        Slot(String query) { this.query = query; }

        boolean accepts(String kind) {
            boolean fact = "fact".equals(kind);
            boolean regular = "song".equals(kind) || "greeting".equals(kind) || "story".equals(kind);
            return this == FACT ? fact : this == REGULAR ? regular : fact || regular;
        }
    }

    private long completedSongs = 0;
    private String pendingTrack;
    private boolean naturalBoundary;

    void enterSong(String track, boolean completedPreviousSong, boolean comingFromCommercial) {
        // The commercial was inserted ahead of this already planned music item.
        if (comingFromCommercial && track.equals(pendingTrack)) return;
        if (completedPreviousSong) completedSongs++;
        pendingTrack = track;
        naturalBoundary = completedPreviousSong;
    }

    Slot take(String track, boolean frequent) {
        if (!track.equals(pendingTrack)) return null;
        pendingTrack = null;
        boolean fact = naturalBoundary && completedSongs % 9 == 0;
        boolean regular = frequent || (naturalBoundary && completedSongs % 5 == 0);
        if (fact) return regular ? Slot.FACT_OR_REGULAR : Slot.FACT;
        return regular ? Slot.REGULAR : null;
    }

    int style() { return (int) (completedSongs % 16); }
}
