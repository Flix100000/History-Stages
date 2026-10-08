package net.bananemdnsa.historystages.api;

/**
 * A version marker for the HistoryStages addon API.
 *
 * <p>Deliberately holds no registration method. Everything is registered through the NeoForge
 * mod-bus events named {@code Register…Event}, one per extension point, because that is the shape
 * a Minecraft mod author already knows — and a static facade beside it would be a second way to
 * do the same thing.
 *
 * <p><strong>Documentation lives in the wiki:</strong>
 * <a href="https://historystages.github.io/api/addon-development">Addon
 * Development</a>. The working example is the demo addon under
 * {@code net.bananemdnsa.historystages.demo}, which exercises all five extension points and —
 * enforced by a test — reaches for nothing outside this package.
 */
public final class HistoryStagesAPI {

    /**
     * The generation of this API surface. Bumps whenever something under {@code api} is removed,
     * renamed or changes signature, and only then.
     *
     * <p>Usually this matches the mod's major version, but it doesn't have to: a mod release such
     * as 6.3.0 may rebuild part of the API and raise this to 7 on its own. The loader only knows
     * the mod version, so an addon pins the mod versions it was built against in its
     * {@code mods.toml}; the changelog names the mod release that raised the generation, which is
     * where that range has to end. This constant exists to be read in a log line or a crash
     * report, not as a gate.
     */
    public static final int API_VERSION = 6;

    /**
     * Additions within the current generation. Bumps when a release grows the API without
     * breaking it, and starts again at 0 when {@link #API_VERSION} goes up.
     */
    public static final int API_MINOR = 1;

    private HistoryStagesAPI() {}
}