package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.Kind;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.Result;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.Step;
import net.bananemdnsa.historystages.client.editor.graph.GraphLayoutHistory.TreeState;
import net.bananemdnsa.historystages.data.graph.GraphPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphLayoutHistoryTest {

    /** Stands in for the screen: holds both trees and records every apply. */
    private static final class FakeLayout implements GraphLayoutHistory.Applier {
        final Map<Boolean, TreeState> trees = new HashMap<>();
        final List<String> applied = new ArrayList<>();

        FakeLayout(TreeState global) {
            trees.put(false, global);
            trees.put(true, TreeState.of(false, Map.of()));
        }

        @Override
        public TreeState current(boolean individual) {
            return trees.get(individual);
        }

        @Override
        public void apply(boolean individual, TreeState state) {
            applied.add(individual + "->" + state.frozen());
            trees.put(individual, state);
        }
    }

    private static TreeState frozen(Object... idXY) {
        Map<String, GraphPos> map = new HashMap<>();
        for (int i = 0; i < idXY.length; i += 3) {
            map.put((String) idXY[i], GraphPos.of((Integer) idXY[i + 1], (Integer) idXY[i + 2]));
        }
        return TreeState.of(true, map);
    }

    /** Records a global-tree change from the layout's current state to {@code next}, as the screen does. */
    private static void change(GraphLayoutHistory history, FakeLayout layout, TreeState next) {
        TreeState before = layout.current(false);
        layout.trees.put(false, next);
        history.record(Step.between(Map.of(false, before), Map.of(false, next)));
    }

    @Test
    void undoRestoresTheStateBeforeTheStep() {
        TreeState start = frozen("a", 0, 0);
        FakeLayout layout = new FakeLayout(start);
        GraphLayoutHistory history = new GraphLayoutHistory();
        change(history, layout, frozen("a", 2, 0));

        assertEquals(Result.DONE, history.undo(layout));
        assertEquals(start, layout.current(false));
    }

    @Test
    void redoRestoresTheStateAfterTheStep() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();
        TreeState moved = frozen("a", 2, 0);
        change(history, layout, moved);
        history.undo(layout);

        assertEquals(Result.DONE, history.redo(layout));
        assertEquals(moved, layout.current(false));
        assertFalse(history.canRedo());
        assertTrue(history.canUndo());
    }

    @Test
    void aNewStepDropsTheRedoBranch() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();
        change(history, layout, frozen("a", 1, 0));
        history.undo(layout);
        assertTrue(history.canRedo());

        change(history, layout, frozen("a", 0, 5));

        assertFalse(history.canRedo());
    }

    @Test
    void keepsOnlyTheNewestFiftySteps() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();
        for (int i = 1; i <= GraphLayoutHistory.LIMIT + 5; i++) change(history, layout, frozen("a", i, 0));

        int undone = 0;
        while (history.undo(layout) == Result.DONE) undone++;

        assertEquals(GraphLayoutHistory.LIMIT, undone);
        // The five oldest steps fell out, so the furthest back we get is position 5, not 0.
        assertEquals(frozen("a", 5, 0), layout.current(false));
    }

    @Test
    void aStepThatChangesNothingIsNotRecorded() {
        GraphLayoutHistory history = new GraphLayoutHistory();
        TreeState same = frozen("a", 1, 1);
        history.record(Step.between(Map.of(false, same), Map.of(false, frozen("a", 1, 1))));

        assertFalse(history.canUndo());
    }

    @Test
    void someoneElsesChangeDiscardsTheHistoryInsteadOfOverwritingIt() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();
        change(history, layout, frozen("a", 1, 0));
        // Another admin moves the stage before we undo.
        layout.trees.put(false, frozen("a", 9, 9));

        assertEquals(Result.DISCARDED, history.undo(layout));
        assertTrue(layout.applied.isEmpty());
        assertEquals(frozen("a", 9, 9), layout.current(false));
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    void anUnfrozenTreeIsComparedWithoutItsComputedPositions() {
        // Before the first drag the tree is unfrozen; its positions are whatever the algorithm
        // produced and may be recomputed (a stage was added) without anyone touching the layout.
        TreeState auto = TreeState.of(false, Map.of("a", GraphPos.of(0, 0)));
        FakeLayout layout = new FakeLayout(frozen("a", 3, 3));
        GraphLayoutHistory history = new GraphLayoutHistory();
        history.record(Step.rearrange(Map.of(false, frozen("a", 3, 3)), Map.of(false, auto)));
        layout.trees.put(false, TreeState.of(false, Map.of("a", GraphPos.of(0, 2), "b", GraphPos.of(2, 0))));

        assertEquals(Result.DONE, history.undo(layout));
        assertEquals(frozen("a", 3, 3), layout.current(false));
    }

    @Test
    void aFrozenTreeIsComparedByItsPositions() {
        assertFalse(frozen("a", 0, 0).matches(frozen("a", 0, 1)));
        assertTrue(frozen("a", 0, 0).matches(frozen("a", 0, 0)));
        assertFalse(frozen("a", 0, 0).matches(TreeState.of(false, Map.of("a", GraphPos.of(0, 0)))));
    }

    @Test
    void undoWithNothingRecordedDoesNothing() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();

        assertEquals(Result.NOTHING, history.undo(layout));
        assertEquals(Result.NOTHING, history.redo(layout));
        assertTrue(layout.applied.isEmpty());
    }

    @Test
    void describesAMoveByHowManyStagesMoved() {
        Step step = Step.between(Map.of(false, frozen("a", 0, 0, "b", 0, 1, "c", 0, 2)),
                Map.of(false, frozen("a", 1, 0, "b", 1, 1, "c", 0, 2)));

        assertEquals(Kind.MOVE, step.kind());
        assertEquals(2, step.count());
    }

    @Test
    void describesPlacingFromTheTray() {
        Step step = Step.between(Map.of(false, frozen("a", 0, 0)),
                Map.of(false, frozen("a", 0, 0, "b", 4, 4)));

        assertEquals(Kind.PLACE, step.kind());
        assertEquals(1, step.count());
    }

    @Test
    void describesTakingStagesOffTheMap() {
        Step step = Step.between(Map.of(false, frozen("a", 0, 0, "b", 1, 0, "c", 2, 0)),
                Map.of(false, frozen("a", 0, 0)));

        assertEquals(Kind.REMOVE, step.kind());
        assertEquals(2, step.count());
    }

    @Test
    void countsAcrossBothTrees() {
        Step step = Step.between(
                Map.of(false, frozen("a", 0, 0), true, frozen("x", 0, 0)),
                Map.of(false, frozen("a", 1, 0), true, frozen("x", 1, 0)));

        assertEquals(Kind.MOVE, step.kind());
        assertEquals(2, step.count());
    }

    @Test
    void undoAppliesEveryTreeTheStepTouched() {
        FakeLayout layout = new FakeLayout(frozen("a", 0, 0));
        layout.trees.put(true, frozen("x", 0, 0));
        GraphLayoutHistory history = new GraphLayoutHistory();
        history.record(Step.between(
                Map.of(false, frozen("a", 0, 0), true, frozen("x", 0, 0)),
                Map.of(false, frozen("a", 1, 0), true, frozen("x", 1, 0))));
        layout.trees.put(false, frozen("a", 1, 0));
        layout.trees.put(true, frozen("x", 1, 0));

        history.undo(layout);

        assertEquals(frozen("a", 0, 0), layout.current(false));
        assertEquals(frozen("x", 0, 0), layout.current(true));
    }
}
