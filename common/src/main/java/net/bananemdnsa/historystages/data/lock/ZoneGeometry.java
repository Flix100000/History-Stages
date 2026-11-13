package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers "does this point lie in this shape" — and nothing else.
 *
 * <p><strong>No Minecraft imports, ever.</strong> This is the one part of the zone category a
 * plain unit test can reach, and the one where a mistake is silent: an off-by-one on a corner
 * makes a zone one block too small, which nobody notices until a player walks through the gap.
 * The verifier loads a class as soon as a Minecraft type is assigned anywhere in it, so a single
 * import here would take the whole test class down with it.
 *
 * <p>Build limits arrive as arguments for the same reason. {@code -64} and {@code 320} are the
 * Overworld's; the Nether ends at 128 and a modded dimension is free to be anything.
 */
public final class ZoneGeometry {

    private ZoneGeometry() {}

    /** True when the block at these coordinates lies inside the shape. Corners are inclusive. */
    public static boolean contains(ZoneShape shape, int x, int y, int z, int minY, int maxY) {
        if (shape == null) return false;
        return switch (shape.type()) {
            case CUBE -> containsCube(shape, x, y, z, minY, maxY);
            case SPHERE -> containsSphere(shape, x, y, z);
            case CYLINDER -> containsCylinder(shape, x, y, z, minY, maxY);
        };
    }

    /**
     * True when the point lies in at least one of the shapes — a zone is the union of its shapes,
     * never the intersection. That is what lets an L-shaped area be three cubes rather than a
     * polygon, and it is why an empty list means "nowhere" and not "everywhere".
     */
    public static boolean containsAny(List<ZoneShape> shapes, int x, int y, int z,
                                      int minY, int maxY) {
        if (shapes == null || shapes.isEmpty()) return false;
        for (ZoneShape shape : shapes) {
            if (contains(shape, x, y, z, minY, maxY)) return true;
        }
        return false;
    }

    /**
     * The smallest x/z rectangle enclosing every shape, as {@code {minX, maxX, minZ, maxZ}} —
     * or null when there is nothing to enclose.
     *
     * <p>A cheap pre-filter for the index: one comparison pair rejects a whole zone before any of
     * its shapes is examined, which is what keeps a zone made of a dozen boxes from costing a
     * dozen tests per tick.
     *
     * <p>Horizontal only, deliberately. The vertical extent of a {@code full_height} shape depends
     * on the dimension it sits in, and the index is built from stage data alone — no level in
     * sight. Height is settled by the exact test, where the level is known.
     *
     * <p>The box must never be tighter than the shapes. A box one block too small silently
     * unlocks the edge of a zone, and nothing in game would show it.
     */
    public static int[] horizontalBounds(List<ZoneShape> shapes) {
        if (shapes == null || shapes.isEmpty()) return null;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (ZoneShape shape : shapes) {
            if (shape == null) continue;
            int lowX;
            int highX;
            int lowZ;
            int highZ;
            if (shape.type() == ZoneShapeType.CUBE) {
                lowX = Math.min(shape.fromX(), shape.toX());
                highX = Math.max(shape.fromX(), shape.toX());
                lowZ = Math.min(shape.fromZ(), shape.toZ());
                highZ = Math.max(shape.fromZ(), shape.toZ());
            } else {
                lowX = shape.fromX() - shape.radius();
                highX = shape.fromX() + shape.radius();
                lowZ = shape.fromZ() - shape.radius();
                highZ = shape.fromZ() + shape.radius();
            }
            minX = Math.min(minX, lowX);
            maxX = Math.max(maxX, highX);
            minZ = Math.min(minZ, lowZ);
            maxZ = Math.max(maxZ, highZ);
        }

        if (minX == Integer.MAX_VALUE) return null;
        return new int[]{ minX, maxX, minZ, maxZ };
    }

    /** Takes block coordinates one at a time, without them having to be boxed up first. */
    public interface BlockSink {
        void accept(int x, int y, int z);
    }

