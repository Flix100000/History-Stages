package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevokeTest {

    private static final StageScope G = StageScope.GLOBAL;
    private static final StageStateView NONE = StageStateView.NONE_UNLOCKED;

    private static List<LogicBlock> blocks(String json) {
        return LogicCodec.read(JsonParser.parseString(json).getAsJsonArray());
    }

    private static StageStateView has(String... ids) {
        return StageStateView.of(Set.of(ids));
    }

    private static final String LIGHT = """
            [ { "type": "revoke_when", "condition": { "unlocked": "shadow_pact" } } ]""";

    @Test
    void firesOnTheJumpFromFalseToTrue() {
        List<LogicBlock> b = blocks(LIGHT);
        assertTrue(StageLogic.revokeFires(b, G, NONE, NONE, has("shadow_pact"), NONE));
    }

    @Test
    void doesNotFireWhenTheConditionAlreadyHeld() {
        // Once only: re-researching the light path while the pact stands must not lose it again.
        List<LogicBlock> b = blocks(LIGHT);
        assertFalse(StageLogic.revokeFires(b, G, has("shadow_pact"), NONE, has("shadow_pact", "x"), NONE));
    }

    @Test
    void doesNotFireWhenTheConditionTurnsFalse() {
        List<LogicBlock> b = blocks(LIGHT);
        assertFalse(StageLogic.revokeFires(b, G, has("shadow_pact"), NONE, NONE, NONE));
    }

    @Test
    void aLockedStageCanTriggerToo() {
        List<LogicBlock> b = blocks("""
                [ { "type": "revoke_when", "condition": { "not": { "unlocked": "anchor" } } } ]""");
        assertTrue(StageLogic.revokeFires(b, G, has("anchor"), NONE, NONE, NONE),
                "losing the anchor stage is a change like any other");
    }

    @Test
    void otherBlockTypesNeverRevoke() {
        List<LogicBlock> b = blocks("""
                [ { "type": "blocked_while", "condition": { "unlocked": "shadow_pact" } } ]""");
        assertFalse(StageLogic.revokeFires(b, G, NONE, NONE, has("shadow_pact"), NONE));
    }

    @Test
    void referencesAreListedForTheHandler() {
        List<LogicBlock> b = blocks("""
                [ { "type": "revoke_when", "condition": { "all": [ { "unlocked": "a" }, { "unlocked": "g", "scope": "global" } ] } },
                  { "type": "blocked_while", "condition": { "unlocked": "z" } } ]""");
        assertEquals(Set.of("a"), Set.copyOf(StageLogic.revokeReferences(b, StageScope.INDIVIDUAL, StageScope.INDIVIDUAL)));
        assertEquals(Set.of("g"), Set.copyOf(StageLogic.revokeReferences(b, StageScope.INDIVIDUAL, StageScope.GLOBAL)));
    }

    @Test
    void stateWithAChangeAppliedAnswersForThatStageOnly() {
        StageStateView base = has("a");
        StageStateView plusB = StageLogic.withChange(base, "b", true);
        StageStateView minusA = StageLogic.withChange(base, "a", false);
        assertTrue(plusB.isUnlocked("a") && plusB.isUnlocked("b"));
        assertFalse(minusA.isUnlocked("a"));
    }
}
