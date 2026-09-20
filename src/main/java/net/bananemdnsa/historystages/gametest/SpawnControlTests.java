package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.IntRange;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;
import net.bananemdnsa.historystages.util.lock.SpawnControlGate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * SpawnControl against a real FinalizeSpawnEvent. The decision table itself is pinned by
 * SpawnRuleSetTest; this proves the handler reads the position it is given.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpawnControlTests {

    private SpawnControlTests() {}

    @GameTest(template = "empty")
    public static void aHeightConditionLetsOneOfTwoSummonsThrough(GameTestHelper helper) {
        BlockPos low = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos high = low.above(2);
        GameTestStages.global("spawn_height", stage -> stage.getEntities().setSpawnlock(List.of(
                new EntitySpawnLockEntry("minecraft:zombie", List.of("summon"), GenerationPhase.WHILE_LOCKED,
                        new SpawnConditions(null, null, null, new IntRange(low.getY(), low.getY()),
                                null, null, null, Set.of()),
                        null))));
        SpawnControlGate.rebuild();
        try {
            EntityType.ZOMBIE.spawn(helper.getLevel(), low, MobSpawnType.COMMAND);
            EntityType.ZOMBIE.spawn(helper.getLevel(), high, MobSpawnType.COMMAND);

            int zombies = helper.getEntities(EntityType.ZOMBIE).size();
            if (zombies != 1) {
                helper.fail("expected exactly the zombie inside the height range, found " + zombies);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            SpawnControlGate.rebuild();
        }
    }
}
