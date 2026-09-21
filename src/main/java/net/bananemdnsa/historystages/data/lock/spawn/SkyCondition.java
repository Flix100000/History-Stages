package net.bananemdnsa.historystages.data.lock.spawn;

public enum SkyCondition {
    VISIBLE("visible"),
    HIDDEN("hidden");

    private final String serialized;

    SkyCondition(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    public boolean visible() {
        return this == VISIBLE;
    }

    public static SkyCondition parse(String raw) {
        for (SkyCondition value : values()) {
            if (value.serialized.equals(raw)) return value;
        }
        return null;
    }
}
