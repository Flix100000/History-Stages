package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpawnRuleSetTest {

    private static final String ZOMBIE = "minecraft:zombie";

    private static SpawnContext at(String biome, long dayTime) {
        return new SpawnContext("minecraft:overworld", biome, List.of(), 64, 0, true, dayTime, false, false, 0);
    }

    private static final SpawnContext PLAINS_NOON = at("minecraft:plains", 6000);
    private static final SpawnContext PLAINS_NIGHT = at("minecraft:plains", 18000);
    private static final SpawnContext MUSHROOM_NIGHT = at("minecraft:mushroom_fields", 18000);

    private static SpawnConditions nightOnly() {
        return new SpawnConditions(null, null, null, null, TimeOfDay.NIGHT, null, null, Set.of());
    }

    private static SpawnConditions nightInSwamp() {
        return new SpawnConditions(null, IdFilter.only(List.of("minecraft:swamp")), null, null,
                TimeOfDay.NIGHT, null, null, Set.of());
    }

    private static ExtraBiomeSpawns mushrooms(boolean ignoreRules) {
        return new ExtraBiomeSpawns(List.of("minecraft:mushroom_fields"), null, ignoreRules);
    }

    private static EntitySpawnLockEntry rule(GenerationPhase phase, SpawnConditions c, ExtraBiomeSpawns extra) {
        return new EntitySpawnLockEntry(ZOMBIE, null, phase, c, extra);
    }

    private static SpawnRuleSet set(Map<String, List<EntitySpawnLockEntry>> byStage, String... unlocked) {
        return SpawnRuleSet.build(byStage, Set.of(unlocked));
    }

    @Test
    void whileLockedAppliesOnlyWhileLocked() {
        Map<String, List<EntitySpawnLockEntry>> stages = Map.of("iron", List.of(new EntitySpawnLockEntry(ZOMBIE)));
        assertFalse(set(stages).isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertTrue(set(stages, "iron").isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertFalse(set(stages, "iron").isActive());
    }

    @Test
    void afterUnlockDoesNothingWhileLocked() {
        Map<String, List<EntitySpawnLockEntry>> stages =
                Map.of("iron", List.of(rule(GenerationPhase.AFTER_UNLOCK, null, null)));
        assertTrue(set(stages).isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertFalse(set(stages, "iron").isAllowed(ZOMBIE, "natural", PLAINS_NOON));
    }

    @Test
    void conditionsSayWhereTheEntityIsStillAllowed() {
        SpawnRuleSet s = set(Map.of("iron", List.of(rule(GenerationPhase.WHILE_LOCKED, nightOnly(), null))));
        assertFalse(s.isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertTrue(s.isAllowed(ZOMBIE, "natural", PLAINS_NIGHT));
    }

    @Test
    void aPureExtraBiomeRuleRestrictsNothingElsewhere() {
        SpawnRuleSet s = set(Map.of("iron", List.of(rule(GenerationPhase.AFTER_UNLOCK, null, mushrooms(false)))), "iron");
        assertTrue(s.isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertTrue(s.isAllowed(ZOMBIE, "natural", MUSHROOM_NIGHT));
        assertEquals(1, s.extras().size());
    }

    @Test
    void theBiomeConditionDoesNotApplyInsideExtraBiomes() {
        SpawnRuleSet s = set(Map.of("iron", List.of(rule(GenerationPhase.WHILE_LOCKED, nightInSwamp(), mushrooms(false)))));
        assertTrue(s.isAllowed(ZOMBIE, "natural", MUSHROOM_NIGHT), "biome condition skipped in an extra biome");
        assertFalse(s.isAllowed(ZOMBIE, "natural", at("minecraft:mushroom_fields", 6000)), "time still applies");
        assertFalse(s.isAllowed(ZOMBIE, "natural", PLAINS_NIGHT), "outside, the biome condition applies");
    }

    @Test
    void conditionsOnlyCoverTheLockedSources() {
        EntitySpawnLockEntry naturalOnly = new EntitySpawnLockEntry(ZOMBIE, List.of("natural"),
                GenerationPhase.WHILE_LOCKED, nightOnly(), null);
        SpawnRuleSet s = set(Map.of("iron", List.of(naturalOnly)));
        assertFalse(s.isAllowed(ZOMBIE, "natural", PLAINS_NOON));
        assertTrue(s.isAllowed(ZOMBIE, "spawn_egg", PLAINS_NOON));
        assertFalse(s.isAllowed(ZOMBIE, null, PLAINS_NOON), "an unknown source is covered by every entry");
    }

    @Test
    void everyActiveEntryHasToAllowTheSpawn() {
        SpawnRuleSet s = set(Map.of(
                "iron", List.of(rule(GenerationPhase.WHILE_LOCKED, nightOnly(), null)),
                "bronze", List.of(rule(GenerationPhase.WHILE_LOCKED,
                        new SpawnConditions(null, null, SkyCondition.HIDDEN, null, null, null, null, Set.of()), null))));
        assertFalse(s.isAllowed(ZOMBIE, "natural", PLAINS_NIGHT), "night passes, sky does not");
    }

    @Test
    void forcesPlacementOnlyWithTheFlagInsideAnExtraBiome() {
        SpawnRuleSet flagged = set(Map.of("iron", List.of(rule(GenerationPhase.WHILE_LOCKED, nightOnly(), mushrooms(true)))));
        assertTrue(flagged.forcesPlacement(ZOMBIE, MUSHROOM_NIGHT));
        assertFalse(flagged.forcesPlacement(ZOMBIE, PLAINS_NIGHT));
        assertFalse(flagged.forcesPlacement(ZOMBIE, at("minecraft:mushroom_fields", 6000)));
        assertTrue(flagged.hasPlacementOverrides(ZOMBIE));

        SpawnRuleSet plain = set(Map.of("iron", List.of(rule(GenerationPhase.WHILE_LOCKED, null, mushrooms(false)))));
        assertFalse(plain.forcesPlacement(ZOMBIE, MUSHROOM_NIGHT));
        assertFalse(plain.hasPlacementOverrides(ZOMBIE));
    }

    @Test
    void aSourceRestrictedEntryStillOffersItsExtraBiomes() {
        EntitySpawnLockEntry spawnerOnly = new EntitySpawnLockEntry(ZOMBIE, List.of("spawner"),
                GenerationPhase.WHILE_LOCKED, null, mushrooms(false));
        SpawnRuleSet s = set(Map.of("iron", List.of(spawnerOnly)));
        assertEquals(1, s.extras().size());
    }
}
