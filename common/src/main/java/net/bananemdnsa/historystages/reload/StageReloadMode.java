package net.bananemdnsa.historystages.reload;

/**
 * What a stage change that altered the gated recipes sets off.
 *
 * <ul>
 *   <li>FULL — every datapack again, and the server waits for it. What 6.x shipped.</li>
 *   <li>BACKGROUND — the same full reload, with the server ticking on, see {@link BackgroundReload}.</li>
 *   <li>TARGETED — only the mods' own reload listeners plus a tags event, also without waiting.</li>
 * </ul>
 */
public enum StageReloadMode {
    FULL, BACKGROUND, TARGETED;

    /**
     * What {@code [advanced] targetedReloadSkip} starts out with: listeners that hold no recipes
     * and are slow enough to matter. Here rather than in {@link LightReload} because the config
     * spec is built in tests without Minecraft on the classpath, and this enum needs none.
     *
     * <p>Measured on Manufactio Reforged (121 mods):
     * <ul>
     *   <li>Modular Machinery Reborn reads machine definitions and opens the datapacks once per
     *   machine to find where each came from — 10.7 of the 12 seconds a targeted reload held the
     *   server thread.</li>
     *   <li>The Hordes reads its spawn tables — 1.1 seconds, most of what was left.</li>
     * </ul>
     */
    public static final java.util.List<String> DEFAULT_TARGETED_SKIP = java.util.List.of(
            "es.degrassi.mmreborn.common.machine.MachineJsonReloadListener",
            "net.smileycorp.hordes.hordeevent.data.HordeTableLoader");
}
