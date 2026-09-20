package net.bananemdnsa.historystages.data.lock.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnRuleTextTest {

    @Test
    void aPlainLockHasNoText() {
        assertEquals("", SpawnRuleText.compact(new EntitySpawnLockEntry("minecraft:zombie")));
    }

    @Test
    void everyPartIsListed() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", List.of("natural"),
                GenerationPhase.AFTER_UNLOCK,
                new SpawnConditions(null, null, null, new IntRange(-64, 40), TimeOfDay.NIGHT, null, null, Set.of(4, 0)),
                new ExtraBiomeSpawns(List.of("minecraft:mushroom_fields"), new SpawnWeight(95, 4, 4), true));
        assertEquals("sources: natural; after_unlock; y -64..40; time night; moon 0,4; "
                        + "extra biomes: minecraft:mushroom_fields (weight 95, group 4-4, ignoring own rules)",
                SpawnRuleText.compact(entry));
    }
}
