package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.lock.spawn.FixedSpawnRule;
import net.bananemdnsa.historystages.data.lock.spawn.IntRange;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;
import net.bananemdnsa.historystages.util.lock.SpawnControlGate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * SpawnControl against a real MobSpawnEvent.FinalizeSpawn. The decision table itself is pinned by
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

            // getBounds() is private here, so the box is built from the two positions the test used.
            AABB around = new AABB(low).minmax(new AABB(high)).inflate(2);
            int zombies = helper.getLevel()
                    .getEntities(EntityType.ZOMBIE, around, Entity::isAlive).size();
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

    /**
     * The mob locks only "summon"; the stage fixes "summon" as allowed. Both spawn paths have to
     * see the fixed row — the rule set behind real spawns and the lock engine behind the
     * EntityJoinLevel fallback — or the zombie stays blocked by a value that is no longer its own.
     */
    @GameTest(template = "empty")
    public static void aSourceFixedAsAllowedLetsTheMobThrough(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        GameTestStages.global("spawn_fixed", stage -> {
            stage.getEntities().setSpawnlock(new java.util.ArrayList<>(List.of(
                    new EntitySpawnLockEntry("minecraft:zombie", List.of("summon")))));
            stage.setFixedSpawnRule(new FixedSpawnRule(List.of(FixedSpawnRule.source("summon")),
                    new EntitySpawnLockEntry("*", null, GenerationPhase.WHILE_LOCKED, SpawnConditions.EMPTY, null)
                            .withLockedSources(List.of())));
        });
        SpawnControlGate.rebuild();
        try {
            List<String> gating = StageLocks.engine().gatingStagesForEntitySpawn("minecraft:zombie", "summon",
                    helper.getLevel().dimension().location().toString(), StageScope.GLOBAL);
            if (!gating.isEmpty()) {
                helper.fail("summon is fixed as allowed, but the lock engine still names " + gating);
                return;
            }

            EntityType.ZOMBIE.spawn(helper.getLevel(), pos, MobSpawnType.COMMAND);
            int zombies = helper.getLevel()
                    .getEntities(EntityType.ZOMBIE, new AABB(pos).inflate(2), Entity::isAlive).size();
            if (zombies != 1) {
                helper.fail("summon is fixed as allowed, but the summoned zombie did not appear");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            SpawnControlGate.rebuild();
        }
    }
}
