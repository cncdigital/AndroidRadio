package mx.sntss1puebla.credenciales;

/** State of one capsule at a time. Called only from the service main thread. */
final class RadioCapsulePlayback {
    private long nextId;
    private long activeId;
    private boolean audible;

    long start() {
        activeId = ++nextId;
        audible = false;
        return activeId;
    }

    long getActiveId() { return activeId; }

    boolean isAudible() { return audible; }

    boolean waitingToStart(long id) { return id != 0 && activeId == id && !audible; }

    boolean markAudible(long id) {
        if (!waitingToStart(id)) return false;
        audible = true;
        return true;
    }

    boolean stop() {
        if (activeId == 0) return false;
        activeId = 0;
        audible = false;
        return true;
    }
}
