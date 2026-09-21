package net.bananemdnsa.historystages.data.lock;

/**
 * One potion effect a zone applies, as it is written in the stage file.
 *
 * <p>A structured object rather than the common config's {@code "id, seconds, amplifier"} string,
 * because this one is edited through a GUI rather than typed into a .toml — but the limits are
 * deliberately the same as {@code BiomeEffectRegistry}'s, so the two behave alike.
 *
 * <p>No Minecraft type here: resolving the id against the effect registry happens where the
 * effect is applied, not where it is stored. An unknown id is logged once and skipped there,
 * exactly as the biome lock does it.
 */
public record ZoneEffectSpec(String id, int seconds, int amplifier) {

    public static final int MAX_SECONDS = 3600;
    public static final int MAX_AMPLIFIER = 255;

    /** True when this entry is worth handing to the effect registry at all. */
    public boolean isUsable() {
        return id != null && !id.isBlank()
                && seconds >= 1 && seconds <= MAX_SECONDS
                && amplifier >= 0 && amplifier <= MAX_AMPLIFIER;
    }

    public int durationTicks() {
        return seconds * 20;
    }
}
