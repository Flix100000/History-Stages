package net.bananemdnsa.historystages.fabric.gametest;

import java.util.function.Consumer;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.ICancellableEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.bananemdnsa.historystages.platform.event.level.BlockEvent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A handler on the platform bus cancels, and the real interaction has to stop. The Fabric twin of
 * the NeoForge {@code BridgeCancelTests}.
 *
 * <p>The player goes through {@code ServerPlayerGameMode}, the path a real click or block break
 * takes, so Fabric's own callbacks and our mixins are on it. Each cancelling test has a twin
 * without the handler, so a setup that cannot open a chest or break a block at all fails there
 * instead of letting the cancel test pass for the wrong reason.
 *
 * <p>Sky access, because the default barrier ceiling sits right on top of the structure and a
 * chest under a solid block does not open.
 */
public class BridgeCancelTests {

    private static final BlockPos POS = BlockPos.ZERO;

    @GameTest(skyAccess = true)
    public void cancelledRightClickBlockDoesNotOpenChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        withCancelling(PlayerInteractEvent.RightClickBlock.class, player, () -> rightClickChest(helper, player));
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
                "a cancelled right click opened the chest anyway");
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void rightClickBlockOpensChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        rightClickChest(helper, player);
        helper.assertTrue(player.containerMenu != player.inventoryMenu,
                "the test player cannot open a chest at all, so the cancel test proves nothing");
        helper.succeed();
    }

    /** useBlock FALSE without a cancel: the chest stays shut, the bridge's gate in useItemOn holds. */
    @GameTest(skyAccess = true)
    public void deniedBlockUseDoesNotOpenChest(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Consumer<PlayerInteractEvent.RightClickBlock> deny = event -> {
            if (event.getEntity() == player) {
                event.setUseBlock(net.bananemdnsa.historystages.platform.TriState.FALSE);
            }
        };
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, deny);
        try {
            rightClickChest(helper, player);
        } finally {
            EventBus.unregister(deny);
        }
        helper.assertTrue(player.containerMenu == player.inventoryMenu,
                "useBlock FALSE still let the chest open");
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void cancelledBlockBreakKeepsBlock(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        helper.setBlock(POS, Blocks.STONE);
        withCancelling(BlockEvent.BreakEvent.class, player,
                () -> player.gameMode.destroyBlock(helper.absolutePos(POS)));
        helper.assertBlockPresent(Blocks.STONE, POS);
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void blockBreakRemovesBlock(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        helper.setBlock(POS, Blocks.STONE);
        player.gameMode.destroyBlock(helper.absolutePos(POS));
        helper.assertBlockNotPresent(Blocks.STONE, POS);
        helper.succeed();
    }

    static void rightClickChest(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(POS, Blocks.CHEST);
        BlockPos absolute = helper.absolutePos(POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Runs the action with a platform-bus handler that cancels {@code type} for this player only. */
    private static <T extends Event & ICancellableEvent> void withCancelling(Class<T> type, ServerPlayer player,
                                                                           Runnable action) {
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
