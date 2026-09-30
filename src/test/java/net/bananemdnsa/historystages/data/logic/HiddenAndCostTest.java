package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenAndCostTest {

    private static final StageScope G = StageScope.GLOBAL;
    private static final StageStateView NONE = StageStateView.NONE_UNLOCKED;

    private static List<LogicBlock> blocks(String json) {
        return LogicCodec.read(JsonParser.parseString(json).getAsJsonArray());
    }

    private static StageStateView has(String... ids) {
        return StageStateView.of(Set.of(ids));
    }

    // ---- hidden_while -----------------------------------------------------------------

    @Test
    void hiddenDefaultsToAnonymousWhileTheConditionHolds() {
        List<LogicBlock> b = blocks("""
                [ { "type": "hidden_while", "condition": { "not": { "unlocked": "nether" } } } ]""");
        assertEquals(StageLogic.Visibility.ANONYMOUS, StageLogic.visibility(b, G, NONE, NONE));
        assertEquals(StageLogic.Visibility.VISIBLE, StageLogic.visibility(b, G, has("nether"), NONE));
    }

    @Test
    void vanishWinsOverAnonymous() {
        List<LogicBlock> b = blocks("""
                [ { "type": "hidden_while", "mode": "anonymous", "condition": { "unlocked": "a" } },
                  { "type": "hidden_while", "mode": "vanish", "condition": { "unlocked": "b" } } ]""");
        assertEquals(StageLogic.Visibility.ANONYMOUS, StageLogic.visibility(b, G, has("a"), NONE));
        assertEquals(StageLogic.Visibility.VANISH, StageLogic.visibility(b, G, has("a", "b"), NONE));
    }

    @Test
    void hiddenDoesNotBlock() {
        List<LogicBlock> b = blocks("""
                [ { "type": "hidden_while", "condition": { "not": { "unlocked": "nether" } } } ]""");
        assertTrue(StageLogic.blocked(b, G, NONE, NONE).isEmpty(), "hidden is only visibility");
    }

    @Test
    void anUnknownModeFallsBackToAnonymous() {
        List<LogicBlock> b = blocks("""
                [ { "type": "hidden_while", "mode": "sparkly", "condition": { "unlocked": "a" } } ]""");
        assertEquals(StageLogic.Visibility.ANONYMOUS, StageLogic.visibility(b, G, has("a"), NONE));
    }

    // ---- cost_while -------------------------------------------------------------------

    @Test
    void missingPercentagesMeanUnchanged() {
        List<LogicBlock> b = blocks("""
                [ { "type": "cost_while", "time": 50, "condition": { "unlocked": "a" } } ]""");
        StageLogic.CostFactors f = StageLogic.cost(b, G, has("a"), NONE);
        assertEquals(0.5, f.time(), 1e-9);
        assertEquals(1.0, f.items(), 1e-9);
        assertEquals(1.0, f.xp(), 1e-9);
        assertEquals(1, f.active().size());
    }

    @Test
    void holdingBlocksMultiplyAndInactiveOnesAreListedAsHints() {
        List<LogicBlock> b = blocks("""
                [ { "type": "cost_while", "time": 50, "items": 50, "condition": { "unlocked": "a" } },
                  { "type": "cost_while", "time": 200, "xp": 0, "condition": { "unlocked": "b" } },
                  { "type": "cost_while", "items": 10, "condition": { "unlocked": "c" } } ]""");
        StageLogic.CostFactors f = StageLogic.cost(b, G, has("a", "b"), NONE);
        assertEquals(1.0, f.time(), 1e-9, "50 % and 200 % cancel out");
        assertEquals(0.5, f.items(), 1e-9);
        assertEquals(0.0, f.xp(), 1e-9, "0 % means free");
        assertEquals(2, f.active().size());
        assertEquals(1, f.inactive().size());
    }

    @Test
    void percentagesAreClampedToTheAllowedRange() {
        List<LogicBlock> b = blocks("""
                [ { "type": "cost_while", "time": -30, "items": 99999, "condition": { "unlocked": "a" } } ]""");
        StageLogic.CostFactors f = StageLogic.cost(b, G, has("a"), NONE);
        assertEquals(0.0, f.time(), 1e-9);
        assertEquals(10.0, f.items(), 1e-9);
    }

    @Test
    void noCostBlocksMeansNeutral() {
        assertTrue(StageLogic.cost(List.of(), G, NONE, NONE).isNeutral());
    }

    @Test
    void scaledCountsRoundUpAndOnlyZeroPercentIsFree() {
        assertEquals(12, LogicBlockParams.scale(16, 0.75));
        assertEquals(2, LogicBlockParams.scale(3, 0.5));
        assertEquals(1, LogicBlockParams.scale(1, 0.01), "never rounds down to nothing");
        assertEquals(0, LogicBlockParams.scale(5, 0.0));
        assertEquals(24, LogicBlockParams.scale(16, 1.5));
    }

    @Test
    void paramsSurviveARoundTrip() {
        var in = JsonParser.parseString("""
                [ { "type": "cost_while", "time": 50, "items": 100, "xp": 75, "condition": { "unlocked": "a" } },
                  { "type": "hidden_while", "mode": "vanish", "condition": { "unlocked": "b" } } ]""").getAsJsonArray();
        assertEquals(in, LogicCodec.write(LogicCodec.read(in)));
    }
}
