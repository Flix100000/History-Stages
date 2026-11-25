package net.bananemdnsa.historystages.client.editor.widget;

/**
 * Where a popup menu goes so that all of it stays on screen. Kept free of Minecraft types so the
 * arithmetic can be unit-tested.
 */
public final class MenuPlacement {

    private MenuPlacement() {}

    /**
     * Start coordinate along one axis for a menu of {@code size} that wants to open at
     * {@code anchor} on a screen of {@code limit}. If it would run past the far edge it opens the
     * other way, ending at the anchor, the way desktop menus flip near the bottom of the screen.
     * If even that doesn't fit, it is pushed back on screen so the near edge stays reachable.
     */
    public static int fit(int anchor, int size, int limit) {
        if (anchor + size <= limit) return Math.max(0, anchor);
        int flipped = anchor - size;
        if (flipped >= 0) return flipped;
        return Math.max(0, limit - size);
    }

    /** Pushes an already placed start coordinate back inside {@code [0, limit - size]}. */
    public static int clamp(int start, int size, int limit) {
        return Math.max(0, Math.min(start, limit - size));
    }
}
