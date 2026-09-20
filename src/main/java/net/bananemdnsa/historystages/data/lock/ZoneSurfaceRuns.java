package net.bananemdnsa.historystages.data.lock;

/**
 * Decides which of a row of identical wall faces gets drawn as one long one, and how long.
 *
 * <p>A flat wall a hundred blocks across is a hundred faces describing one rectangle, and writing
 * all of them out every frame is what used to keep the zone border down to a dozen blocks of reach.
 * Running them together fixes that, but only if every face in the row agrees on who covers whom —
 * and there is no shared bookkeeping to ask, because the faces are found in whatever order they
 * come out of a hash set.
 *
 * <p>So the runs are pinned to a fixed grid instead of starting wherever the wall does. A face
 * either sits on a multiple of {@link #CAP}, in which case it starts a run, or it is covered by
 * whatever started before it in the same grid step. Both halves can then be decided by looking at
 * one face and its immediate neighbours, with no argument possible.
 *
 * <p><strong>The cap is why the grid is needed.</strong> The first version simply asked "does the
 * face behind me exist" and stood down if it did. On a wall longer than the cap that is a lie: the
 * run behind it stopped at the cap, and everything past it stood down for a run that never
 * arrived. The wall lost every block past the first stretch.
 *
 * <p>No Minecraft imports, so the property that matters — every face covered exactly once — can be
 * checked by a plain unit test rather than by looking at a wall in game and hoping.
 */
public final class ZoneSurfaceRuns {

    /**
     * How many faces one may swallow.
     *
     * <p>Not unbounded, because a face is drawn at a single strength and that strength comes from
     * how far its middle is from the camera. A rectangle running off into the distance would carry
     * the brightness of its middle the whole way.
     */
    public static final int CAP = 16;

    /** Whether the face this many steps along the row exists and is flat. Step 0 is this face. */
    public interface Row {
        boolean flatAt(int step);
    }

    private ZoneSurfaceRuns() {}

    /**
     * How many faces this one is to cover, or zero when an earlier one already covers it.
     *
     * @param coordinate this face's position along the row, in blocks
     */
    public static int lengthAt(int coordinate, Row row) {
        int offset = Math.floorMod(coordinate, CAP);
        if (offset != 0 && row.flatAt(-1)) return 0;

        int room = CAP - offset;
        int length = 1;
        while (length < room && row.flatAt(length)) length++;
        return length;
    }
}
