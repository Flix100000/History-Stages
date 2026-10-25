package net.bananemdnsa.historystages.client.editor.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicDraftTest {

    private static JsonArray json(String s) {
        return JsonParser.parseString(s).getAsJsonArray();
    }

    @Test
    void anEditorShapedFileRoundTripsUnchanged() {
        JsonArray in = json("""
                [ { "type": "blocked_while", "condition": { "all": [
                    { "all": [ { "unlocked": "fire_1" }, { "not": { "unlocked": "fire_3" } } ] },
                    { "any": [ { "unlocked": "world", "scope": "global" }, { "unlocked": "x" } ] } ] } } ]""");
        assertEquals(in, LogicDraft.read(in).write());
    }

    @Test
    void readOnlyBlocksAreWrittenBackVerbatim() {
        JsonArray in = json("""
                [ { "type": "future_type", "condition": { "unlocked": "a" }, "extra": 1 },
                  { "type": "blocked_while", "condition": { "any": [ { "all": [ { "any": [ { "unlocked": "deep" } ] } ] } ] } } ]""");
        LogicDraft draft = LogicDraft.read(in);
        assertFalse(draft.blocks.get(0).editable(), "unknown type");
        assertFalse(draft.blocks.get(1).editable(), "tree deeper than groups");
        assertEquals(in, draft.write());
    }

    @Test
    void editsAreWrittenAndExtraFieldsOnTheBlockSurvive() {
        LogicDraft draft = LogicDraft.read(json("""
                [ { "type": "blocked_while", "condition": { "unlocked": "a" }, "note": "keep" } ]"""));
        draft.blocks.get(0).groups.get(0).terms.add(new LogicDraft.Term("b", StageScope.GLOBAL, false));
        JsonArray out = draft.write();
        assertEquals("keep", out.get(0).getAsJsonObject().get("note").getAsString());
        assertEquals(json("""
                [ { "type": "blocked_while", "note": "keep", "condition": { "all": [ { "all": [
                    { "unlocked": "a" }, { "not": { "unlocked": "b", "scope": "global" } } ] } ] } } ]"""), out);
    }

    @Test
    void aBlockWithoutAnyPickedStageIsNotSaved() {
        LogicDraft draft = new LogicDraft();
        LogicDraft.Block block = LogicDraft.Block.newBlock(LogicBlockTypes.BLOCKED_WHILE);
        block.groups.get(0).terms.add(new LogicDraft.Term(null, null, true));
        draft.blocks.add(block);
        assertTrue(block.isEmpty());
        assertNull(draft.write(), "an empty condition would block the stage forever");
    }

    @Test
    void emptyGroupsAndBlankTermsAreDroppedFromAnOtherwiseFilledBlock() {
        LogicDraft draft = new LogicDraft();
        LogicDraft.Block block = LogicDraft.Block.newBlock(LogicBlockTypes.BLOCKED_WHILE);
        block.groups.get(0).terms.add(new LogicDraft.Term("a", null, true));
        block.groups.get(0).terms.add(new LogicDraft.Term("", null, true));
        block.groups.add(new LogicDraft.Group(false));
        draft.blocks.add(block);
        assertEquals(json("""
                [ { "type": "blocked_while", "condition": { "all": [ { "all": [ { "unlocked": "a" } ] } ] } } ]"""),
                draft.write());
    }

    @Test
    void editedSettingsAreWrittenAndDefaultsStayOutOfTheFile() {
        LogicDraft draft = LogicDraft.read(json("""
                [ { "type": "cost_while", "time": 50, "condition": { "unlocked": "a" } },
                  { "type": "hidden_while", "condition": { "unlocked": "b" } } ]"""));
        LogicDraft.Block cost = draft.blocks.get(0);
        LogicDraft.Block hidden = draft.blocks.get(1);
        assertEquals(50, cost.percent("time"));
        assertEquals(100, cost.percent("items"));
        cost.setPercent("time", 100);
        cost.setPercent("xp", 75);
        hidden.setHiddenMode(net.bananemdnsa.historystages.data.logic.LogicBlockParams.HiddenMode.VANISH);
        JsonArray out = draft.write();
        assertFalse(out.get(0).getAsJsonObject().has("time"), "100 % is the default and is left out");
        assertEquals(75, out.get(0).getAsJsonObject().get("xp").getAsInt());
        assertEquals("vanish", out.get(1).getAsJsonObject().get("mode").getAsString());
    }
}
