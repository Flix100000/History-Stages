package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.DisguiseRule;
import net.bananemdnsa.historystages.data.disguise.DisguiseRuleSet;
import net.bananemdnsa.historystages.data.disguise.DropsMode;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Breaking a disguised, locked block through the server's game mode, on whichever loader runs
 * it: break speed, harvest check and drops must all come from the disguise. Both loaders' GameTest
 * classes call these; the player comes from the loader's own fake-connection helper.
 *
 * <p>Emerald ore sits in a global stage nobody has, disguised with {@code drops: disguise}.
 */
public final class DisguiseBreakScenarios {

    private static final String STAGE = "gametest:disguise_ore";
    private static final BlockPos ORE = new BlockPos(1, 2, 1);
    private static final BlockPos REFERENCE = new BlockPos(1, 2, 3);

    private DisguiseBreakScenarios() {}

    /** As stone, with a pickaxe: cobblestone, never emerald. */
    public static void dropsTheDisguise(GameTestHelper helper, Function<GameTestHelper, ServerPlayer> players) {
        run(helper, "minecraft:stone", () -> {
            List<Item> drops = breakOre(helper, players.apply(helper), Items.IRON_PICKAXE);
            if (!drops.contains(Items.COBBLESTONE) || drops.contains(Items.EMERALD)) {
                fail(helper, "emerald ore disguised as stone dropped " + drops + ", expected cobblestone and no emerald");
            }
        });
    }

    /** As dirt, by hand: dirt. The real ore needs a pickaxe and would drop nothing. */
    public static void harvestFollowsTheDisguise(GameTestHelper helper, Function<GameTestHelper, ServerPlayer> players) {
        run(helper, "minecraft:dirt", () -> {
            List<Item> drops = breakOre(helper, players.apply(helper), Items.AIR);
            if (!drops.contains(Items.DIRT)) {
                fail(helper, "emerald ore disguised as dirt, broken by hand, dropped " + drops + ", expected dirt");
            }
        });
    }

    /** As stone, with a pickaxe: the crack grows as fast as on real stone. */
    public static void breaksAtTheDisguisesSpeed(GameTestHelper helper, Function<GameTestHelper, ServerPlayer> players) {
        run(helper, "minecraft:stone", () -> {
            ServerPlayer player = players.apply(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            helper.setBlock(ORE, Blocks.EMERALD_ORE);
            helper.setBlock(REFERENCE, Blocks.STONE);
            float ore = progress(helper, player, ORE);
            float stone = progress(helper, player, REFERENCE);
            if (Math.abs(ore - stone) > 1.0e-6f) {
                fail(helper, "disguised ore breaks at " + ore + " per tick, stone at " + stone);
            }
        });
    }

    private static float progress(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        return state.getDestroyProgress(player, helper.getLevel(), pos);
    }

    private static void run(GameTestHelper helper, String disguiseAs, Runnable body) {
        DisguiseRuleSet before = DisguiseData.get();
        try {
            DisguiseData.set(DisguiseRuleSet.empty().withRule(new DisguiseRule(
                    "minecraft:emerald_ore", disguiseAs, DropsMode.DISGUISE, true, null)));
            StageEntry entry = new StageEntry();
            entry.setDisplayName("disguise_ore");
            entry.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:emerald_ore"))));
            StageManager.getStages().put(STAGE, entry);
            StageLocks.stagesChanged();
            body.run();
        } finally {
            StageManager.getStages().remove(STAGE);
            StageLocks.stagesChanged();
            DisguiseData.set(before);
        }
        helper.succeed();
    }

    /** Places the ore, breaks it with {@code tool}, returns the items that fell out. */
    private static List<Item> breakOre(GameTestHelper helper, ServerPlayer player, Item tool) {
        player.setGameMode(GameType.SURVIVAL);
        helper.setBlock(ORE, Blocks.EMERALD_ORE);
        BlockPos pos = helper.absolutePos(ORE);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool == Items.AIR ? ItemStack.EMPTY : new ItemStack(tool));

        AABB area = new AABB(pos).inflate(2);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
        player.gameMode.destroyBlock(pos);

        List<Item> out = new ArrayList<>();
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
            out.add(e.getItem().getItem());
            e.discard();
        }
        return out;
    }

    private static void fail(GameTestHelper helper, String message) {
        helper.fail(Component.literal(message));
    }
}
