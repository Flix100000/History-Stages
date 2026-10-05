package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.disguise.DisguiseData;
import net.bananemdnsa.historystages.data.disguise.DisguiseRule;
import net.bananemdnsa.historystages.data.disguise.DisguiseRuleSet;
import net.bananemdnsa.historystages.data.disguise.DropsMode;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Breaking a disguised block for real, through the player's game mode — the whole path a click
 * takes: break event, harvest check, loot, drop event.
 *
 * <p>Emerald ore disguised as stone. Stone drops cobblestone with a pickaxe, which is the item every
 * test looks for; emerald is what must never appear while the disguise holds.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisguiseGameTests {

    private static final BlockPos ORE = new BlockPos(1, 2, 1);

    private DisguiseGameTests() {}

    @GameTest(template = "empty")
    public static void disguiseModeDropsTheDisguise(GameTestHelper helper) {
        run(helper, DropsMode.DISGUISE, false, player -> {
            List<Item> drops = breakOre(helper, player, Items.IRON_PICKAXE);
            if (!drops.contains(Items.COBBLESTONE) || drops.contains(Items.EMERALD)) {
                helper.fail("emerald ore disguised as stone (drops: disguise) dropped " + drops
                        + " — expected cobblestone and no emerald");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void disguiseModeNeedsTheDisguisesTool(GameTestHelper helper) {
        // Stone needs a pickaxe. Without one it drops nothing — and so must the disguised ore,
        // even though a fist would not change what the real ore drops either. The control is the
        // pickaxe test above.
        run(helper, DropsMode.DISGUISE, false, player -> {
            List<Item> drops = breakOre(helper, player, Items.AIR);
            if (!drops.isEmpty()) {
                helper.fail("broke disguised ore by hand and got " + drops
                        + " — stone gives nothing without a pickaxe");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void realModeKeepsTheBreakLock(GameTestHelper helper) {
        run(helper, DropsMode.REAL, false, player -> {
            List<Item> drops = breakOre(helper, player, Items.IRON_PICKAXE);
            if (drops.contains(Items.COBBLESTONE)) {
                helper.fail("drops: real must not hand out the disguise's drops, got " + drops);
                return;
            }
            if (Config.GAMEPLAY.lockBlockBreaking.get() && drops.contains(Items.EMERALD)) {
                helper.fail("drops: real keeps the break lock, but the locked ore dropped " + drops);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void anUnlockedStageDropsTheRealBlock(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "disguise_ore";
        try {
            run(helper, DropsMode.DISGUISE, false, player -> {
                data.addStage(stageId);
                List<Item> drops = breakOre(helper, player, Items.IRON_PICKAXE);
                if (!drops.contains(Items.EMERALD)) {
                    helper.fail("the stage is unlocked, the disguise must be gone, but breaking "
                            + "dropped " + drops);
                    return;
                }
                helper.succeed();
            });
        } finally {
            data.removeStage(stageId);
        }
    }

    @GameTest(template = "empty")
    public static void anIndividualStageDisguisesForThatPlayer(GameTestHelper helper) {
        run(helper, DropsMode.DISGUISE, true, player -> {
            List<Item> drops = breakOre(helper, player, Items.IRON_PICKAXE);
            if (!drops.contains(Items.COBBLESTONE) || drops.contains(Items.EMERALD)) {
                helper.fail("ore in an individual stage this player lacks, disguised as stone, "
                        + "dropped " + drops);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void anIndividualUnlockEndsTheDisguiseForThatPlayer(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "disguise_ore";
        ServerPlayer[] holder = new ServerPlayer[1];
        try {
            run(helper, DropsMode.DISGUISE, true, player -> {
                holder[0] = player;
                data.addStage(player.getUUID(), stageId);
                List<Item> drops = breakOre(helper, player, Items.IRON_PICKAXE);
                if (!drops.contains(Items.EMERALD)) {
                    helper.fail("this player unlocked the individual stage, but breaking still "
                            + "dropped " + drops);
                    return;
                }
                helper.succeed();
            });
        } finally {
            if (holder[0] != null) data.removeStage(holder[0].getUUID(), stageId);
        }
    }

    // --- helpers ---

    private static void run(GameTestHelper helper, DropsMode drops, boolean individual,
                            Consumer<ServerPlayer> body) {
        DisguiseRuleSet before = DisguiseData.get();
        try {
            DisguiseData.set(DisguiseRuleSet.empty().withRule(new DisguiseRule(
                    "minecraft:emerald_ore", "minecraft:stone", drops, true, null)));
            Consumer<net.bananemdnsa.historystages.data.StageEntry> fill = stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:emerald_ore"))));
            if (individual) {
                GameTestStages.individual("disguise_ore", fill);
            } else {
                GameTestStages.global("disguise_ore", fill);
            }

            ServerPlayer player = GameTestPlayers.createConnected(helper);
            player.setGameMode(GameType.SURVIVAL);
            body.accept(player);
        } finally {
            GameTestStages.removeAll();
            DisguiseData.set(before);
        }
    }

    /** Places the ore, breaks it with {@code tool}, returns the items that fell out. */
    private static List<Item> breakOre(GameTestHelper helper, ServerPlayer player, Item tool) {
        helper.setBlock(ORE, Blocks.EMERALD_ORE);
        BlockPos pos = helper.absolutePos(ORE);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool == Items.AIR ? ItemStack.EMPTY : new ItemStack(tool));

        AABB area = new AABB(pos).inflate(2);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(e -> e.discard());
        player.gameMode.destroyBlock(pos);

        List<Item> out = new ArrayList<>();
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
            out.add(e.getItem().getItem());
            e.discard();
        }
        return out;
    }
}
