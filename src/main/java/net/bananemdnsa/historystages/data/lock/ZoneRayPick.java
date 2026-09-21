package net.bananemdnsa.historystages.data.lock;

import java.util.List;

/**
 * Which shape a ray meets first.
 *
 * <p>Free of Minecraft, because otherwise picking a shape on the map could only be checked in
 * game — and a click that grabs the shape next to the one you aimed at is not noticed until you
 * have already edited the wrong one.
 *
 * <p>All three shapes are convex, so a ray enters once and leaves once. Each is reduced to that one
 * interval of ray parameters and the smallest entry wins. A ray starting inside a shape gets a
 * negative entry and therefore beats everything it is looking at, which is what picking from
 * inside a zone should do.
 *
 * <p>The bounds are the drawn ones: a cube runs to the far edge of its last block, and a sphere or
 * cylinder carries the same half-block that the renderer bends its faces onto. That keeps clicking
 * and looking in step; the blockwise verdict a player is judged by is a separate question, asked of
 * {@link ZoneGeometry}.
 */
public final class ZoneRayPick {

    private static final double MISS = Double.POSITIVE_INFINITY;
    private static final double EPSILON = 1e-9;

    private ZoneRayPick() {}

    /**
     * @param worldMinY the dimension's build floor, used by {@code full_height} shapes
     * @param worldMaxY the dimension's build ceiling, likewise
     * @return index of the nearest shape the ray meets, or -1
     */
    public static int nearest(List<ZoneShape> shapes, double ox, double oy, double oz,
                              double dx, double dy, double dz, int worldMinY, int worldMaxY) {
        if (shapes == null || shapes.isEmpty()) return -1;

        int best = -1;
        double bestEntry = MISS;
        for (int i = 0; i < shapes.size(); i++) {
            double entry = entry(shapes.get(i), ox, oy, oz, dx, dy, dz, worldMinY, worldMaxY);
            if (entry < bestEntry) {
                bestEntry = entry;
                best = i;
            }
        }
        return best;
    }

    private static double entry(ZoneShape shape, double ox, double oy, double oz,
                                double dx, double dy, double dz, int minY, int maxY) {
        // The vertical bounds are worked out inside each branch: a sphere carries its own, and
        // handing it the cube's reading of from/to would quietly make it a different shape.
        double[] span = switch (shape.type()) {
            case CUBE -> box(
                    Math.min(shape.fromX(), shape.toX()),
                    shape.fullHeight() ? minY : Math.min(shape.fromY(), shape.toY()),
                    Math.min(shape.fromZ(), shape.toZ()),
                    Math.max(shape.fromX(), shape.toX()) + 1,
                    shape.fullHeight() ? maxY : Math.max(shape.fromY(), shape.toY()) + 1,
                    Math.max(shape.fromZ(), shape.toZ()) + 1,
                    ox, oy, oz, dx, dy, dz);
            case SPHERE -> sphere(shape.fromX() + 0.5, shape.fromY() + 0.5, shape.fromZ() + 0.5,
                    shape.radius() + 0.5, ox, oy, oz, dx, dy, dz);
            case CYLINDER -> overlap(
                    tube(shape.fromX() + 0.5, shape.fromZ() + 0.5, shape.radius() + 0.5,
                            ox, oz, dx, dz),
                    slab(shape.fullHeight() ? minY : shape.fromY(),
                            shape.fullHeight() ? maxY : shape.fromY() + shape.height(), oy, dy));
        };

        if (span == null || span[1] < 0) return MISS;
        return span[0];
    }

    /** Slab method: the ray is inside while it sits between the walls on all three axes. */
    private static double[] box(double x0, double y0, double z0, double x1, double y1, double z1,
                                double ox, double oy, double oz, double dx, double dy, double dz) {
        double[] spanX = slab(x0, x1, ox, dx);
        double[] spanY = slab(y0, y1, oy, dy);
        double[] spanZ = slab(z0, z1, oz, dz);
        return overlap(overlap(spanX, spanY), spanZ);
    }

    /** The stretch of the ray between two parallel walls, or null when it never gets between them. */
    private static double[] slab(double low, double high, double origin, double direction) {
        if (Math.abs(direction) < EPSILON) {
            return origin < low || origin > high ? null : new double[] {
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        }
        double ta = (low - origin) / direction;
        double tb = (high - origin) / direction;
        return ta <= tb ? new double[] {ta, tb} : new double[] {tb, ta};
    }

    private static double[] sphere(double cx, double cy, double cz, double r,
                                   double ox, double oy, double oz,
                                   double dx, double dy, double dz) {
        double ex = ox - cx;
        double ey = oy - cy;
        double ez = oz - cz;
        return roots(dx * dx + dy * dy + dz * dz,
                2 * (ex * dx + ey * dy + ez * dz),
                ex * ex + ey * ey + ez * ez - r * r);
    }

    /**
     * The cylinder wall, taken as endless upwards and cut to height afterwards.
     *
     * <p>The straight drop is the case the quadratic cannot answer — its leading term is zero
     * there. Left to the formula it would divide by zero and every click from above would miss.
     */
    private static double[] tube(double cx, double cz, double r,
                                 double ox, double oz, double dx, double dz) {
        double ex = ox - cx;
        double ez = oz - cz;
        double a = dx * dx + dz * dz;

        if (a < EPSILON) {
            return ex * ex + ez * ez > r * r ? null : new double[] {
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        }
        return roots(a, 2 * (ex * dx + ez * dz), ex * ex + ez * ez - r * r);
    }

    private static double[] roots(double a, double b, double c) {
        double disc = b * b - 4 * a * c;
        if (disc < 0) return null;
        double root = Math.sqrt(disc);
        return new double[] {(-b - root) / (2 * a), (-b + root) / (2 * a)};
    }

    private static double[] overlap(double[] first, double[] second) {
        if (first == null || second == null) return null;
        double enter = Math.max(first[0], second[0]);
        double exit = Math.min(first[1], second[1]);
        return exit < enter ? null : new double[] {enter, exit};
    }
}