    /**
     * Every block of this shape that has a neighbour outside it, limited to the given box.
     *
     * <p>Walking the box block by block would be the obvious way and is the wrong one. A wall is a
     * surface, and the volume behind it grows a whole dimension faster: drawing a zone from sixty
     * blocks away would spend nearly all of its time on blocks buried deep inside it.
     *
     * <p>All three shapes are convex, so a straight line either misses one or crosses it exactly
     * once. The two ends of that crossing are then the only blocks on the line with a neighbour
     * outside <em>along that line</em> — and taking all three directions in turn finds every
     * surface block there is, because a block on the surface has an outside neighbour in at least
     * one of them.
     *
     * <p>A block is handed over more than once: a corner is an end in all three directions. The
     * caller is collecting them into a set anyway, which is what makes that cheaper than checking.
     */
    public static void surfaceBlocks(ZoneShape shape,
                                     int boxX0, int boxY0, int boxZ0,
                                     int boxX1, int boxY1, int boxZ1,
                                     int minY, int maxY, BlockSink out) {
        if (shape == null || out == null) return;

        int[] own = shapeBounds(shape, minY, maxY);
        int x0 = Math.max(boxX0, own[0]);
        int y0 = Math.max(boxY0, own[1]);
        int z0 = Math.max(boxZ0, own[2]);
        int x1 = Math.min(boxX1, own[3]);
        int y1 = Math.min(boxY1, own[4]);
        int z1 = Math.min(boxZ1, own[5]);
        if (x0 > x1 || y0 > y1 || z0 > z1) return;

        int[] run = new int[2];

        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                if (!runX(shape, y, z, minY, maxY, run)) continue;
                if (run[0] >= x0 && run[0] <= x1) out.accept(run[0], y, z);
                if (run[1] != run[0] && run[1] >= x0 && run[1] <= x1) out.accept(run[1], y, z);
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!runY(shape, x, z, minY, maxY, run)) continue;
                if (run[0] >= y0 && run[0] <= y1) out.accept(x, run[0], z);
                if (run[1] != run[0] && run[1] >= y0 && run[1] <= y1) out.accept(x, run[1], z);
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (!runZ(shape, x, y, minY, maxY, run)) continue;
                if (run[0] >= z0 && run[0] <= z1) out.accept(x, y, run[0]);
                if (run[1] != run[0] && run[1] >= z0 && run[1] <= z1) out.accept(x, y, run[1]);
            }
        }
    }

    /** The smallest box holding the whole shape, as {@code {x0, y0, z0, x1, y1, z1}}. */
    private static int[] shapeBounds(ZoneShape shape, int minY, int maxY) {
        if (shape.type() == ZoneShapeType.SPHERE) {
            int r = shape.radius();
            return new int[]{ shape.fromX() - r, shape.fromY() - r, shape.fromZ() - r,
                    shape.fromX() + r, shape.fromY() + r, shape.fromZ() + r };
        }
        int low = lowY(shape, minY);
        int high = highY(shape, maxY);
        if (shape.type() == ZoneShapeType.CYLINDER) {
            int r = shape.radius();
            return new int[]{ shape.fromX() - r, low, shape.fromZ() - r,
                    shape.fromX() + r, high, shape.fromZ() + r };
        }
        return new int[]{ Math.min(shape.fromX(), shape.toX()), low,
                Math.min(shape.fromZ(), shape.toZ()),
                Math.max(shape.fromX(), shape.toX()), high,
                Math.max(shape.fromZ(), shape.toZ()) };
    }

    /** The stretch of the shape along x at this (y, z), or false when the line misses it. */
    private static boolean runX(ZoneShape shape, int y, int z, int minY, int maxY, int[] into) {
        switch (shape.type()) {
            case CUBE -> {
                if (z < Math.min(shape.fromZ(), shape.toZ())) return false;
                if (z > Math.max(shape.fromZ(), shape.toZ())) return false;
                if (y < lowY(shape, minY) || y > highY(shape, maxY)) return false;
                into[0] = Math.min(shape.fromX(), shape.toX());
                into[1] = Math.max(shape.fromX(), shape.toX());
                return true;
            }
            case SPHERE -> {
                long dy = (long) y - shape.fromY();
                long dz = (long) z - shape.fromZ();
                return round(shape, shape.fromX(), squareRest(shape, dy, dz), into);
            }
            default -> {
                if (y < lowY(shape, minY) || y > highY(shape, maxY)) return false;
                long dz = (long) z - shape.fromZ();
                return round(shape, shape.fromX(), squareRest(shape, 0, dz), into);
            }
        }
    }

    /** The stretch along y. For a box and a cylinder that is simply their height. */
    private static boolean runY(ZoneShape shape, int x, int z, int minY, int maxY, int[] into) {
        if (shape.type() == ZoneShapeType.SPHERE) {
            long dx = (long) x - shape.fromX();
            long dz = (long) z - shape.fromZ();
            return round(shape, shape.fromY(), squareRest(shape, dx, dz), into);
        }
        if (shape.type() == ZoneShapeType.CUBE) {
            if (x < Math.min(shape.fromX(), shape.toX())) return false;
            if (x > Math.max(shape.fromX(), shape.toX())) return false;
            if (z < Math.min(shape.fromZ(), shape.toZ())) return false;
            if (z > Math.max(shape.fromZ(), shape.toZ())) return false;
        } else {
            long dx = (long) x - shape.fromX();
            long dz = (long) z - shape.fromZ();
            long r = shape.radius();
            if (dx * dx + dz * dz > r * r) return false;
        }
        into[0] = lowY(shape, minY);
        into[1] = highY(shape, maxY);
        return true;
    }

    /** The stretch along z, the mirror image of {@link #runX}. */
    private static boolean runZ(ZoneShape shape, int x, int y, int minY, int maxY, int[] into) {
        switch (shape.type()) {
            case CUBE -> {
                if (x < Math.min(shape.fromX(), shape.toX())) return false;
                if (x > Math.max(shape.fromX(), shape.toX())) return false;
                if (y < lowY(shape, minY) || y > highY(shape, maxY)) return false;
                into[0] = Math.min(shape.fromZ(), shape.toZ());
                into[1] = Math.max(shape.fromZ(), shape.toZ());
                return true;
            }
            case SPHERE -> {
                long dx = (long) x - shape.fromX();
                long dy = (long) y - shape.fromY();
                return round(shape, shape.fromZ(), squareRest(shape, dx, dy), into);
            }
            default -> {
                if (y < lowY(shape, minY) || y > highY(shape, maxY)) return false;
                long dx = (long) x - shape.fromX();
                return round(shape, shape.fromZ(), squareRest(shape, 0, dx), into);
            }
        }
    }

    private static long squareRest(ZoneShape shape, long a, long b) {
        long r = shape.radius();
        return r * r - a * a - b * b;
    }

    private static boolean round(ZoneShape shape, int centre, long rest, int[] into) {
        if (rest < 0) return false;
        int reach = isqrt(rest);
        into[0] = centre - reach;
        into[1] = centre + reach;
        return true;
    }

    /**
     * Integer square root, rounded down.
     *
     * <p>{@code (int) Math.sqrt} is not good enough here: it can come back a hair under a whole
     * number for a perfect square, and the block that gets lost is on the outside of the shape,
     * where a missing block is a hole in a wall.
     */
    private static int isqrt(long value) {
        int guess = (int) Math.sqrt((double) value);
        while ((long) (guess + 1) * (guess + 1) <= value) guess++;
        while (guess > 0 && (long) guess * guess > value) guess--;
        return guess;
    }

    /**
     * How finely a movement segment is walked when looking for the point it entered a zone.
     *
     * <p>Half a block, because the thinnest shape anyone can draw is one block: a coarser step
     * could pass straight through a wall and report nothing, which is the very failure this
     * method exists to prevent.
     */
    private static final double SEGMENT_STEP = 0.5;

    /**
     * The first point along the movement from one position to the next that lies in any of these
     * shapes, or null when the movement stays clear of them.
     *
     * <p>Without this the barrier is a sieve. With an elytra and a rocket a player covers twenty
     * blocks between two ticks, so a thin zone is never occupied at the moment anyone looks — they
     * were never "inside", and a per-tick check has nothing to react to.
     *
     * <p>Walked in steps rather than intersected analytically. Three shape types would each need
     * their own intersection, and at the distances involved the walk is both fast enough and
     * obviously right.
     */
    public static double[] segmentEnters(List<ZoneShape> shapes,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         int minY, int maxY) {
        if (shapes == null || shapes.isEmpty()) return null;

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(distance / SEGMENT_STEP);

        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            double x = x1 + dx * t;
            double y = y1 + dy * t;
            double z = z1 + dz * t;
            if (containsAny(shapes, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                    minY, maxY)) {
                return new double[]{ x, y, z };
            }
        }
        return null;
    }

    /**
     * How far past a wall a pushed position lands.
     *
     * <p>Landing exactly on the boundary would read as "still inside" on the next tick, and the
     * player would be shoved again and again without ever getting out.
     */
    private static final double CLEARANCE = 0.35;

    /**
     * How many times a point is pushed before the arrangement is called impossible.
     *
     * <p>Shapes can box a point in — three cubes around a corner, or one that spans the world.
     * Without a cap the loop would run until the tick budget is gone.
     */
    private static final int MAX_ESCAPE_STEPS = 8;

    /**
     * A position that satisfies this whole zone, or null when the point already does.
     *
     * <p>"Satisfies" depends on which way round the zone reads. An ordinary zone is escaped by
     * leaving <em>every</em> one of its shapes — pushing out of one straight into its neighbour
     * would leave the player just as stuck, which is why this loops rather than pushing once. An
     * inverted zone is a cage, and satisfying it means getting back inside one of them.
     *
     * <p>May return a position that is still inside. That is the honest answer for an arrangement
     * with no way out, and the caller can then leave the player alone instead of shoving them
     * around every tick.
     */
    public static double[] escape(List<ZoneShape> shapes, boolean inverted,
                                  double x, double y, double z, int minY, int maxY) {
        if (shapes == null || shapes.isEmpty()) return null;

        boolean inside = containsAny(shapes, (int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z), minY, maxY);
        // An ordinary zone holds you when you are inside it, an inverted one when you are not.
        // Matching means the zone has no claim on this point, so there is nothing to escape.
        if (inside == inverted) return null;

        if (inverted) return nearestInsideAny(shapes, x, y, z, minY, maxY);

        double[] at = { x, y, z };
        for (int step = 0; step < MAX_ESCAPE_STEPS; step++) {
            ZoneShape blocking = firstContaining(shapes, at, minY, maxY);
            if (blocking == null) return at;

            List<double[]> candidates = exits(blocking, at[0], at[1], at[2], minY, maxY);

            // Nearest exit that is clear of the whole zone, not merely of the shape being left.
            // Without this pass two touching cubes bounce a player between them until the cap.
            double[] free = null;
            for (double[] candidate : candidates) {
                if (!containsAny(shapes, (int) Math.floor(candidate[0]),
                        (int) Math.floor(candidate[1]), (int) Math.floor(candidate[2]),
                        minY, maxY)) {
                    free = candidate;
                    break;
                }
            }
            if (free != null) return free;

            // Every exit lands in another shape. Take the nearest and try again from there —
            // a longer chain may still open out.
            at = candidates.get(0);
        }
        return at;
    }

    private static ZoneShape firstContaining(List<ZoneShape> shapes, double[] at,
                                             int minY, int maxY) {
        for (ZoneShape shape : shapes) {
            if (contains(shape, (int) Math.floor(at[0]), (int) Math.floor(at[1]),
                    (int) Math.floor(at[2]), minY, maxY)) {
                return shape;
            }
        }
        return null;
    }

    /** The nearest way into any one of the shapes — the shortest trip back into the cage. */
    private static double[] nearestInsideAny(List<ZoneShape> shapes, double x, double y, double z,
                                             int minY, int maxY) {
        double[] best = null;
        double bestDistance = Double.MAX_VALUE;

        for (ZoneShape shape : shapes) {
            double[] candidate = nearestInside(shape, x, y, z, minY, maxY);
            if (candidate == null) continue;

            double dx = candidate[0] - x;
            double dy = candidate[1] - y;
            double dz = candidate[2] - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * The closest position outside this shape, or null when the point is already outside.
     *
     * <p>Doubles, not blocks: this feeds a player's position, and rounding to the block would
     * either leave them inside or throw them further than the shortest way out.
     */
    public static double[] nearestOutside(ZoneShape shape, double x, double y, double z,
                                          int minY, int maxY) {
        if (shape == null) return null;
        if (!contains(shape, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                minY, maxY)) {
            return null;
        }
        return exits(shape, x, y, z, minY, maxY).get(0);
    }

    /** Every way out of this shape, nearest first. Never empty for a point that is inside. */
    private static List<double[]> exits(ZoneShape shape, double x, double y, double z,
                                        int minY, int maxY) {
        return switch (shape.type()) {
            case CUBE -> boxExits(x, y, z,
                    Math.min(shape.fromX(), shape.toX()), Math.max(shape.fromX(), shape.toX()) + 1,
                    lowY(shape, minY), highY(shape, maxY) + 1,
                    Math.min(shape.fromZ(), shape.toZ()), Math.max(shape.fromZ(), shape.toZ()) + 1,
                    shape.fullHeight());
            case SPHERE -> List.of(outOfSphere(shape, x, y, z));
            case CYLINDER -> cylinderExits(shape, x, y, z, minY, maxY);
        };
    }

    /**
     * The closest position inside this shape, or null when the point is already inside.
     *
     * <p>The cage half of {@link #nearestOutside}: an inverted zone keeps a player in rather than
     * out, and the shortest way back is the same walls read from the other side.
     */
    public static double[] nearestInside(ZoneShape shape, double x, double y, double z,
                                         int minY, int maxY) {
        if (shape == null) return null;
        if (contains(shape, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                minY, maxY)) {
            return null;
        }
        return switch (shape.type()) {
            case CUBE -> intoBox(x, y, z,
                    Math.min(shape.fromX(), shape.toX()), Math.max(shape.fromX(), shape.toX()) + 1,
                    lowY(shape, minY), highY(shape, maxY) + 1,
                    Math.min(shape.fromZ(), shape.toZ()), Math.max(shape.fromZ(), shape.toZ()) + 1);
            case SPHERE, CYLINDER -> intoRound(shape, x, y, z, minY, maxY);
        };
    }

    private static int lowY(ZoneShape shape, int minY) {
        if (shape.fullHeight()) return minY;
        return shape.type() == ZoneShapeType.CYLINDER
                ? shape.fromY()
                : Math.min(shape.fromY(), shape.toY());
    }

    private static int highY(ZoneShape shape, int maxY) {
        if (shape.fullHeight()) return maxY;
        return shape.type() == ZoneShapeType.CYLINDER
                ? shape.fromY() + shape.height()
                : Math.max(shape.fromY(), shape.toY());
    }

    /**
     * Every way out of a box, nearest first.
     *
     * <p>All of them rather than only the nearest, because the nearest is not always usable: two
     * cubes side by side — which is how anyone builds an L — push a player out of one and straight
     * into the other, and back again. {@link #escape} walks this list until it finds an exit that
     * is clear of the whole zone.
     *
     * <p>The vertical two are left out when the shape reaches the build limits; there is no
     * leaving a full-height shape upwards.
     */
    private static List<double[]> boxExits(double x, double y, double z,
                                           double x0, double x1, double y0, double y1,
                                           double z0, double z1, boolean fullHeight) {
        List<double[]> exits = new ArrayList<>(6);
        exits.add(new double[]{ x0 - CLEARANCE, y, z });
        exits.add(new double[]{ x1 + CLEARANCE, y, z });
        exits.add(new double[]{ x, y, z0 - CLEARANCE });
        exits.add(new double[]{ x, y, z1 + CLEARANCE });
        if (!fullHeight) {
            exits.add(new double[]{ x, y0 - CLEARANCE, z });
            exits.add(new double[]{ x, y1 + CLEARANCE, z });
        }
        sortByDistance(exits, x, y, z);
        return exits;
    }

    private static void sortByDistance(List<double[]> points, double x, double y, double z) {
        points.sort((a, b) -> Double.compare(
                squaredDistance(a, x, y, z), squaredDistance(b, x, y, z)));
    }

    private static double squaredDistance(double[] p, double x, double y, double z) {
        double dx = p[0] - x;
        double dy = p[1] - y;
        double dz = p[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** In through whichever wall is nearest — the same comparison read from outside. */
    private static double[] intoBox(double x, double y, double z,
                                    double x0, double x1, double y0, double y1,
                                    double z0, double z1) {
        return new double[]{
                clamp(x, x0 + CLEARANCE, x1 - CLEARANCE),
                clamp(y, y0 + CLEARANCE, y1 - CLEARANCE),
                clamp(z, z0 + CLEARANCE, z1 - CLEARANCE) };
    }

    private static double[] outOfSphere(ZoneShape shape, double x, double y, double z) {
        double dx = x - shape.fromX();
        double dy = y - shape.fromY();
        double dz = z - shape.fromZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double reach = shape.radius() + 1 + CLEARANCE;

        // Dead centre there is no direction at all. Straight up, fixed, so the answer is the same
        // on every tick — anything picked afresh each time makes the player vibrate.
        if (length < 1.0e-6) {
            return new double[]{ shape.fromX(), shape.fromY() + reach, shape.fromZ() };
        }
        double scale = reach / length;
        return new double[]{
                shape.fromX() + dx * scale,
                shape.fromY() + dy * scale,
                shape.fromZ() + dz * scale };
    }

    /** Sideways along the radius, plus the floor and the ceiling where there is one. */
    private static List<double[]> cylinderExits(ZoneShape shape, double x, double y, double z,
                                                int minY, int maxY) {
        double dx = x - shape.fromX();
        double dz = z - shape.fromZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double reach = shape.radius() + 1 + CLEARANCE;

        List<double[]> exits = new ArrayList<>(3);
        if (flat < 1.0e-6) {
            // On the axis every direction is equally far; pick one and keep picking it, or the
            // player is nudged somewhere different each tick and shivers on the spot.
            exits.add(new double[]{ shape.fromX() + reach, y, shape.fromZ() });
        } else {
            double scale = reach / flat;
            exits.add(new double[]{ shape.fromX() + dx * scale, y, shape.fromZ() + dz * scale });
        }

        if (!shape.fullHeight()) {
            exits.add(new double[]{ x, lowY(shape, minY) - CLEARANCE, z });
            exits.add(new double[]{ x, highY(shape, maxY) + 1 + CLEARANCE, z });
        }
        sortByDistance(exits, x, y, z);
        return exits;
    }

    /** Pulls a point onto the near side of a round shape's surface. */
    private static double[] intoRound(ZoneShape shape, double x, double y, double z,
                                      int minY, int maxY) {
        double dx = x - shape.fromX();
        double dz = z - shape.fromZ();
        double dy = shape.type() == ZoneShapeType.SPHERE ? y - shape.fromY() : 0;

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double reach = Math.max(0, shape.radius() - CLEARANCE);
        if (length < 1.0e-6) {
            return new double[]{ shape.fromX(), shape.fromY(), shape.fromZ() };
        }
        double scale = reach / length;

        double ny = shape.type() == ZoneShapeType.SPHERE
                ? shape.fromY() + dy * scale
                : clamp(y, lowY(shape, minY) + CLEARANCE, highY(shape, maxY) + 1 - CLEARANCE);
        return new double[]{ shape.fromX() + dx * scale, ny, shape.fromZ() + dz * scale };
    }

    private static double clamp(double value, double low, double high) {
        if (low > high) return (low + high) / 2;
        return Math.max(low, Math.min(high, value));
    }

    /** True when the point is inside a box from {@link #horizontalBounds}. Null means "no". */
    public static boolean withinHorizontalBounds(int[] bounds, int x, int z) {
        return bounds != null
                && x >= bounds[0] && x <= bounds[1]
                && z >= bounds[2] && z <= bounds[3];
    }

    private static boolean containsCube(ZoneShape shape, int x, int y, int z, int minY, int maxY) {
        if (x < Math.min(shape.fromX(), shape.toX())) return false;
        if (x > Math.max(shape.fromX(), shape.toX())) return false;
        if (z < Math.min(shape.fromZ(), shape.toZ())) return false;
        if (z > Math.max(shape.fromZ(), shape.toZ())) return false;
        return withinHeight(shape, y, Math.min(shape.fromY(), shape.toY()),
                Math.max(shape.fromY(), shape.toY()), minY, maxY);
    }

    /**
     * Squared distance throughout — a square root here would cost precision and buy nothing, and
     * this runs for every player every tick.
     *
     * <p>{@code long} rather than {@code int} is not overcaution: Minecraft allows coordinates out
     * to 30 million, where {@code dx * dx} overflows an {@code int} and the zone would answer
     * wrongly in exactly the remote places nobody thinks to test.
     */
    private static boolean containsSphere(ZoneShape shape, int x, int y, int z) {
        long dx = (long) x - shape.fromX();
        long dy = (long) y - shape.fromY();
        long dz = (long) z - shape.fromZ();
        long radius = shape.radius();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static boolean containsCylinder(ZoneShape shape, int x, int y, int z,
                                            int minY, int maxY) {
        long dx = (long) x - shape.fromX();
        long dz = (long) z - shape.fromZ();
        long radius = shape.radius();
        if (dx * dx + dz * dz > radius * radius) return false;
        // The centre is the floor and the height grows upwards; see the spec, section 4.
        return withinHeight(shape, y, shape.fromY(), shape.fromY() + shape.height(), minY, maxY);
    }

    /**
     * The height test the cube and the cylinder share. {@code fullHeight} replaces the shape's own
     * numbers with the dimension's build range.
     */
    private static boolean withinHeight(ZoneShape shape, int y, int shapeLow, int shapeHigh,
                                        int minY, int maxY) {
        int low = shape.fullHeight() ? minY : shapeLow;
        int high = shape.fullHeight() ? maxY : shapeHigh;
        return y >= low && y <= high;
    }
}
