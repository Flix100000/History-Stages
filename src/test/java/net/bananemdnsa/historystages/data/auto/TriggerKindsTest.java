package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.trigger.TriggerKind;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerKindsTest {

    @BeforeEach @AfterEach
    void reset() { TriggerTypes.resetForTesting(); }

    @Test void builtInsHaveTheirKinds() {
        for (String t : new String[]{"biome", "structure", "dimension", "item", "effect", "xp_level"}) {
            assertEquals(TriggerKind.PLAYER_STATE, TriggerTypes.kindOf(t), t);
        }
        assertEquals(TriggerKind.WORLD_STATE, TriggerTypes.kindOf("weather"));
        assertEquals(TriggerKind.WORLD_STATE, TriggerTypes.kindOf("world_time"));
        for (String t : new String[]{"entity", "block_place", "block_break", "advancement",
                "playtime", "stat", "day_count"}) {
            assertEquals(TriggerKind.EVENT, TriggerTypes.kindOf(t), t);
        }
    }

    @Test void unknownAndPlainRegistrationsAreEvents() {
        assertEquals(TriggerKind.EVENT, TriggerTypes.kindOf("notinstalled:x"));
        TriggerTypes.register("mymod:plain", TriggerTypes.classFor("playtime"));
        assertEquals(TriggerKind.EVENT, TriggerTypes.kindOf("mymod:plain"));
    }

    @Test void aStateKindNeedsAStateTriggerClass() {
        assertThrows(IllegalArgumentException.class, () -> TriggerTypes.register(
                "mymod:bad", TriggerTypes.classFor("playtime"), TriggerKind.PLAYER_STATE, StageScope.INDIVIDUAL));
    }

    @Test void theKindOverloadWithoutScopesMeansBoth() {
        TriggerTypes.register("mymod:zone", BiomeTrigger.class, TriggerKind.PLAYER_STATE);
        assertEquals(TriggerKind.PLAYER_STATE, TriggerTypes.kindOf("mymod:zone"));
        assertEquals(java.util.Set.of(StageScope.GLOBAL, StageScope.INDIVIDUAL), TriggerTypes.scopesOf("mymod:zone"));
    }

    @Test void aDeclaredKindSticksAndResetDropsIt() {
        TriggerTypes.register("mymod:zone", BiomeTrigger.class, TriggerKind.PLAYER_STATE,
                StageScope.GLOBAL, StageScope.INDIVIDUAL);
        assertEquals(TriggerKind.PLAYER_STATE, TriggerTypes.kindOf("mymod:zone"));
        TriggerTypes.resetForTesting();
        assertEquals(TriggerKind.EVENT, TriggerTypes.kindOf("mymod:zone"));
        assertEquals(TriggerKind.PLAYER_STATE, TriggerTypes.kindOf("biome"));
    }
}
