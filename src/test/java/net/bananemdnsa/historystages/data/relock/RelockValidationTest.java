package net.bananemdnsa.historystages.data.relock;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.auto.TriggerTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelockValidationTest {

    /** type() must report the registered name; the built-in classes hard-code their own. */
    static final class GlobalOnly implements TriggerCondition {
        @Override public String type() { return "mymod:x"; }
        @Override public long signature() { return 1L; }
    }

    @BeforeEach
    @AfterEach
    void reset() { TriggerTypes.resetForTesting(); }

    @Test void lockTriggerTypeOutsideTheStageScope() {
        TriggerTypes.register("mymod:x", GlobalOnly.class, StageScope.GLOBAL);
        String json = "{\"mode\":\"auto\",\"lock_trigger\":{\"triggers\":[{\"type\":\"mymod:x\"}]}}";
        assertEquals(1, p(json, StageScope.INDIVIDUAL).size());
        assertEquals(List.of(), p(json, StageScope.GLOBAL));
    }

    @Test void globalConditionalWithoutAutoTriggerStillWarnsAboutDependencies() {
        assertEquals(1, p("""
                {"mode":"conditional",
                 "dependencies":[{"items":[{"id":"minecraft:dirt","count":1}]}]}""", StageScope.GLOBAL).size());
    }

    private static StageEntry e(String json) { return new Gson().fromJson(json, StageEntry.class); }

    private static List<String> p(String json, StageScope scope) {
        return RelockValidation.problems("s", e(json), scope);
    }

    @Test void cleanStagesHaveNoProblems() {
        assertEquals(List.of(), p("{\"mode\":\"auto\"}", StageScope.GLOBAL));
        assertEquals(List.of(), p("""
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"weather","state":"rain"}]}}""",
                StageScope.GLOBAL));
        assertEquals(List.of(), p("""
                {"mode":"auto","lock_trigger":{"triggers":[
                  {"type":"entity","id":"minecraft:villager"},
                  {"type":"effect","id":"minecraft:speed","negate":true}]}}""", StageScope.INDIVIDUAL));
    }

    @Test void eventInConditional() {
        List<String> out = p("""
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"entity","id":"minecraft:zombie"}]}}""",
                StageScope.INDIVIDUAL);
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("'entity'"));
    }

    @Test void playerStateInGlobalConditional() {
        assertEquals(1, p("""
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"dimension","id":"minecraft:the_nether"}]}}""",
                StageScope.GLOBAL).size());
    }

    @Test void negateInConditional() {
        assertEquals(1, p("""
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"weather","state":"rain","negate":true}]}}""",
                StageScope.GLOBAL).size());
    }

    @Test void lockTriggerOnWrongMode() {
        for (String mode : new String[]{"external", "temporary", "conditional"}) {
            assertEquals(1, p("{\"mode\":\"" + mode + "\",\"lock_trigger\":{\"triggers\":[{\"type\":\"entity\",\"id\":\"minecraft:zombie\"}]}}",
                    StageScope.GLOBAL).size(), mode);
        }
    }

    @Test void negatedEventAndNegatedPlayerStateOnGlobal() {
        assertEquals(1, p("""
                {"mode":"auto","lock_trigger":{"triggers":[{"type":"entity","id":"minecraft:zombie","negate":true}]}}""",
                StageScope.INDIVIDUAL).size());
        assertEquals(1, p("""
                {"mode":"auto","lock_trigger":{"triggers":[{"type":"dimension","id":"minecraft:the_nether","negate":true}]}}""",
                StageScope.GLOBAL).size());
    }

    @Test void globalConditionalWithNonStageDependencies() {
        assertEquals(1, p("""
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"weather","state":"rain"}]},
                 "dependencies":[{"items":[{"id":"minecraft:dirt","count":1}]}]}""", StageScope.GLOBAL).size());
    }

    @Test void globalConditionalWithRecipesWarnsAboutReloads() {
        String json = """
                {"mode":"conditional","auto_trigger":{"triggers":[{"type":"weather","state":"rain"}]},
                 "recipes":["minecraft:torch"]}""";
        List<String> out = p(json, StageScope.GLOBAL);
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("recipe"));
        assertEquals(List.of(), p(json, StageScope.INDIVIDUAL));
    }

    @Test void lockTriggerShapeProblems() {
        List<String> unknownKey = p("""
                {"mode":"auto","lock_trigger":{"triggers":[],"re_unlocable":true}}""", StageScope.GLOBAL);
        assertEquals(1, unknownKey.size());
        assertTrue(unknownKey.get(0).contains("'re_unlocable'"));

        List<String> badMode = p("""
                {"mode":"auto","lock_trigger":{"mode":"every","triggers":[{"type":"entity","id":"minecraft:zombie"}]}}""",
                StageScope.GLOBAL);
        assertEquals(1, badMode.size());
        assertTrue(badMode.get(0).contains("'every'"));

        List<String> notObject = p("{\"mode\":\"auto\",\"lock_trigger\":\"soon\"}", StageScope.GLOBAL);
        assertEquals(1, notObject.size());
        assertTrue(notObject.get(0).contains("not an object"));
    }

    @Test void negateInAutoTriggerOfAutoAndTemporary() {
        for (String mode : new String[]{"auto", "temporary"}) {
            List<String> out = p("{\"mode\":\"" + mode
                    + "\",\"auto_trigger\":{\"triggers\":[{\"type\":\"effect\",\"id\":\"minecraft:speed\",\"negate\":true}]}}",
                    StageScope.INDIVIDUAL);
            assertEquals(1, out.size(), mode);
            assertTrue(out.get(0).contains("negate"), mode);
        }
    }

    /** "negate": 1 is not read as a boolean at all: no negation, and one warning naming the value. */
    @Test void nonBooleanNegateAndReUnlockableAreWarnedAndIgnored() {
        StageEntry entry = e("""
                {"mode":"auto","lock_trigger":{"re_unlockable":"yes","triggers":[
                  {"type":"effect","id":"minecraft:speed","negate":1}]}}""");
        assertFalse(entry.getLockTrigger().getTriggers().get(0) instanceof net.bananemdnsa.historystages.data.auto.NegatedTrigger);
        assertNull(entry.getLockTrigger().getRawReUnlockable());
        assertFalse(entry.getLockTrigger().isReUnlockable());
        List<String> out = RelockValidation.problems("s", entry, StageScope.INDIVIDUAL);
        assertEquals(2, out.size(), out.toString());
        assertTrue(out.stream().anyMatch(m -> m.contains("\"re_unlockable\": \"yes\"")), out.toString());
        assertTrue(out.stream().anyMatch(m -> m.contains("\"negate\": 1")), out.toString());
    }
}
