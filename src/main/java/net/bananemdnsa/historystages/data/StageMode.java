package net.bananemdnsa.historystages.data;

/**
 * Determines how a stage is unlocked and whether a research scroll is generated.
 *
 * <ul>
 *   <li>{@link #DEFAULT} — scroll generated, Pedestal research as today.</li>
 *   <li>{@link #AUTO} — no scroll generated. Unlock via {@code auto_trigger}
 *       discovery events.</li>
 *   <li>{@link #EXTERNAL} — scroll generated, but the Pedestal refuses to
 *       research it. Modpack devs unlock via {@code /stage unlock} or scripts.</li>
 *   <li>{@link #TEMPORARY} — no scroll generated. Like {@link #AUTO}, unlocks via
 *       {@code auto_trigger} discovery events, but re-locks automatically after a
 *       configured {@code temporary.duration}. Can optionally be re-triggered with
 *       a cooldown (see {@code temporary}).</li>
 *   <li>{@link #CONDITIONAL} — no scroll; open only while its {@code auto_trigger} states
 *       hold, re-evaluated every second.</li>
 * </ul>
 */
public enum StageMode {
    DEFAULT("default"),
    AUTO("auto"),
    EXTERNAL("external"),
    TEMPORARY("temporary"),
    CONDITIONAL("conditional");

    /** True iff this mode reads the {@code auto_trigger} list (AUTO, TEMPORARY, CONDITIONAL). */
    public boolean usesAutoTrigger() {
        return this == AUTO || this == TEMPORARY || this == CONDITIONAL;
    }

    /** True iff a {@code lock_trigger} block applies (DEFAULT and AUTO only). */
    public boolean allowsLockTrigger() {
        return this == DEFAULT || this == AUTO;
    }

    private final String serialized;

    StageMode(String serialized) {
        this.serialized = serialized;
    }

    public String serialize() {
        return serialized;
    }

    /** Returns {@link #DEFAULT} if {@code raw} is null or unknown. */
    public static StageMode parse(String raw) {
        if (raw == null) return DEFAULT;
        for (StageMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return m;
        }
        return DEFAULT;
    }

    /** True iff {@code raw} corresponds to a defined mode (used for warnings). */
    public static boolean isKnown(String raw) {
        if (raw == null) return true; // null = absent = default, not a typo
        for (StageMode m : values()) {
            if (m.serialized.equalsIgnoreCase(raw)) return true;
        }
        return false;
    }
}
