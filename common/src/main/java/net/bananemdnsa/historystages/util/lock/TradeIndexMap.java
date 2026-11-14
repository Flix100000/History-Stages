package net.bananemdnsa.historystages.util.lock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of the merchant's offers each trade-screen row stands for, per player.
 *
 * <p>When locked offers are held back, the client numbers the rows it got (0, 1, 2 ...) while the
 * server still numbers the merchant's whole list. A click on row {@code i} must select offer
 * {@code kept[i]}, or a visible trade selects whatever sits at that index server-side: another
 * offer, or a locked one whose payment is then refused.
 */
public final class TradeIndexMap {

    private record Shown(int containerId, List<Integer> kept) {}

    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

    private TradeIndexMap() {}

    /** The rows this player was sent for this menu; {@code null} when nothing was held back. */
    public static void shown(UUID player, int containerId, List<Integer> kept) {
        if (kept == null) {
            SHOWN.remove(player);
        } else {
            SHOWN.put(player, new Shown(containerId, List.copyOf(kept)));
        }
    }

    /** The screen this map entry belongs to closed. */
    public static void forget(UUID player, int containerId) {
        SHOWN.computeIfPresent(player, (id, shown) -> shown.containerId() == containerId ? null : shown);
    }

    /** For tests: whether anything is remembered for this player. */
    public static boolean remembers(UUID player) {
        return SHOWN.containsKey(player);
    }

    /** The merchant's index for a row the player clicked in this menu. */
    public static int serverIndex(UUID player, int containerId, int row) {
        Shown shown = SHOWN.get(player);
        if (shown == null || shown.containerId() != containerId) return row;
        // Out of range stays out of range: vanilla ignores an index it does not have.
        return row >= 0 && row < shown.kept().size() ? shown.kept().get(row) : -1;
    }
}
