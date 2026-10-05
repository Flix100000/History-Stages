package net.bananemdnsa.historystages.data.lock.spawn;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.util.lock.SpawnRuleSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixed SpawnControl rows: a fixed row takes the stage's value on every mob, the other rows stay
 * the mob's own, and the mob's stored entry is never rewritten.
 */
class FixedSpawnRuleTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final SpawnContext NIGHT_OVERWORLD = new SpawnContext("minecraft:overworld",
            "minecraft:plains", List.of(), 64, 0, true, 18000, false, false, 0);

    private static EntitySpawnLockEntry values(List<String> locked, GenerationPhase phase, SpawnConditions conditions) {
        return new EntitySpawnLockEntry("*", null, phase, conditions, null).withLockedSources(locked);
    }

    @Test
    void withoutFixedRowsTheEntryComesBackUntouched() {
        EntitySpawnLockEntry own = new EntitySpawnLockEntry(ZOMBIE, List.of("natural"));
        assertSame(own, FixedSpawnRule.copyRows(Set.of(), new EntitySpawnLockEntry("*"), own));
    }

    @Test
    void aFixedSourceTakesTheStageValueBothWays() {
        EntitySpawnLockEntry own = new EntitySpawnLockEntry(ZOMBIE, List.of("natural", "spawner"));
        FixedSpawnRule rule = new FixedSpawnRule(
                List.of(FixedSpawnRule.source("spawner"), FixedSpawnRule.source("breeding")),
                values(List.of("breeding"), GenerationPhase.WHILE_LOCKED, SpawnConditions.EMPTY));

        EntitySpawnLockEntry effective = rule.apply(own);

        assertTrue(effective.blocksSource("natural"), "not fixed: the mob's own value");
        assertFalse(effective.blocksSource("spawner"), "fixed as allowed");
        assertTrue(effective.blocksSource("breeding"), "fixed as locked");
        assertFalse(effective.blocksSource("summon"), "not fixed and not locked by the mob");
    }

    /**
     * The case a stored entry cannot express: the stage allows the only source the mob locks.
     * An empty source list reads as "all", so without the explicit "none" the mob would end up
     * blocked everywhere — the opposite of what was fixed.
     */
    @Test
    void allowingTheMobsOnlySourceLeavesItBlockingNothing() {
        EntitySpawnLockEntry own = new EntitySpawnLockEntry(ZOMBIE, List.of("natural"));
        FixedSpawnRule rule = new FixedSpawnRule(List.of(FixedSpawnRule.source("natural")),
                values(List.of(), GenerationPhase.WHILE_LOCKED, SpawnConditions.EMPTY));

        EntitySpawnLockEntry effective = rule.apply(own);

        for (String source : EntitySpawnLockEntry.ALL_SOURCES) {
            assertFalse(effective.blocksSource(source), source + " must not be blocked");
        }
    }

    @Test
    void fixedConditionsAndPhaseWinWhileTheRestAndTheExtraBiomesStay() {
        ExtraBiomeSpawns extra = new ExtraBiomeSpawns(List.of("minecraft:mushroom_fields"), null, false);
        EntitySpawnLockEntry own = new EntitySpawnLockEntry(ZOMBIE, null, GenerationPhase.WHILE_LOCKED,
                new SpawnConditions(null, null, SkyCondition.HIDDEN, null, TimeOfDay.DAY, null, null, Set.of()),
                extra);
        FixedSpawnRule rule = new FixedSpawnRule(List.of(FixedSpawnRule.PHASE, FixedSpawnRule.TIME),
                values(EntitySpawnLockEntry.ALL_SOURCES, GenerationPhase.AFTER_UNLOCK,
                        new SpawnConditions(null, null, SkyCondition.VISIBLE, null, TimeOfDay.NIGHT, null, null, Set.of())));

        EntitySpawnLockEntry effective = rule.apply(own);

        assertEquals(GenerationPhase.AFTER_UNLOCK, effective.getPhase());
        assertEquals(TimeOfDay.NIGHT, effective.getConditions().time());
        assertEquals(SkyCondition.HIDDEN, effective.getConditions().sky(), "sky is not fixed: the stage's VISIBLE must not leak");
        assertEquals(extra, effective.getExtraBiomes());
        assertEquals(ZOMBIE, effective.getId());
    }

    @Test
    void theStoredEntryStaysAsTypedAndTheSpawnCheckSeesTheFixedRows() {
        StageEntry stage = new StageEntry();
        EntitySpawnLockEntry own = new EntitySpawnLockEntry(ZOMBIE, List.of("natural"));
        stage.getEntities().setSpawnlock(new java.util.ArrayList<>(List.of(own)));
        stage.setFixedSpawnRule(new FixedSpawnRule(List.of(FixedSpawnRule.source("natural")),
                values(List.of(), GenerationPhase.WHILE_LOCKED, SpawnConditions.EMPTY)));

        assertEquals(own, stage.getEntities().getSpawnlock().get(0));

        SpawnRuleSet rules = SpawnRuleSet.build(Map.of("iron_age", stage.getEffectiveSpawnlock()), Set.of());
        assertTrue(rules.isAllowed(ZOMBIE, "natural", NIGHT_OVERWORLD),
                "natural is fixed as allowed, so the locked stage must let the zombie spawn");

        SpawnRuleSet ownOnly = SpawnRuleSet.build(Map.of("iron_age", stage.getEntities().getSpawnlock()), Set.of());
        assertFalse(ownOnly.isAllowed(ZOMBIE, "natural", NIGHT_OVERWORLD), "control: the mob alone blocks natural");
    }

    @Test
    void theRuleSurvivesTheStageFileIncludingAllSourcesAllowed() {
        StageEntry stage = new StageEntry();
        stage.setFixedSpawnRule(new FixedSpawnRule(
                List.of(FixedSpawnRule.PHASE, FixedSpawnRule.source("natural"), FixedSpawnRule.DIMENSIONS),
                values(List.of(), GenerationPhase.AFTER_UNLOCK,
                        SpawnConditions.EMPTY.withDimensions(new IdFilter(FilterMode.EXCLUDE, List.of("minecraft:the_nether"))))));

        String json = stage.toJson();
        StageEntry restored = new Gson().fromJson(json, StageEntry.class);
        FixedSpawnRule rule = restored.getFixedSpawnRule();

        assertEquals(List.of(FixedSpawnRule.PHASE, FixedSpawnRule.source("natural"), FixedSpawnRule.DIMENSIONS), rule.rows());
        assertEquals(GenerationPhase.AFTER_UNLOCK, rule.values().getPhase());
        assertEquals(FilterMode.EXCLUDE, rule.values().getConditions().dimensions().mode());
        assertFalse(rule.values().blocksSource("natural"), "\"none locked\" must not read back as \"all\": " + json);
    }

    @Test
    void noRowsMeansNoKey() {
        StageEntry stage = new StageEntry();
        stage.setFixedSpawnRule(new FixedSpawnRule(List.of(), new EntitySpawnLockEntry("*")));

        assertNull(stage.getFixedSpawnRule());
        assertFalse(stage.toJson().contains("fixed_spawn_rule"));
    }

    @Test
    void unknownRowsAreDropped() {
        FixedSpawnRule rule = new FixedSpawnRule(List.of("height", "typo"), new EntitySpawnLockEntry("*"));
        assertEquals(List.of(FixedSpawnRule.HEIGHT), rule.rows());
    }
}
