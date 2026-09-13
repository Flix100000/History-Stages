package net.bananemdnsa.historystages.data.lock.spawn;

public enum FilterMode {
    ONLY("only"),
    EXCLUDE("exclude");

    private final String serialized;

    FilterMode(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    /** Null for anything unknown, so a hand-written typo drops the filter instead of inverting it. */
    public static FilterMode parse(String raw) {
        for (FilterMode mode : values()) {
            if (mode.serialized.equals(raw)) return mode;
        }
        return null;
    }
}
