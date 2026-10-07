package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.data.graph.GraphPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Undo/redo for the graph layout. Every layout change already sends a tree's complete position
 * map, so a step is simply the tree as it was and as it became; undoing re-sends the old one.
 *
 * <p>Plain Java on purpose — no packets, no {@code GraphLayoutData} — so it can be unit-tested.
 * Reading and applying a tree is the screen's job, handed in as an {@link Applier}.
 *
 * <p>One history per client ({@link #CLIENT}), so it survives closing the graph and a detour
 * through the stage editor; {@code ClientDisconnectHandler} clears it.
 */
public final class GraphLayoutHistory {

    public static final int LIMIT = 50;

    public static final GraphLayoutHistory CLIENT = new GraphLayoutHistory();

    /**
     * One tree's layout. An unfrozen tree's positions are the algorithm's and get recomputed
     * whenever a stage appears, so they are kept for describing a step but never compared.
     */
    public record TreeState(boolean frozen, Map<String, GraphPos> positions) {

        public static TreeState of(boolean frozen, Map<String, GraphPos> positions) {
            return new TreeState(frozen, Map.copyOf(positions));
        }

        public boolean matches(TreeState other) {
            if (other == null || frozen != other.frozen) return false;
            return !frozen || positions.equals(other.positions);
        }
    }

    public enum Kind { MOVE, PLACE, REMOVE, REARRANGE }

    /** Keyed by the individual flag; only the trees the change touched. */
    public record Step(Kind kind, int count, Map<Boolean, TreeState> before, Map<Boolean, TreeState> after) {

        /** Works out what kind of change it was from the positions alone, so callers need not say. */
        public static Step between(Map<Boolean, TreeState> before, Map<Boolean, TreeState> after) {
            int added = 0, removed = 0, moved = 0;
            for (Map.Entry<Boolean, TreeState> e : after.entrySet()) {
                TreeState old = before.get(e.getKey());
                Map<String, GraphPos> from = old == null ? Map.of() : old.positions();
                Map<String, GraphPos> to = e.getValue().positions();
                for (Map.Entry<String, GraphPos> p : to.entrySet()) {
                    GraphPos was = from.get(p.getKey());
                    if (was == null) added++;
                    else if (!was.equals(p.getValue())) moved++;
                }
                for (String id : from.keySet()) {
                    if (!to.containsKey(id)) removed++;
                }
            }
            if (removed > 0 && added == 0 && moved == 0) return new Step(Kind.REMOVE, removed, Map.copyOf(before), Map.copyOf(after));
            if (added > 0 && removed == 0 && moved == 0) return new Step(Kind.PLACE, added, Map.copyOf(before), Map.copyOf(after));
            return new Step(Kind.MOVE, moved + added + removed, Map.copyOf(before), Map.copyOf(after));
        }

        public static Step rearrange(Map<Boolean, TreeState> before, Map<Boolean, TreeState> after) {
            return new Step(Kind.REARRANGE, 0, Map.copyOf(before), Map.copyOf(after));
        }

        boolean changesNothing() {
            Set<Boolean> trees = new HashSet<>(before.keySet());
            trees.addAll(after.keySet());
            for (Boolean tree : trees) {
                TreeState a = before.get(tree);
                if (a == null || !a.matches(after.get(tree))) return false;
            }
            return true;
        }
    }

    /** The screen's side: what a tree looks like right now, and how to put one back. */
    public interface Applier {
        TreeState current(boolean individual);

        void apply(boolean individual, TreeState state);
    }

    public enum Result { DONE, NOTHING, DISCARDED }

    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();

    public void record(Step step) {
        if (step.changesNothing()) return;
        undo.push(step);
        redo.clear();
        while (undo.size() > LIMIT) undo.removeLast();
    }

    public Result undo(Applier applier) {
        return travel(undo, redo, applier, true);
    }

    public Result redo(Applier applier) {
        return travel(redo, undo, applier, false);
    }

    /**
     * If any touched tree no longer looks the way this step left it, somebody else changed it in
     * the meantime — another admin, or a stage that appeared. Putting the old map back would wipe
     * their change without a word, so the whole history goes instead.
     */
    private Result travel(Deque<Step> from, Deque<Step> to, Applier applier, boolean backwards) {
        Step step = from.peek();
        if (step == null) return Result.NOTHING;
        Map<Boolean, TreeState> expected = backwards ? step.after() : step.before();
        Map<Boolean, TreeState> target = backwards ? step.before() : step.after();
        for (Map.Entry<Boolean, TreeState> e : expected.entrySet()) {
            if (!e.getValue().matches(applier.current(e.getKey()))) {
                clear();
                return Result.DISCARDED;
            }
        }
        target.forEach(applier::apply);
        from.pop();
        to.push(step);
        return Result.DONE;
    }

    public Step peekUndo() {
        return undo.peek();
    }

    public Step peekRedo() {
        return redo.peek();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
