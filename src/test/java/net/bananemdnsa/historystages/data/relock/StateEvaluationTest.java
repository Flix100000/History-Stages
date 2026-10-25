package net.bananemdnsa.historystages.data.relock;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.StateView;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateEvaluationTest {

    private static final StateView NETHER_NIGHT = StateView.builder()
            .dimension("minecraft:the_nether").dayTime(18000L).weather(false, false).build();

    private static AutoTrigger at(String mode, TriggerCondition... t) { return new AutoTrigger(mode, List.of(t)); }

    @Test void stateUsableRules() {
        assertTrue(TriggerRules.stateUsable("dimension", StageScope.INDIVIDUAL));
        assertFalse(TriggerRules.stateUsable("dimension", StageScope.GLOBAL));
        assertTrue(TriggerRules.stateUsable("weather", StageScope.GLOBAL));
        assertTrue(TriggerRules.stateUsable("weather", StageScope.INDIVIDUAL));
        assertFalse(TriggerRules.stateUsable("entity", StageScope.INDIVIDUAL));
        assertFalse(TriggerRules.stateUsable("advancement", StageScope.GLOBAL));
    }

    @Test void anyAndAll() {
        DimensionTrigger nether = new DimensionTrigger("minecraft:the_nether");
        TimeOfDayTrigger day = TimeOfDayTrigger.custom(0, 12000);
        assertTrue(StateEvaluation.conditionHolds(at("any", nether, day), NETHER_NIGHT, StageScope.INDIVIDUAL));
        assertFalse(StateEvaluation.conditionHolds(at("all", nether, day), NETHER_NIGHT, StageScope.INDIVIDUAL));
        assertTrue(StateEvaluation.conditionHolds(at("all", nether, TimeOfDayTrigger.custom(13000, 23000)),
                NETHER_NIGHT, StageScope.INDIVIDUAL));
    }

    @Test void unusableTriggersAreIgnoredNotCounted() {
        // An entity trigger in a conditional stage is warned about at load and ignored here:
        // it neither opens the stage (ANY) nor blocks it forever (ALL).
        EntityTrigger kill = new EntityTrigger("minecraft:zombie", null);
        DimensionTrigger nether = new DimensionTrigger("minecraft:the_nether");
        assertTrue(StateEvaluation.conditionHolds(at("all", kill, nether), NETHER_NIGHT, StageScope.INDIVIDUAL));
        assertFalse(StateEvaluation.conditionHolds(at("any", kill), NETHER_NIGHT, StageScope.INDIVIDUAL));
        assertFalse(StateEvaluation.conditionHolds(at("all", kill), NETHER_NIGHT, StageScope.INDIVIDUAL));
    }

    @Test void globalIgnoresPlayerStates() {
        DimensionTrigger nether = new DimensionTrigger("minecraft:the_nether");
        assertFalse(StateEvaluation.conditionHolds(at("any", nether), NETHER_NIGHT, StageScope.GLOBAL));
        assertTrue(StateEvaluation.conditionHolds(at("any", nether, new WeatherTrigger("clear")),
                NETHER_NIGHT, StageScope.GLOBAL));
    }

    @Test void negationsInAConditionalAreIgnored() {
        NegatedTrigger notOverworld = new NegatedTrigger(new DimensionTrigger("minecraft:overworld"));
        assertFalse(StateEvaluation.conditionHolds(at("any", notOverworld), NETHER_NIGHT, StageScope.INDIVIDUAL));
    }

    @Test void emptyNeverHolds() {
        assertFalse(StateEvaluation.conditionHolds(new AutoTrigger(), NETHER_NIGHT, StageScope.INDIVIDUAL));
        assertFalse(StateEvaluation.conditionHolds(null, NETHER_NIGHT, StageScope.INDIVIDUAL));
    }

    @Test void firingNegations() {
        NegatedTrigger noFire = new NegatedTrigger(new EffectTrigger("minecraft:fire_resistance"));
        NegatedTrigger notNether = new NegatedTrigger(new DimensionTrigger("minecraft:the_nether"));
        LockTrigger lt = new LockTrigger("any", List.of(noFire, notNether, new EntityTrigger("minecraft:villager", null)), null);
        assertEquals(List.of(noFire), StateEvaluation.firingNegations(lt, NETHER_NIGHT, StageScope.INDIVIDUAL));
        // Global: player states may not be negated, so nothing fires.
        assertEquals(List.of(), StateEvaluation.firingNegations(lt, NETHER_NIGHT, StageScope.GLOBAL));
        StateView withFire = StateView.builder().dimension("minecraft:the_nether")
                .effects(Set.of("minecraft:fire_resistance")).build();
        assertEquals(List.of(), StateEvaluation.firingNegations(lt, withFire, StageScope.INDIVIDUAL));
    }
}
