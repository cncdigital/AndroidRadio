package mx.sntss1puebla.credenciales;

import java.util.LinkedList;

/** Listener-local only; these public identifiers rotate clips without collecting listener data. */
final class RadioCapsuleHistory {
    private static final int LIMIT = 12;
    private final LinkedList<Entry> aired = new LinkedList<>();

    record Entry(String id, String template) {
        static Entry fromHeaders(String id, String template) {
            return id != null && id.matches("[1-9][0-9]{0,9}") &&
                template != null && template.matches("[a-z0-9]{1,10}") ? new Entry(id, template) : null;
        }
    }

    void recordAudible(Entry entry) {
        aired.removeIf(prior -> prior.id().equals(entry.id()));
        aired.addFirst(entry);
        if (aired.size() > LIMIT) aired.removeLast();
    }

    boolean repeatsImmediate(Entry entry) {
        return !aired.isEmpty() && (aired.getFirst().id().equals(entry.id()) ||
            aired.getFirst().template().equals(entry.template()));
    }

    String recentIds() { return String.join(",", aired.stream().map(Entry::id).toArray(String[]::new)); }
    String recentTemplates() { return String.join(",", aired.stream().map(Entry::template).toArray(String[]::new)); }
}
