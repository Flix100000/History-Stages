package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageLogicTest {

    private static final StageScope G = StageScope.GLOBAL;
    private static final StageScope I = StageScope.INDIVIDUAL;
    private static final StageStateView NONE = StageStateView.NONE_UNLOCKED;

    private static List<LogicBlock> blocks(String json) {
        return LogicCodec.read(JsonParser.parseString(json).getAsJsonArray());
    }

    private static StageStateView has(String... ids) {
        return StageStateView.of(Set.of(ids));
    }

    /** The ice entry of the mage example: blocked while fire started and fire not mastered. */
    private static final String ICE_ENTRY = """
            [ { "type": "blocked_while",
                "condition": { "all": [ { "unlocked": "fire_1" },
                                        { "not": { "unlocked": "fire_3" } } ] } } ]""";

    @Test
    void noBlocksMeansNeverBlocked() {
        assertTrue(StageLogic.blocked(List.of(), G, NONE, NONE).isEmpty());
    }

    @Test
    void magePathFollowsTheExample() {
        List<LogicBlock> ice = blocks(ICE_ENTRY);
        assertFalse(StageLogic.blocked(ice, G, NONE, NONE).isBlocked(), "nothing started yet");
        assertTrue(StageLogic.blocked(ice, G, has("fire_1"), NONE).isBlocked(), "fire started");
        assertFalse(StageLogic.blocked(ice, G, has("fire_1", "fire_2", "fire_3"), NONE).isBlocked(),
                "fire mastered");
        assertFalse(StageLogic.blocked(ice, G, has("fire_3"), NONE).isBlocked(),
                "order does not matter: mastered without the entry is not 'started'");
    }

    @Test
    void anyAndNotCombine() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while",
                    "condition": { "any": [ { "unlocked": "a" }, { "not": { "unlocked": "b" } } ] } } ]""");
        assertTrue(StageLogic.blocked(b, G, NONE, NONE).isBlocked(), "b missing");
        assertFalse(StageLogic.blocked(b, G, has("b"), NONE).isBlocked());
        assertTrue(StageLogic.blocked(b, G, has("a", "b"), NONE).isBlocked(), "a unlocked");
    }

    @Test
    void severalBlockedWhileBlocksAreOred() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while", "condition": { "unlocked": "a" } },
                  { "type": "blocked_while", "condition": { "unlocked": "b" } } ]""");
        assertFalse(StageLogic.blocked(b, G, NONE, NONE).isBlocked());
        StageLogic.Blocked byB = StageLogic.blocked(b, G, has("b"), NONE);
        assertTrue(byB.isBlocked());
        assertEquals(1, byB.matching().size(), "only the block that holds is reported");
    }

    @Test
    void aMissingStageCountsAsNotUnlocked() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while", "condition": { "not": { "unlocked": "gone" } } } ]""");
        assertTrue(StageLogic.blocked(b, G, NONE, NONE).isBlocked());
    }

    @Test
    void anIndividualStageReadsBothScopes() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while",
                    "condition": { "all": [ { "unlocked": "world_age", "scope": "global" },
                                            { "unlocked": "my_path" } ] } } ]""");
        assertTrue(StageLogic.blocked(b, I, has("world_age"), has("my_path")).isBlocked());
        assertFalse(StageLogic.blocked(b, I, NONE, has("my_path")).isBlocked(),
                "the global term is read from the global state");
        assertFalse(StageLogic.blocked(b, I, has("world_age", "my_path"), NONE).isBlocked(),
                "an unscoped term on an individual stage reads the individual state");
    }

    @Test
    void aGlobalStageNeverReadsIndividualState() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while", "condition": { "unlocked": "x", "scope": "individual" } } ]""");
        assertFalse(StageLogic.blocked(b, G, has("x"), has("x")).isBlocked(),
                "a global stage has no player to ask; the term is false");
    }

    @Test
    void unknownBlockTypesAndNodesNeverBlock() {
        List<LogicBlock> b = blocks("""
                [ { "type": "revoke_when", "condition": { "unlocked": "a" } },
                  { "type": "blocked_while", "condition": { "future_term": 3 } } ]""");
        assertFalse(StageLogic.blocked(b, G, has("a"), NONE).isBlocked());
    }

    @Test
    void anEmptyAllIsTrueAndAnEmptyAnyIsFalse() {
        // An empty group in the editor must not quietly block a stage forever, so the editor
        // never writes one; this pins what the evaluator does if a hand-written file has it.
        assertTrue(Condition.evaluate(new Condition.All(List.of()), G, NONE, NONE));
        assertFalse(Condition.evaluate(new Condition.Any(List.of()), G, NONE, NONE));
    }

    @Test
    void referencedStagesAreCollected() {
        List<LogicBlock> b = blocks(ICE_ENTRY);
        assertEquals(Set.of("fire_1", "fire_3"), Set.copyOf(StageLogic.referencedStages(b)));
    }

    @Test
    void readingNullOrGarbageGivesNoBlocks() {
        assertTrue(LogicCodec.read(null).isEmpty());
        JsonArray junk = JsonParser.parseString("[ 3, \"x\", null ]").getAsJsonArray();
        assertEquals(3, LogicCodec.read(junk).size(), "kept as unknown blocks so they survive a save");
        assertFalse(StageLogic.blocked(LogicCodec.read(junk), G, NONE, NONE).isBlocked());
    }
}
