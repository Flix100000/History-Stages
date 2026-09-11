package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one property the merging has to have: every face drawn exactly once.
 *
 * <p>Checked by laying the runs back out over the row rather than by asserting lengths. Lengths
 * are an implementation detail and would have to be rewritten the next time the cap moves; a wall
 * with a hole in it is the actual failure, and it is the same failure whatever the cap says.
 */
class ZoneSurfaceRunsTest {

    /**
     * Runs the merging over a row of faces and reports how many times each block ends up covered.
     *
     * @param wall for each block along the row, whether it carries a face at all
     */
    private static int[] coverage(boolean[] wall) {
        int[] covered = new int[wall.length];
        for (int at = 0; at < wall.length; at++) {
            if (!wall[at]) continue;

            int here = at;
            int length = ZoneSurfaceRuns.lengthAt(at, step -> {
                int probe = here + step;
                return probe >= 0 && probe < wall.length && wall[probe];
            });
            for (int i = 0; i < length; i++) covered[at + i]++;
        }
        return covered;
    }

    private static void assertCoveredOnce(boolean[] wall) {
        int[] covered = coverage(wall);
        List<String> wrong = new ArrayList<>();
        for (int at = 0; at < wall.length; at++) {
            int want = wall[at] ? 1 : 0;
            if (covered[at] != want) {
                wrong.add("block " + at + " is drawn " + covered[at] + " times, wanted " + want);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }

    private static boolean[] solidRow(int length) {
        boolean[] wall = new boolean[length];
        java.util.Arrays.fill(wall, true);
        return wall;
    }

    /** The one that was broken: past the cap every face stood down for a run that never came. */
    @Test
    void aWallLongerThanTheCapIsStillDrawnEndToEnd() {
        assertCoveredOnce(solidRow(4 * ZoneSurfaceRuns.CAP + 5));
    }

    @Test
    void aShortWallIsDrawnOnce() {
        assertCoveredOnce(solidRow(3));
    }

    @Test
    void aWallWithGapsInItIsDrawnOnce() {
        boolean[] wall = solidRow(60);
        wall[7] = false;
        wall[8] = false;
        wall[19] = false;
        wall[32] = false;
        wall[33] = false;
        wall[34] = false;
        assertCoveredOnce(wall);
    }

    /** A gap right where a run would have started, and one right before the next grid step. */
    @Test
    void aWallBrokenAtTheGridStepsIsDrawnOnce() {
        boolean[] wall = solidRow(5 * ZoneSurfaceRuns.CAP);
        wall[ZoneSurfaceRuns.CAP] = false;
        wall[2 * ZoneSurfaceRuns.CAP - 1] = false;
        wall[3 * ZoneSurfaceRuns.CAP + 1] = false;
        assertCoveredOnce(wall);
    }

    /** Zones sit at negative coordinates as often as not, and the grid has to hold there too. */
    @Test
    void aWallAtNegativeCoordinatesIsDrawnOnce() {
        int length = 3 * ZoneSurfaceRuns.CAP + 7;
        int origin = -100;

        int[] covered = new int[length];
        for (int i = 0; i < length; i++) {
            int here = i;
            int run = ZoneSurfaceRuns.lengthAt(origin + i,
                    step -> here + step >= 0 && here + step < length);
            for (int k = 0; k < run; k++) covered[i + k]++;
        }
        for (int i = 0; i < length; i++) {
            assertEquals(1, covered[i], "block " + (origin + i) + " is drawn " + covered[i] + " times");
        }
    }

    @Test
    void noRunEverExceedsTheCap() {
        boolean[] wall = solidRow(200);
        for (int at = 0; at < wall.length; at++) {
            int here = at;
            int run = ZoneSurfaceRuns.lengthAt(at,
                    step -> here + step >= 0 && here + step < wall.length);
            assertTrue(run <= ZoneSurfaceRuns.CAP, "run at " + at + " is " + run + " long");
        }
    }
}
