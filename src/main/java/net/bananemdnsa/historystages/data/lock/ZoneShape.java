package net.bananemdnsa.historystages.data.lock;

/**
 * One shape of a zone. A plain carrier — every question about it is answered by
 * {@link ZoneGeometry}.
 *
 * <p>Which fields carry meaning depends on {@link #type()}:
 * <ul>
 *   <li>{@code CUBE} — {@code from} and {@code to} are two opposite corners, in any order.</li>
 *   <li>{@code SPHERE} — {@code from} is the centre, {@code radius} the radius. {@code to} unused.</li>
 *   <li>{@code CYLINDER} — {@code from} is the centre of the <em>floor</em>, {@code radius} the
 *       radius, {@code height} the extent upwards. {@code to} unused.</li>
 * </ul>
 *
 * <p>{@code fullHeight} applies to {@code CUBE} and {@code CYLINDER}: the shape then spans the
 * whole build range of its dimension and the height numbers are ignored. It is the most common
 * case in practice — a village is gated as a whole, not as a slice of one.
 */
public record ZoneShape(
        ZoneShapeType type,
        int fromX, int fromY, int fromZ,
        int toX, int toY, int toZ,
        int radius,
        int height,
        boolean fullHeight) {

    public static ZoneShape cube(int fromX, int fromY, int fromZ, int toX, int toY, int toZ,
                                 boolean fullHeight) {
        return new ZoneShape(ZoneShapeType.CUBE, fromX, fromY, fromZ, toX, toY, toZ,
                0, 0, fullHeight);
    }

    public static ZoneShape sphere(int centreX, int centreY, int centreZ, int radius) {
        return new ZoneShape(ZoneShapeType.SPHERE, centreX, centreY, centreZ, 0, 0, 0,
                radius, 0, false);
    }

    public static ZoneShape cylinder(int centreX, int floorY, int centreZ, int radius, int height,
                                     boolean fullHeight) {
        return new ZoneShape(ZoneShapeType.CYLINDER, centreX, floorY, centreZ, 0, 0, 0,
                radius, height, fullHeight);
    }
}
