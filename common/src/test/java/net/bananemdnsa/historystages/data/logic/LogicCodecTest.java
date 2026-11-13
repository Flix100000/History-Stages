package net.bananemdnsa.historystages.data.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicCodecTest {

    private static JsonArray json(String s) {
        return JsonParser.parseString(s).getAsJsonArray();
    }

    @Test
    void aKnownBlockRoundTrips() {
        JsonArray in = json("""
                [ { "type": "blocked_while",
                    "condition": { "all": [ { "unlocked": "a" },
                                            { "not": { "unlocked": "b", "scope": "global" } },
                                            { "any": [ { "unlocked": "c" } ] } ] } } ]""");
        assertEquals(in, LogicCodec.write(LogicCodec.read(in)));
    }

    @Test
    void unknownBlocksAndNodesSurviveASaveUnchanged() {
        JsonArray in = json("""
                [ { "type": "revoke_when", "condition": { "unlocked": "a" }, "extra": [1, 2] },
                  { "type": "blocked_while", "condition": { "all": [ { "future_term": { "x": 1 } },
                                                                     { "unlocked": "b" } ] } },
                  42 ]""");
        assertEquals(in, LogicCodec.write(LogicCodec.read(in)));
    }

    @Test
    void extraFieldsOnAKnownBlockSurvive() {
        // A newer version may add a field to a block type this one knows; it must not be lost.
        JsonArray in = json("""
                [ { "type": "blocked_while", "condition": { "unlocked": "a" }, "note": "keep me" } ]""");
        assertEquals(in, LogicCodec.write(LogicCodec.read(in)));
    }

    @Test
    void flatGroupsMapToATreeAndBack() {
        LogicGroups groups = new LogicGroups(List.of(
                new LogicGroups.Group(true, List.of(
                        new LogicGroups.Term("fire_1", null, true),
                        new LogicGroups.Term("fire_3", null, false))),
                new LogicGroups.Group(false, List.of(
                        new LogicGroups.Term("world", StageScope.GLOBAL, true)))));
        Condition tree = groups.toCondition();
        Optional<LogicGroups> back = LogicGroups.fromCondition(tree);
        assertTrue(back.isPresent());
        assertEquals(groups, back.get());
    }

    @Test
    void aTreeTheEditorCannotShowIsReportedAsNotFlat() {
        Condition deep = new Condition.Any(List.of(
                new Condition.All(List.of(new Condition.Any(List.of(new Condition.Unlocked("a", null)))))));
        assertTrue(LogicGroups.fromCondition(deep).isEmpty());
    }

    @Test
    void aSingleTermIsShownAsOneGroup() {
        // Hand-written files often skip the wrapping; the editor should still open them.
        Optional<LogicGroups> g = LogicGroups.fromCondition(new Condition.Unlocked("a", null));
        assertTrue(g.isPresent());
        assertEquals(1, g.get().groups().size());
        assertEquals(1, g.get().groups().get(0).terms().size());
    }
}
