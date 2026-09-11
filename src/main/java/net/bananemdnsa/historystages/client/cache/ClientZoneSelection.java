package net.bananemdnsa.historystages.client.cache;

/**
 * What this client knows about its own zone selection.
 *
 * <p>Only ever the viewer's own two corners — a selection is private, and nothing here is shared
 * between players. Read by the preview renderer every frame and by the zone editor when it offers
 * to turn the selection into a shape.
 */
public final class ClientZoneSelection {

    private static volatile String dimension = "";
    private static volatile boolean hasFirst;
    private static volatile int firstX;
    private static volatile int firstY;
    private static volatile int firstZ;
    private static volatile boolean hasSecond;
    private static volatile int secondX;
    private static volatile int secondY;
    private static volatile int secondZ;

    private ClientZoneSelection() {}

    public static void update(String dimensionId,
                              boolean first, int fx, int fy, int fz,
                              boolean second, int sx, int sy, int sz) {
        dimension = dimensionId == null ? "" : dimensionId;
        hasFirst = first;
        firstX = fx;
        firstY = fy;
        firstZ = fz;
        hasSecond = second;
        secondX = sx;
        secondY = sy;
        secondZ = sz;
    }

    public static String dimension() { return dimension; }

    public static boolean hasFirst() { return hasFirst; }
    public static boolean hasSecond() { return hasSecond; }
    public static boolean isComplete() { return hasFirst && hasSecond; }

    public static int firstX() { return firstX; }
    public static int firstY() { return firstY; }
    public static int firstZ() { return firstZ; }
    public static int secondX() { return secondX; }
    public static int secondY() { return secondY; }
    public static int secondZ() { return secondZ; }

    public static void clear() {
        dimension = "";
        hasFirst = false;
        hasSecond = false;
    }
}
