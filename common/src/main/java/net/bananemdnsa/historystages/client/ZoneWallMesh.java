package net.bananemdnsa.historystages.client;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.data.lock.ZoneGeometry;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneShapeType;
import net.bananemdnsa.historystages.data.lock.ZoneSurfaceRuns;
import net.minecraft.core.BlockPos;

/**
 * The block faces of a zone's surface within reach of the camera, worked out once per block the
 * player walks rather than once per frame.
 *
 * <p>Block faces because that is how a zone is asked about itself — a sphere of radius twelve is a
 * set of blocks, and the barrier stops the player at their edges. Rounding is left to the renderer,
 * which pulls the corners of a face belonging to a sphere or a cylinder onto the true surface. That
 * keeps the two honest with each other: the same faces, moved, rather than a second idea of where
 * the zone is.
 *
 * <p>Which shape a face belongs to is settled here, where the blocks are already in hand. It is the
 * shape containing the block on the inside of the face — for an ordinary zone the block itself, for
 * an inverted one its neighbour, because an inverted zone is the world with a hole in it and the
 * hole is what has the shape.
 */
final class ZoneWallMesh {

    /**
     * Six ints per face: block x, y, z, which side is exposed, what to round it onto, and how many
     * blocks it runs for.
     */
    static final int STRIDE = 6;

    static final int DIR_DOWN = 0;
    static final int DIR_UP = 1;
    static final int DIR_NORTH = 2;
    static final int DIR_SOUTH = 3;
    static final int DIR_WEST = 4;
    static final int DIR_EAST = 5;

    /** Which way each of the six sides faces, indexed by the direction constants above. */
    static final int[] STEP_X = { 0, 0, 0, 0, -1, 1 };
    static final int[] STEP_Y = { -1, 1, 0, 0, 0, 0 };
    static final int[] STEP_Z = { 0, 0, -1, 1, 0, 0 };

    /**
     * The direction a flat face grows in when it swallows its neighbours, per side.
     *
     * <p>A flat wall of a hundred blocks is a hundred faces and four hundred corners written out
     * every frame, all of them describing one rectangle. Running them together is what lets the
     * wall reach far enough to see a whole zone at once.
     */
    static final int[] RUN_X = { 1, 1, 1, 1, 0, 0 };
    static final int[] RUN_Z = { 0, 0, 0, 0, 1, 1 };



    private int[] faces = new int[256 * STRIDE];
    private int count;

    /**
     * A round shape some of these faces sit on, together with how its zone reads.
     *
     * <p>The inversion travels with it because it decides which way the face is turned: an
     * ordinary zone shows a sphere from outside, an inverted one from inside, and the renderer
     * would otherwise throw away exactly the half it is meant to draw.
     */
    record Round(ZoneShape shape, boolean inverted) {}

    /** The round surfaces any of these faces sit on. A face on a box refers to none of them. */
    private final List<Round> projectors = new ArrayList<>();

    /** The blocks worth asking about, gathered per zone. A corner is offered three times. */
    private final LongOpenHashSet candidates = new LongOpenHashSet();

    /** The camera block and shape list this mesh was built for, so a rebuild can be skipped. */
    private long builtAt = Long.MIN_VALUE;
    private List<ClientZoneShapes.Visible> builtFrom;
    private int builtRadius = -1;

    int count() {
        return count;
    }

    int face(int index, int slot) {
        return faces[index * STRIDE + slot];
    }

    /** The surface this face has to be rounded onto, or null when it is already flat. */
    Round projector(int index) {
        int slot = faces[index * STRIDE + 4];
        return slot < 0 ? null : projectors.get(slot);
    }

    /** Drops the mesh, so nothing survives into the next world. */
    void forget() {
        count = 0;
        projectors.clear();
        candidates.clear();
        builtAt = Long.MIN_VALUE;
        builtFrom = null;
        builtRadius = -1;
    }

    /**
     * Rebuilds when the camera has crossed into another block, when the server has sent a
     * different set of zones, or when the player has moved the reach in their settings.
     *
     * <p>The zone list is compared by identity on purpose: {@code ClientZoneShapes.update} copies,
     * so every sync is a new instance and every mesh built against the old shapes is dropped here.
     */
    void refresh(List<ClientZoneShapes.Visible> zones, int camX, int camY, int camZ, int radius,
                 int minY, int maxY) {
        // A short reach is cheap enough to redo every block. A long one is not, and it does not
        // need to be: build a little further than asked and the same mesh still fits after a few
        // steps. What that buys is one rebuild every few seconds instead of four a second.
        int slack = Math.max(1, radius / 12);
        long at = key(Math.floorDiv(camX, slack), Math.floorDiv(camY, slack),
                Math.floorDiv(camZ, slack));
        if (at == builtAt && zones == builtFrom && radius == builtRadius) return;

        builtAt = at;
        builtFrom = zones;
        builtRadius = radius;
        count = 0;
        projectors.clear();

        boolean anyDrawn = false;
        for (ClientZoneShapes.Visible zone : zones) {
            anyDrawn |= zone.border();
        }
        // A barrier zone that keeps itself hidden is in this list too, and asking every block
        // around the player about it would be work for a wall nobody is allowed to see.
        if (!anyDrawn) return;

        for (ClientZoneShapes.Visible zone : zones) {
            if (!zone.border()) continue;
            build(zone, camX, camY, camZ, radius + slack, minY, maxY);
        }
    }

