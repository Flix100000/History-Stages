package net.bananemdnsa.historystages.data.lock.spawn;

public enum WeatherCondition {
    CLEAR("clear"),
    /** Includes thunderstorms — a storm is rain with lightning, and nobody expects it to stop counting. */
    RAIN("rain"),
    THUNDER("thunder");

    private final String serialized;

    WeatherCondition(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    public boolean matches(boolean raining, boolean thundering) {
        return switch (this) {
            case CLEAR -> !raining;
            case RAIN -> raining;
            case THUNDER -> thundering;
        };
    }

    public static WeatherCondition parse(String raw) {
        for (WeatherCondition value : values()) {
            if (value.serialized.equals(raw)) return value;
        }
        return null;
    }
}
