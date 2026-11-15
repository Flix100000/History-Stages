package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.events.lock.BiomeLockHandler;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * The biome lock's per-player verdict, driven through the real tick and click events.
 *
 * <p>The verdict is cached per player and refreshed by the tick, so what can go wrong is a
 * verdict that outlives the lock it came from.
 */
public final class BiomeLockTests {

    private BiomeLockTests() {}

    private static String biomeAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBiome(pos).unwrapKey()
                .map(k -> k.identifier().toString())
                .orElseThrow(() -> new IllegalStateException("the test biome has no id"));
    }

    private static boolean rightClickRefused(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock click =
                new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, Direction.UP);
        EventBus.post(click);
        return click.isCanceled();
    }

    /**
     * The in-game report: a biome taken out of the only stage that locked one still refused
     * every placement and every break, because with no biome lock left the tick returned before
     * it ever looked at the old verdict again.
     */
    @HsGameTest
    public static void removingTheLastBiomeLockFreesThePlayer(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        try {
            BlockPos here = player.blockPosition();
            StageEntry stage = GameTestStages.global("biome_removed",
                    entry -> entry.setBiomes(new ArrayList<>(List.of(biomeAt(helper, here)))));
            EventBus.post(new PlayerTickEvent.Post(player));
            if (!BiomeLockHandler.isInsideLockedBiome(player) || !rightClickRefused(player, here)) {
                helper.fail("the biome is locked, but the player is not treated as standing in it");
                return;
            }

            // What saving the stage in the editor does: new stage data, then the cache reset.
            stage.setBiomes(new ArrayList<>());
            StageLocks.stagesChanged();
            BiomeLockHandler.invalidateAll();
            EventBus.post(new PlayerTickEvent.Post(player));

            if (BiomeLockHandler.isInsideLockedBiome(player)) {
                helper.fail("no stage locks a biome any more, but the player still counts as inside one");
                return;
            }
            if (rightClickRefused(player, here)) {
                helper.fail("no stage locks a biome any more, but a click in it is still refused");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            BiomeLockHandler.clearPlayer(player.getUUID());
        }
    }
}