    /**
     * The faces of one zone, found through the surface of its shapes rather than by sweeping the
     * space around the camera.
     *
     * <p>The sweep is what used to be here, and it is why the wall could only reach a dozen blocks:
     * the work grows with the cube of the reach, and nearly all of it is spent on blocks buried
     * inside the zone. Asking each shape for its own surface costs what the wall is worth instead.
     */
    private void build(ClientZoneShapes.Visible zone, int camX, int camY, int camZ, int radius,
                       int minY, int maxY) {
        // An inverted zone is made of the blocks around its shapes, not of the shapes, so the
        // blocks worth looking at sit one step further out than the surface being asked for.
        int reach = zone.inverted() ? radius + 1 : radius;
        int x0 = camX - reach;
        int y0 = camY - reach;
        int z0 = camZ - reach;
        int x1 = camX + reach;
        int y1 = camY + reach;
        int z1 = camZ + reach;

        candidates.clear();
        boolean inverted = zone.inverted();
        for (ZoneShape shape : zone.shapes()) {
            ZoneGeometry.surfaceBlocks(shape, x0, y0, z0, x1, y1, z1, minY, maxY,
                    (bx, by, bz) -> {
                        candidates.add(BlockPos.asLong(bx, by, bz));
                        if (!inverted) return;
                        for (int dir = 0; dir < 6; dir++) {
                            candidates.add(BlockPos.asLong(
                                    bx + STEP_X[dir], by + STEP_Y[dir], bz + STEP_Z[dir]));
                        }
                    });
        }

        LongIterator walk = candidates.iterator();
        while (walk.hasNext()) {
            long packed = walk.nextLong();
            int bx = BlockPos.getX(packed);
            int by = BlockPos.getY(packed);
            int bz = BlockPos.getZ(packed);
            if (!solid(zone, bx, by, bz, minY, maxY)) continue;

            for (int dir = 0; dir < 6; dir++) {
                int nx = bx + STEP_X[dir];
                int ny = by + STEP_Y[dir];
                int nz = bz + STEP_Z[dir];
                if (solid(zone, nx, ny, nz, minY, maxY)) continue;

                int innerX = inverted ? nx : bx;
                int innerY = inverted ? ny : by;
                int innerZ = inverted ? nz : bz;
                int projector = projectorFor(zone, innerX, innerY, innerZ, minY, maxY);

                int run = 1;
                if (projector < 0) {
                    int rx = RUN_X[dir];
                    int rz = RUN_Z[dir];
                    int side = dir;
                    run = ZoneSurfaceRuns.lengthAt(rx != 0 ? bx : bz,
                            step -> flat(zone, bx + rx * step, by, bz + rz * step, side,
                                    minY, maxY, x0, y0, z0, x1, y1, z1));
                    // Zero means a face further back along the row already covers this one.
                    if (run == 0) continue;
                }
                add(bx, by, bz, dir, projector, run);
            }
        }
    }

    /**
     * Whether this block carries a face on that side which is flat, and therefore free to be run
     * together with its neighbours.
     *
     * <p>Bounded by the same box the candidates came from. A face just outside it is never written
     * out, so letting it swallow one that is inside would leave a gap at the edge of the wall.
     */
    private boolean flat(ClientZoneShapes.Visible zone, int x, int y, int z, int dir,
                         int minY, int maxY,
                         int x0, int y0, int z0, int x1, int y1, int z1) {
        if (x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1) return false;
        if (!solid(zone, x, y, z, minY, maxY)) return false;

        int nx = x + STEP_X[dir];
        int ny = y + STEP_Y[dir];
        int nz = z + STEP_Z[dir];
        if (solid(zone, nx, ny, nz, minY, maxY)) return false;

        ZoneShape owner = zone.inverted()
                ? ownerOf(zone, nx, ny, nz, minY, maxY)
                : ownerOf(zone, x, y, z, minY, maxY);
        return owner == null || owner.type() == ZoneShapeType.CUBE;
    }

    /** An inverted zone holds the world outside its shapes; everything else holds the inside. */
    private static boolean solid(ClientZoneShapes.Visible zone, int x, int y, int z,
                                 int minY, int maxY) {
        return ZoneGeometry.containsAny(zone.shapes(), x, y, z, minY, maxY) != zone.inverted();
    }

    /** The first shape of the zone holding this block, or null when none of them does. */
    private static ZoneShape ownerOf(ClientZoneShapes.Visible zone, int x, int y, int z,
                                     int minY, int maxY) {
        for (ZoneShape shape : zone.shapes()) {
            if (ZoneGeometry.contains(shape, x, y, z, minY, maxY)) return shape;
        }
        return null;
    }

    /** The slot of the round shape this face sits on, or -1 for a face of a box. */
    private int projectorFor(ClientZoneShapes.Visible zone, int x, int y, int z,
                             int minY, int maxY) {
        ZoneShape shape = ownerOf(zone, x, y, z, minY, maxY);
        if (shape == null || shape.type() == ZoneShapeType.CUBE) return -1;

        for (int slot = 0; slot < projectors.size(); slot++) {
            Round known = projectors.get(slot);
            if (known.shape() == shape && known.inverted() == zone.inverted()) return slot;
        }
        projectors.add(new Round(shape, zone.inverted()));
        return projectors.size() - 1;
    }

    private void add(int x, int y, int z, int dir, int projector, int run) {
        int at = count * STRIDE;
        if (at + STRIDE > faces.length) {
            int[] grown = new int[faces.length * 2];
            System.arraycopy(faces, 0, grown, 0, faces.length);
            faces = grown;
        }
        faces[at] = x;
        faces[at + 1] = y;
        faces[at + 2] = z;
        faces[at + 3] = dir;
        faces[at + 4] = projector;
        faces[at + 5] = run;
        count++;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }
}
