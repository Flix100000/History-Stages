package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.lock.spawn.ExtraBiomeSpawns;
import net.bananemdnsa.historystages.data.lock.spawn.FilterMode;
import net.bananemdnsa.historystages.data.lock.spawn.IdFilter;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;
import net.bananemdnsa.historystages.data.lock.spawn.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntitySpawnLockEntryTest {

    private static SpawnConditions night() {
        return new SpawnConditions(null, null, null, null, TimeOfDay.NIGHT, null, null, Set.of());
    }

    @Test
    void legacyDimensionListBecomesAnOnlyInCondition() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", null, List.of("minecraft:the_nether"));
        assertEquals(IdFilter.only(List.of("minecraft:the_nether")), entry.getConditions().dimensions());
        assertEquals(List.of("minecraft:the_nether"), entry.getUnlockDimensions());
        assertFalse(entry.blocksDimension("minecraft:the_nether"));
        assertTrue(entry.blocksDimension("minecraft:overworld"));
    }

    @Test
    void excludeDimensionsAreNotReportedAsTheLegacyAllowList() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", null, GenerationPhase.WHILE_LOCKED,
                SpawnConditions.EMPTY.withDimensions(new IdFilter(FilterMode.EXCLUDE, List.of("minecraft:the_end"))), null);
        assertNull(entry.getUnlockDimensions());
        assertTrue(entry.blocksDimension("minecraft:the_end"));
        assertFalse(entry.blocksDimension("minecraft:overworld"));
    }

    @Test
    void legacyExpressibleMeansTheOldJsonShapeCanHoldIt() {
        assertTrue(new EntitySpawnLockEntry("a:b", List.of("natural"), List.of("minecraft:overworld")).isLegacyExpressible());
        assertFalse(new EntitySpawnLockEntry("a:b", null, GenerationPhase.WHILE_LOCKED, night(), null).isLegacyExpressible());
        assertFalse(new EntitySpawnLockEntry("a:b", null, GenerationPhase.AFTER_UNLOCK, null, null).isLegacyExpressible());
    }

    @Test
    void anExtraBiomeListWithoutIdsIsNoExtraBiomes() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("a:b", null, GenerationPhase.WHILE_LOCKED, null,
                new ExtraBiomeSpawns(List.of(), null, true));
        assertNull(entry.getExtraBiomes());
        assertEquals(new EntitySpawnLockEntry("a:b"), entry);
    }

    @Test
    void anEmptyDimensionFilterIsNoDimensionConditionAtAll() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("a:b", null, GenerationPhase.WHILE_LOCKED,
                SpawnConditions.EMPTY.withDimensions(IdFilter.only(List.of())), null);
        assertEquals(new EntitySpawnLockEntry("a:b"), entry);
        assertFalse(entry.hasUnlockDimensions());
    }
}
