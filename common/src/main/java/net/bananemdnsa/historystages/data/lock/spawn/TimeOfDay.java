package net.bananemdnsa.historystages.data.lock.spawn;

public enum TimeOfDay {
    DAY("day"),
    NIGHT("night");

    private final String serialized;

    TimeOfDay(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    public static TimeOfDay parse(String raw) {
        for (TimeOfDay value : values()) {
            if (value.serialized.equals(raw)) return value;
        }
        return null;
    }
}
