package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicReferencesTest {

    private static List<LogicBlock> blocks(String json) {
        return LogicCodec.read(JsonParser.parseString(json).getAsJsonArray());
    }

    private static List<LogicReferences.Kind> kinds(String owner, StageScope scope, String json) {
        return LogicReferences.problems(owner, scope, blocks(json), Set.of("g1", "g2"), Set.of("i1"))
                .stream().map(LogicReferences.Problem::kind).toList();
    }

    @Test
    void cleanReferencesReportNothing() {
        assertTrue(kinds("i1x", StageScope.INDIVIDUAL, """
                [ { "type": "blocked_while", "condition": { "all": [
                    { "unlocked": "g1", "scope": "global" }, { "unlocked": "i1" } ] } } ]""").isEmpty());
    }

    @Test
    void eachProblemIsNamed() {
        assertEquals(List.of(LogicReferences.Kind.MISSING), kinds("g1", StageScope.GLOBAL, """
                [ { "type": "blocked_while", "condition": { "unlocked": "nope" } } ]"""));
        assertEquals(List.of(LogicReferences.Kind.SCOPE), kinds("g1", StageScope.GLOBAL, """
                [ { "type": "blocked_while", "condition": { "unlocked": "i1", "scope": "individual" } } ]"""));
        assertEquals(List.of(LogicReferences.Kind.SELF), kinds("g1", StageScope.GLOBAL, """
                [ { "type": "blocked_while", "condition": { "unlocked": "g1" } } ]"""));
        assertEquals(List.of(LogicReferences.Kind.UNKNOWN_TYPE), kinds("g1", StageScope.GLOBAL, """
                [ { "type": "future_type", "condition": { "unlocked": "g2" } } ]"""));
        assertEquals(List.of(LogicReferences.Kind.UNKNOWN_NODE), kinds("g1", StageScope.GLOBAL, """
                [ { "type": "blocked_while", "condition": { "all": [ { "weird": 1 }, { "unlocked": "g2" } ] } } ]"""));
    }

    @Test
    void anIndividualStageMayShareAnIdWithAGlobalOne() {
        // Same id, other scope: not a self-reference.
        assertTrue(kinds("g1", StageScope.INDIVIDUAL, """
                [ { "type": "blocked_while", "condition": { "unlocked": "g1", "scope": "global" } } ]""").isEmpty());
    }
}
