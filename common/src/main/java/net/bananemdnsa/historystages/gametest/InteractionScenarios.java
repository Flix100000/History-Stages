package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.events.lock.InteractionLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;

/** How an empty-hand right-click on a mob is classified for interaction locks. */
public final class InteractionScenarios {

    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private InteractionScenarios() {}

    /** A pig takes a saddle and is mounted; a cow and a zombie do not, whatever their slots say. */
    public static void onlySaddleableMobsAreMounted(GameTestHelper helper) {
        expect(helper, EntityTypes.PIG, "mount");
        expect(helper, EntityTypes.COW, "other");
        expect(helper, EntityTypes.ZOMBIE, "other");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, EntityType<?> type, String action) {
        Entity mob = helper.spawn(type, POS);
        String actual = InteractionLockHandler.classifyAction(mob, ItemStack.EMPTY);
        mob.discard();
        if (!action.equals(actual)) {
            helper.fail(Component.literal("empty hand on " + EntityType.getKey(type) + " classified as "
                    + actual + ", expected " + action));
        }
    }
}
