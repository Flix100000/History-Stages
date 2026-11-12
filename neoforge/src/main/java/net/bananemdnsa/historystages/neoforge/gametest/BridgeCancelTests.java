package net.bananemdnsa.historystages.neoforge.gametest;

import java.util.Map;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.bananemdnsa.historystages.platform.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A handler on the platform bus cancels, and the real NeoForge event has to end up cancelled.
 *
 * <p>The player goes through {@code ServerPlayerGameMode}, the path a real click or block break
 * takes, because the GameTest helpers call the block directly and skip the NeoForge events.
 * Each cancelling test has a twin without the handler: if the setup could not open a chest or
 * break a block at all, the cancelling test would pass for the wrong reason.
 *
 * <p>The handler is only on the bus while its test runs and only reacts to the test's own player,
 * so it cannot reach into a test running next to it.
 */
public final class BridgeCancelTests {

    private static final BlockPos POS = BlockPos.ZERO;

    private BridgeCancelTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("bridge/cancelled_right_click_block_does_not_open_chest",
                BridgeCancelTests::cancelledRightClickBlockDoesNotOpenChest);
        tests.put("bridge/right_click_block_opens_chest", BridgeCancelTests::rightClickBlockOpensChest);
        tests.put("bridge/cancelled_block_break_keeps_block", BridgeCancelTests::cancelledBlockBreakKeepsBlock);
        tests.put("bridge/block_break_removes_block", BridgeCancelTests::blockBreakRemovesBlock);
    }

    public static void cancelledRightClickBlockDoesNotOpenChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        withCancelling(PlayerInteractEvent.RightClickBlock.class, player, () -> rightClickChest(helper, player));
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
                "a cancelled right click opened the chest anyway");
        helper.succeed();
    }

    public static void rightClickBlockOpensChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        rightClickChest(helper, player);
        helper.assertTrue(player.containerMenu != player.inventoryMenu,
                "the test player cannot open a chest at all, so the cancel test proves nothing");
        helper.succeed();
    }

    public static void cancelledBlockBreakKeepsBlock(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        helper.setBlock(POS, Blocks.STONE);
        withCancelling(BlockEvent.BreakEvent.class, player,
                () -> player.gameMode.destroyBlock(helper.absolutePos(POS)));
        helper.assertBlockPresent(Blocks.STONE, POS);
        helper.succeed();
    }

    public static void blockBreakRemovesBlock(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        helper.setBlock(POS, Blocks.STONE);
        player.gameMode.destroyBlock(helper.absolutePos(POS));
        helper.assertBlockNotPresent(Blocks.STONE, POS);
        helper.succeed();
    }

    private static void rightClickChest(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(POS, Blocks.CHEST);
        BlockPos absolute = helper.absolutePos(POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Runs the action with a platform-bus handler that cancels {@code type} for this player only. */
    private static <T extends Event & net.bananemdnsa.historystages.platform.bus.ICancellableEvent> void withCancelling(
            Class<T> type, ServerPlayer player, Runnable action) {
        Consumer<T> canceller = event -> {
            if (playerOf(event) == player) {
                event.setCanceled(true);
            }
        };
        EventBus.addListener(type, canceller);
        try {
            action.run();
        } finally {
            EventBus.unregister(canceller);
        }
    }

    private static Object playerOf(Event event) {
        if (event instanceof PlayerInteractEvent interact) {
            return interact.getEntity();
        }
        if (event instanceof BlockEvent.BreakEvent breakEvent) {
            return breakEvent.getPlayer();
        }
        return null;
    }
}
