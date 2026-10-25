package net.bananemdnsa.historystages.data.auto;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NegatedTriggerJsonTest {

    private static final Gson GSON = new Gson();

    private static AutoTrigger parse(String json) {
        return GSON.fromJson(json, AutoTrigger.class);
    }

    @Test void negateTrueWrapsTheTrigger() {
        AutoTrigger at = parse("""
                {"mode":"any","triggers":[
                  {"type":"effect","id":"minecraft:fire_resistance","negate":true},
                  {"type":"entity","id":"minecraft:villager"}]}""");
        assertInstanceOf(NegatedTrigger.class, at.getTriggers().get(0));
        assertInstanceOf(EffectTrigger.class, ((NegatedTrigger) at.getTriggers().get(0)).inner());
        assertInstanceOf(EntityTrigger.class, at.getTriggers().get(1));
    }

    @Test void negateFalseOrMissingDoesNotWrap() {
        AutoTrigger at = parse("""
                {"triggers":[{"type":"effect","id":"minecraft:speed","negate":false}]}""");
        assertInstanceOf(EffectTrigger.class, at.getTriggers().get(0));
    }

    /** Gson would read "true" as true and 1 as false; only a real boolean counts. */
    @Test void nonBooleanNegateIsNotANegationAndIsReported() {
        AutoTrigger at = parse("""
                {"triggers":[{"type":"effect","id":"minecraft:speed","negate":"true"},
                             {"type":"effect","id":"minecraft:haste","negate":1}]}""");
        assertInstanceOf(EffectTrigger.class, at.getTriggers().get(0));
        assertInstanceOf(EffectTrigger.class, at.getTriggers().get(1));
        assertEquals(2, at.getReadProblems().size());
    }

    @Test void roundTripKeepsNegate() {
        AutoTrigger at = parse("""
                {"triggers":[{"type":"effect","id":"minecraft:fire_resistance","negate":true}]}""");
        JsonObject out = GSON.toJsonTree(at).getAsJsonObject();
        JsonObject t = out.getAsJsonArray("triggers").get(0).getAsJsonObject();
        assertTrue(t.get("negate").getAsBoolean());
        assertEquals("effect", t.get("type").getAsString());
        assertEquals("minecraft:fire_resistance", t.get("id").getAsString());
        assertInstanceOf(NegatedTrigger.class, parse(out.toString()).getTriggers().get(0));
    }

    @Test void unknownTypeWithNegateSurvivesVerbatim() {
        String raw = "{\"type\":\"gone:zone\",\"zone\":\"x\",\"negate\":true}";
        AutoTrigger at = parse("{\"triggers\":[" + raw + "]}");
        JsonObject out = GSON.toJsonTree(at).getAsJsonObject();
        assertEquals(JsonParser.parseString(raw), out.getAsJsonArray("triggers").get(0));
    }

    @Test void negationHasItsOwnSignatureTypeAndHolds() {
        EffectTrigger fire = new EffectTrigger("minecraft:fire_resistance");
        NegatedTrigger not = new NegatedTrigger(fire);
        assertNotEquals(fire.signature(), not.signature());
        assertEquals("effect", not.type());
        StateView withFire = StateView.builder().effects(Set.of("minecraft:fire_resistance")).build();
        StateView without = StateView.builder().build();
        assertFalse(not.holds(withFire));
        assertTrue(not.holds(without));
    }

    @Test void negatingAnEventNeverHolds() {
        assertFalse(new NegatedTrigger(new EntityTrigger("minecraft:zombie", null))
                .holds(StateView.builder().build()));
    }
}
