package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;
import net.bananemdnsa.historystages.data.lock.spawn.TimeOfDay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what the built-in categories answer, so moving them off {@code StageManager}'s typed query
 * methods cannot change it quietly.
 *
 * <p>Only the categories free of Minecraft types are covered here. Items, tags, mods and
 * interaction locks need a live registry, real tags and real stacks; those are pinned in the
 * GameTests under {@code gametest/LockTests} instead.
 */
class BuiltInCategoryMatchingTest {

    private static LockCategory<?> category(String id) {
        LockCategory<?> found = LockCategories.byId(id);
        assertNotNull(found, "no built-in category registered under " + id);
        return found;
    }

    private static Map<String, StageEntry> stages(Object... idsAndEntries) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndEntries.length; i += 2) {
            map.put((String) idsAndEntries[i], (StageEntry) idsAndEntries[i + 1]);
        }
        return map;
    }

    // ---- dimensions ----------------------------------------------------------------

    private static StageEntry gatingDimension(String dimensionId) {
        StageEntry stage = new StageEntry();
        stage.setDimensions(List.of(dimensionId));
        return stage;
    }

    @Test
    void aListedDimensionIsGated() {
        assertTrue(category("historystages:dimensions")
                .gates(gatingDimension("minecraft:the_nether"), "minecraft:the_nether"));
    }

    @Test
    void anUnlistedDimensionIsNotGated() {
        assertFalse(category("historystages:dimensions")
                .gates(gatingDimension("minecraft:the_nether"), "minecraft:the_end"));
    }

    @Test
    void aStageWithoutDimensionsGatesNothing() {
        assertFalse(category("historystages:dimensions")
                .gates(new StageEntry(), "minecraft:the_nether"));
    }

    @Test
    void everyStageGatingADimensionIsReportedInMapOrder() {
        assertEquals(List.of("bronze", "iron"),
                CategoryLockResolver.gatingStages(category("historystages:dimensions"),
                        "minecraft:the_nether",
                        stages("bronze", gatingDimension("minecraft:the_nether"),
                               "iron", gatingDimension("minecraft:the_nether"))));
    }

    // ---- structures ----------------------------------------------------------------

    @Test
    void aListedStructureIsGated() {
        StageEntry stage = new StageEntry();
        stage.setStructures(List.of("minecraft:village_plains"));
        assertTrue(category("historystages:structures").gates(stage, "minecraft:village_plains"));
    }

    @Test
    void anUnlistedStructureIsNotGated() {
        StageEntry stage = new StageEntry();
        stage.setStructures(List.of("minecraft:village_plains"));
        assertFalse(category("historystages:structures").gates(stage, "minecraft:fortress"));
    }

    @Test
    void aStageWithoutStructuresGatesNothing() {
        assertFalse(category("historystages:structures").gates(new StageEntry(), "minecraft:fortress"));
    }

    // ---- biomes --------------------------------------------------------------------

    @Test
    void aListedBiomeIsGated() {
        StageEntry stage = new StageEntry();
        stage.setBiomes(List.of("minecraft:desert"));
        assertTrue(category("historystages:biomes").gates(stage, "minecraft:desert"));
    }

    @Test
    void anUnlistedBiomeIsNotGated() {
        StageEntry stage = new StageEntry();
        stage.setBiomes(List.of("minecraft:desert"));
        assertFalse(category("historystages:biomes").gates(stage, "minecraft:jungle"));
    }

    // ---- recipes -------------------------------------------------------------------

    @Test
    void aListedRecipeIsGated() {
        StageEntry stage = new StageEntry();
        stage.setRecipes(List.of("minecraft:diamond_sword"));
        assertTrue(category("historystages:recipes").gates(stage, "minecraft:diamond_sword"));
    }

    @Test
    void anUnlistedRecipeIsNotGated() {
        StageEntry stage = new StageEntry();
        stage.setRecipes(List.of("minecraft:diamond_sword"));
        assertFalse(category("historystages:recipes").gates(stage, "minecraft:stone_sword"));
    }

    // ---- attack locks --------------------------------------------------------------

    @Test
    void aListedAttackLockGatesTheEntity() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklock(List.of("minecraft:zombie"));
        assertTrue(category("historystages:attacklock").gates(stage, "minecraft:zombie"));
    }

    @Test
    void anUnrelatedEntityIsNotGatedByAnAttackLock() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setAttacklock(List.of("minecraft:zombie"));
        assertFalse(category("historystages:attacklock").gates(stage, "minecraft:creeper"));
    }

    @Test
    void noSpawnLockGatesAttackingHoweverBroadItIs() {
        // Up to 6.0.0 a spawn lock that blocked every source implied an attack lock. It no longer
        // does: whether a creature may be hit is its own question, and answering it from the spawn
        // list handed out invulnerable mobs that had come from a spawner or an egg.
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertFalse(category("historystages:attacklock").gates(stage, "minecraft:zombie"));
        assertFalse(category("historystages:attacklock").globalDualPhaseIds(stage).contains("minecraft:zombie"));
    }

    @Test
    void everyStageGatingAnAttackIsReportedInMapOrder() {
        StageEntry bronze = new StageEntry();
        bronze.getEntities().setAttacklock(List.of("minecraft:zombie"));
        StageEntry iron = new StageEntry();
        iron.getEntities().setAttacklock(List.of("minecraft:zombie"));

        assertEquals(List.of("bronze", "iron"),
                CategoryLockResolver.gatingStages(category("historystages:attacklock"),
                        "minecraft:zombie", stages("bronze", bronze, "iron", iron)));
    }

    // ---- spawn locks ---------------------------------------------------------------

    @Test
    void aSourcelessSpawnLockBlocksEverySource() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertTrue(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "natural", "minecraft:overworld")));
    }

    @Test
    void aSpawnLockOnlyBlocksItsListedSources() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(
                List.of(new EntitySpawnLockEntry("minecraft:zombie", List.of("spawner"))));
        assertTrue(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "spawner", "minecraft:overworld")));
        assertFalse(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "natural", "minecraft:overworld")));
    }

    @Test
    void anotherEntityIsNotGatedByASpawnLock() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        assertFalse(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:creeper", "natural", "minecraft:overworld")));
    }

    @Test
    void aNullSourceAsksOnlyWhetherAnEntryExistsForThisDimension() {
        // The EntityJoinLevel fallback, which fires when no spawn reason is available: any
        // source, as long as an entry covers this dimension.
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(
                List.of(new EntitySpawnLockEntry("minecraft:zombie", List.of("spawner"))));
        assertTrue(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", null, "minecraft:overworld")));
    }

    @Test
    void aSpawnLockUnlockedInThisDimensionDoesNotBlock() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry(
                "minecraft:zombie", List.of(), List.of("minecraft:the_nether"))));
        assertFalse(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "natural", "minecraft:the_nether")));
    }

    @Test
    void anAfterUnlockSpawnRuleLocksNothingWhileTheStageIsLocked() {
        StageEntry stage = new StageEntry();
        stage.getEntities().setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie", null,
                GenerationPhase.AFTER_UNLOCK, null, null)));
        assertFalse(category("historystages:spawnlock").gates(stage,
                new LockSubjects.SpawnSubject("minecraft:zombie", "natural", "minecraft:overworld")));
    }

    @Test
    void spawnLocksAreGlobalOnly() {
        // No per-player spawn gate exists in the data model, and the loader strips spawn locks
        // out of individual stages. The category says so rather than leaving it implicit.
        assertFalse(category("historystages:spawnlock").supportedScopes()
                .contains(net.bananemdnsa.historystages.api.stage.StageScope.INDIVIDUAL));
    }

    // ---- shared contract -----------------------------------------------------------

    @Test
    void aCategoryAskedWithTheWrongSubjectTypeSaysNoInsteadOfThrowing() {
        // A multi-category pass over one stage asks every category with the same subject, so a
        // category that cannot make sense of it has to decline rather than blow up the pass.
        assertFalse(category("historystages:dimensions")
                .gates(gatingDimension("minecraft:the_nether"), 42));
    }
}
