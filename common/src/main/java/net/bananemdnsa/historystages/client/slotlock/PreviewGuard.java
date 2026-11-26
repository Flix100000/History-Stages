package net.bananemdnsa.historystages.client.slotlock;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds someone else's menu or screen for the editor without letting it take the editor down.
 *
 * <p>Menus are normally built from data the server sends, such as the machine's position. The
 * editor has none, so some mod GUIs throw. A target that failed once is not tried again in this
 * session: its constructor already showed it cannot work here, and the in-world pick is the way.
 */
public final class PreviewGuard {

    private final Set<String> failed = new HashSet<>();

    public <T> Optional<T> attempt(String target, Supplier<T> build) {
        if (failed.contains(target)) return Optional.empty();
        try {
            T built = build.get();
            if (built == null) {
                failed.add(target);
                return Optional.empty();
            }
            return Optional.of(built);
        } catch (Throwable t) {
            failed.add(target);
            return Optional.empty();
        }
    }

    public void markFailed(String target) {
        failed.add(target);
    }

    public boolean hasFailed(String target) {
        return failed.contains(target);
    }
}
