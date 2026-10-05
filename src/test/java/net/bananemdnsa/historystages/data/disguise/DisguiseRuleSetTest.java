package net.bananemdnsa.historystages.data.disguise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisguiseRuleSetTest {

    @Test
    void parsesItemAndTagRulesWithDefaults() {
        DisguiseRuleSet set = DisguiseRuleSet.fromJson("""
            { "minecraft:emerald_ore": { "as": "minecraft:stone", "drops": "disguise", "hints": false },
              "#c:ores/diamond": { "as": "minecraft:deepslate" } }""").set();

        DisguiseRule ore = set.itemRule("minecraft:emerald_ore");
        assertEquals("minecraft:stone", ore.as());
        assertEquals(DropsMode.DISGUISE, ore.drops());
        assertFalse(ore.hints());

        DisguiseRule tag = set.tagRule("c:ores/diamond");
        assertEquals("minecraft:deepslate", tag.as());
        assertEquals(DropsMode.REAL, tag.drops());
        assertTrue(tag.hints());
        assertTrue(tag.isTag());
    }

    @Test
    void rejectsModKeysMissingTargetAndSelfDisguise() {
        DisguiseRuleSet.Parsed p = DisguiseRuleSet.fromJson("""
            { "@create": { "as": "minecraft:stone" },
              "minecraft:dirt": { },
              "minecraft:gravel": { "as": "minecraft:gravel" } }""");

        assertTrue(p.set().isEmpty());
        assertEquals(3, p.problems().size());
    }

    @Test
    void nbtOnTagKeyIsDroppedWithProblem() {
        DisguiseRuleSet.Parsed p = DisguiseRuleSet.fromJson("""
            { "#c:ores": { "as": "minecraft:stone", "nbt": { "a": 1 } } }""");

        assertNull(p.set().tagRule("c:ores").nbt());
        assertEquals(1, p.problems().size());
    }

    @Test
    void rejectsEveryMemberOfAnItemKeyCycle() {
        DisguiseRuleSet.Parsed p = DisguiseRuleSet.fromJson("""
            { "a:x": { "as": "a:y" }, "a:y": { "as": "a:x" }, "a:z": { "as": "a:x" } }""");

        assertNull(p.set().itemRule("a:x"));
        assertNull(p.set().itemRule("a:y"));
        assertNotNull(p.set().itemRule("a:z"));
        assertFalse(p.problems().isEmpty());
    }

    @Test
    void roundTripIsSortedAndOmitsDefaults() {
        String json = DisguiseRuleSet.toJson(DisguiseRuleSet.fromJson("""
            { "b:b": { "as": "a:a" }, "a:a": { "as": "c:c", "drops": "disguise" } }""").set());

        assertTrue(json.indexOf("\"a:a\"") < json.indexOf("\"b:b\""));
        assertFalse(json.contains("hints"));
        assertFalse(json.contains("\"real\""));

        DisguiseRuleSet again = DisguiseRuleSet.fromJson(json).set();
        assertEquals(DropsMode.DISGUISE, again.itemRule("a:a").drops());
        assertEquals("a:a", again.itemRule("b:b").as());
    }

    @Test
    void wouldCycleSeesEditsBeforeTheyHappen() {
        DisguiseRuleSet set = DisguiseRuleSet.fromJson("{ \"a:x\": { \"as\": \"a:y\" } }").set();

        assertTrue(set.wouldCycle("a:y", "a:x"));
        assertTrue(set.wouldCycle("a:y", "a:y"));
        assertFalse(set.wouldCycle("a:y", "a:z"));
        // A tag key never takes part in a chain step, so it cannot close a loop.
        assertFalse(set.wouldCycle("#c:ores", "a:x"));
    }

    @Test
    void withRuleAndWithoutAreCopies() {
        DisguiseRuleSet empty = DisguiseRuleSet.empty();
        DisguiseRuleSet one = empty.withRule(new DisguiseRule("a:x", "a:y", DropsMode.REAL, true, null));

        assertTrue(empty.isEmpty());
        assertNotNull(one.itemRule("a:x"));
        assertTrue(one.without("a:x").isEmpty());
    }

    @Test
    void tagRulesAreSortedByKey() {
        DisguiseRuleSet set = DisguiseRuleSet.fromJson("""
            { "#c:ores/z": { "as": "a:a" }, "#c:ores": { "as": "a:b" } }""").set();

        assertEquals("#c:ores", set.tagRules().get(0).key());
        assertEquals("#c:ores/z", set.tagRules().get(1).key());
    }

    @Test
    void malformedJsonGivesEmptySetWithProblem() {
        DisguiseRuleSet.Parsed p = DisguiseRuleSet.fromJson("{ not json");

        assertTrue(p.set().isEmpty());
        assertEquals(1, p.problems().size());
    }

    @Test
    void blankInputIsEmptyWithoutProblem() {
        DisguiseRuleSet.Parsed p = DisguiseRuleSet.fromJson("");

        assertTrue(p.set().isEmpty());
        assertTrue(p.problems().isEmpty());
    }
}
