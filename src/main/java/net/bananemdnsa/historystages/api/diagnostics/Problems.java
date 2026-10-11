package net.bananemdnsa.historystages.api.diagnostics;

/**
 * Reports problems for the debug report. Your mod's namespace is put in front of the category,
 * so {@code warn("Relic Sets", …)} from {@code mymod:…} shows as {@code mymod: Relic Sets}.
 *
 * <p>{@code error}: the stage cannot work as written. {@code warn}: it works, but not as the
 * author probably meant. {@code info}: worth knowing, nothing wrong.
 */
public interface Problems {
    void error(String category, String message);
    void warn(String category, String message);
    void info(String category, String message);
}
