package net.bananemdnsa.historystages.data.lock.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SpawnRuleValidatorTest {

    private static final Predicate<String> VALID = id -> id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");

    @Test
    void aCleanEntryComesBackEqualAndSilent() {
        List<String> warnings = new ArrayList<>();
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", null, List.of("minecraft:overworld"));
        assertEquals(entry, SpawnRuleValidator.sanitize(entry, VALID, warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidIdsAreRemovedAndAnEmptyFilterIsDropped() {
        List<String> warnings = new ArrayList<>();
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", null, GenerationPhase.WHILE_LOCKED,
                new SpawnConditions(null, IdFilter.only(List.of("Not Valid")), null, null, null, null, null, Set.of()),
                new ExtraBiomeSpawns(List.of("#minecraft:is_forest", "BAD"), null, false));

        EntitySpawnLockEntry fixed = SpawnRuleValidator.sanitize(entry, VALID, warnings::add);

        assertNull(fixed.getConditions().biomes());
        assertEquals(List.of("#minecraft:is_forest"), fixed.getExtraBiomes().ids());
        assertEquals(3, warnings.size(), "bad biome id, emptied biome filter, bad extra biome id");
    }

    @Test
    void lightIsClampedAndMoonPhasesOutsideTheCycleRemoved() {
        List<String> warnings = new ArrayList<>();
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", null, GenerationPhase.WHILE_LOCKED,
                new SpawnConditions(null, null, null, null, null, new IntRange(-3, 20), null, Set.of(0, 9)), null);

        EntitySpawnLockEntry fixed = SpawnRuleValidator.sanitize(entry, VALID, warnings::add);

        assertEquals(new IntRange(0, 15), fixed.getConditions().light());
        assertEquals(Set.of(0), fixed.getConditions().moonPhases());
        assertEquals(2, warnings.size());
    }
}
