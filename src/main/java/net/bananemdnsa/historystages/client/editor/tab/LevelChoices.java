package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.List;

/**
 * The levels the "locked from level" menu offers: 1 for every level, then each level that exists.
 *
 * <p>Picking from what the game knows rather than typing a number, because nobody remembers how
 * high Respiration goes. Without a known maximum — an effect no potion gives — five is a sensible
 * handful. A level already stored above the maximum stays in the list, so opening the menu never
 * silently changes an entry.
 */
final class LevelChoices {

    private static final int UNKNOWN_MAX = 5;

    private LevelChoices() {}

    static List<Integer> of(int max, int current) {
        int top = max > 0 ? max : UNKNOWN_MAX;
        List<Integer> levels = new ArrayList<>();
        for (int level = 1; level <= top; level++) levels.add(level);
        if (current > top) levels.add(current);
        return levels;
    }
}
