package net.bananemdnsa.historystages.api.diagnostics;

/**
 * A category of runtime log lines, such as {@code Relic Lock}. Keep one as a constant and pass it
 * to {@link RuntimeLog}:
 *
 * <pre>{@code
 * static final RuntimeCategory RELIC_LOCK = RuntimeCategory.of("mymod:relic_lock", "Relic Lock", Kind.LOCK);
 * }</pre>
 *
 * <p>No registration: a log line is not something the game needs to know about in advance.
 * Two categories with the same id are the same category.
 */
public final class RuntimeCategory {

    private final String id;
    private final String label;
    private final Kind kind;

    private RuntimeCategory(String id, String label, Kind kind) {
        this.id = id;
        this.label = label;
        this.kind = kind;
    }

    /**
     * @param id    namespaced, {@code <modid>:<name>}
     * @param label what the log shows in brackets
     */
    public static RuntimeCategory of(String id, String label, Kind kind) {
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("A runtime category id must be namespaced, like 'mymod:relic_lock': " + id);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Runtime category '" + id + "' needs a label.");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Runtime category '" + id + "' needs a kind.");
        }
        return new RuntimeCategory(id, label, kind);
    }

    public String id() { return id; }
    public String label() { return label; }
    public Kind kind() { return kind; }

    @Override
    public boolean equals(Object o) {
        return o instanceof RuntimeCategory other && other.id.equals(id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return id; }
}
