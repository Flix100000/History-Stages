package net.bananemdnsa.historystages.data.lock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The two corners a player has marked out, waiting to be turned into a zone.
 *
 * <p>Server side, and deliberately <strong>not</strong> world data. A selection is a step that
 * lasts minutes, not world state: it is gone at logout, which saves a saved-data file and the
 * question of what to do with the marks left in somebody's year-old world.
 *
 * <p>No Minecraft imports. A UUID, a dimension name and six numbers is all a selection is, and
 * keeping it that way is what lets its rules be proven without a running game — including the one
 * rule that is easy to get wrong: marking in another world starts over rather than mixing the two.
 */
public final class ZoneSelection {

    private static final Map<UUID, Selection> BY_PLAYER = new ConcurrentHashMap<>();

    private ZoneSelection() {}

    /** One player's marked corners. Mutable, and only ever reached through this class. */
    public static final class Selection {
        private String dimension;
        private boolean hasFirst;
        private int firstX;
        private int firstY;
        private int firstZ;
        private boolean hasSecond;
        private int secondX;
        private int secondY;
        private int secondZ;

        private Selection(String dimension) {
            this.dimension = dimension;
        }

        public String dimension() { return dimension; }

        public boolean hasFirst() { return hasFirst; }
        public boolean hasSecond() { return hasSecond; }

        /** Both corners are set, so this describes a box. */
        public boolean isComplete() { return hasFirst && hasSecond; }

        public int firstX() { return firstX; }
        public int firstY() { return firstY; }
        public int firstZ() { return firstZ; }
        public int secondX() { return secondX; }
        public int secondY() { return secondY; }
        public int secondZ() { return secondZ; }
    }

    /**
     * Sets corner {@code pointNumber} (1 or 2).
     *
     * <p>When the dimension differs from the one already marked, the other corner is dropped
     * rather than kept: a box with one corner in the Nether and one in the Overworld describes
     * nothing, and silently keeping the stale corner is the sort of thing that is only noticed
     * after saving.
     */
    public static void setPoint(UUID player, int pointNumber, int x, int y, int z,
                                String dimension) {
        Selection selection = BY_PLAYER.compute(player, (id, existing) -> {
            if (existing == null) return new Selection(dimension);
            if (!existing.dimension.equals(dimension)) {
                // Same object rather than a fresh one, so a caller holding a reference does not
                // keep reading the discarded world's corners.
                existing.dimension = dimension;
                existing.hasFirst = false;
                existing.hasSecond = false;
            }
            return existing;
        });

        if (pointNumber == 1) {
            selection.hasFirst = true;
            selection.firstX = x;
            selection.firstY = y;
            selection.firstZ = z;
        } else {
            selection.hasSecond = true;
            selection.secondX = x;
            selection.secondY = y;
            selection.secondZ = z;
        }
    }

    /**
     * Which corner the bare {@code /history zone mark} should fill next: 1 when none or both are
     * set, 2 when only the first is.
     *
     * <p>Here rather than in the command, so the alternating rule is stated once.
     */
    public static int nextPoint(UUID player) {
        Selection selection = BY_PLAYER.get(player);
        if (selection == null) return 1;
        return selection.hasFirst && !selection.hasSecond ? 2 : 1;
    }

    /** This player's selection, or null when they have not marked anything. */
    public static Selection of(UUID player) {
        return BY_PLAYER.get(player);
    }

    public static void clear(UUID player) {
        BY_PLAYER.remove(player);
    }

    /** Drops every selection. For the tests, and for a server shutting a world down. */
    public static void clearAll() {
        BY_PLAYER.clear();
    }
}
