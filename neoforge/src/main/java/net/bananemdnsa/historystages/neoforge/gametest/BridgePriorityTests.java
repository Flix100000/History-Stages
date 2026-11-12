package net.bananemdnsa.historystages.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Platform-bus priorities have to hold against other mods' NeoForge listeners, not only among our
 * own handlers: a LOWEST handler is there to see what everyone else decided. Each test hangs a
 * native NeoForge listener next to platform listeners and checks who saw what.
 *
 * <p>All listeners only react to the test's own player and come off the buses when the test ends.
 */
public final class BridgePriorityTests {

    private static final BlockPos POS = BlockPos.ZERO;

    private BridgePriorityTests() {}

    static void collect(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("bridge/platform_priorities_interleave_with_native_listeners",
                BridgePriorityTests::platformPrioritiesInterleaveWithNativeListeners);
        tests.put("bridge/entity_interact_is_posted_once_per_interaction",
                BridgePriorityTests::entityInteractIsPostedOncePerInteraction);
        tests.put("bridge/natively_cancelled_entity_interact_reaches_receive_canceled",
                BridgePriorityTests::nativelyCancelledEntityInteractReachesReceiveCanceled);
    }

    /**
     * A native NORMAL listener cancels. The platform HIGHEST handler runs before it and sees no
     * cancel; the platform LOWEST handler with receiveCanceled runs after it and sees the cancel; the
     * LOWEST handler without receiveCanceled does not run at all.
     */
    public static void platformPrioritiesInterleaveWithNativeListeners(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        List<String> seen = new ArrayList<>();
        Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> nativeCanceller = event -> {
            if (event.getEntity() == player) {
                seen.add("native-normal");
                event.setCanceled(true);
            }
        };
        Consumer<PlayerInteractEvent.RightClickBlock> highest = event -> {
            if (event.getEntity() == player) {
                seen.add("platform-highest:" + event.isCanceled());
            }
        };
        Consumer<PlayerInteractEvent.RightClickBlock> lowestObserver = event -> {
            if (event.getEntity() == player) {
                seen.add("platform-lowest-observer:" + event.isCanceled());
            }
        };
        Consumer<PlayerInteractEvent.RightClickBlock> lowestSkipped = event -> {
            if (event.getEntity() == player) {
                seen.add("platform-lowest-skipped");
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, nativeCanceller);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGHEST, true, highest);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, true, lowestObserver);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, false, lowestSkipped);
        try {
            helper.setBlock(POS, Blocks.CHEST);
            BlockPos absolute = helper.absolutePos(POS);
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        } finally {
            NeoForge.EVENT_BUS.unregister(nativeCanceller);
            EventBus.unregister(highest);
            EventBus.unregister(lowestObserver);
            EventBus.unregister(lowestSkipped);
        }
        helper.assertTrue(seen.equals(List.of("platform-highest:false", "native-normal", "platform-lowest-observer:true")),
                "wrong order or visibility: " + seen);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "the native cancel did not hold");
        helper.succeed();
    }

    /**
     * Vanilla 26.2 sends one interact packet and calls {@code interactOn} once per click, and
     * NeoForge fires one {@code EntityInteract} from it. The bridge turns that into exactly one
     * platform {@code EntityInteractSpecific} and one {@code EntityInteract}, as main expects.
     */
    public static void entityInteractIsPostedOncePerInteraction(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Cow cow = helper.spawn(EntityTypes.COW, POS.above());
        AtomicInteger nativeCount = new AtomicInteger();
        AtomicInteger specificCount = new AtomicInteger();
        AtomicInteger plainCount = new AtomicInteger();
        Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract> nativeCounter = event -> {
            if (event.getEntity() == player) {
                nativeCount.incrementAndGet();
            }
        };
        Consumer<PlayerInteractEvent.EntityInteractSpecific> specific = event -> {
            if (event.getEntity() == player) {
                specificCount.incrementAndGet();
            }
        };
        Consumer<PlayerInteractEvent.EntityInteract> plain = event -> {
            if (event.getEntity() == player) {
                plainCount.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, true,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, nativeCounter);
        EventBus.addListener(PlayerInteractEvent.EntityInteractSpecific.class, specific);
        EventBus.addListener(PlayerInteractEvent.EntityInteract.class, plain);
        try {
            player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
        } finally {
            NeoForge.EVENT_BUS.unregister(nativeCounter);
            EventBus.unregister(specific);
            EventBus.unregister(plain);
        }
        String counts = "native=" + nativeCount + " specific=" + specificCount + " plain=" + plainCount;
        helper.assertTrue(nativeCount.get() == 1 && specificCount.get() == 1 && plainCount.get() == 1, counts);
        helper.succeed();
    }

    /** A native cancel before the bridge must still reach platform handlers that asked for cancelled events. */
    public static void nativelyCancelledEntityInteractReachesReceiveCanceled(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Cow cow = helper.spawn(EntityTypes.COW, POS.above());
        List<String> seen = new ArrayList<>();
        Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract> nativeCanceller = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        Consumer<PlayerInteractEvent.EntityInteractSpecific> specific = event -> {
            if (event.getEntity() == player) {
                seen.add("specific:" + event.isCanceled());
            }
        };
        Consumer<PlayerInteractEvent.EntityInteract> plain = event -> {
            if (event.getEntity() == player) {
                seen.add("plain:" + event.isCanceled());
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, false,
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, nativeCanceller);
        EventBus.addListener(PlayerInteractEvent.EntityInteractSpecific.class, EventPriority.NORMAL, true, specific);
        EventBus.addListener(PlayerInteractEvent.EntityInteract.class, EventPriority.NORMAL, true, plain);
        try {
            player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
        } finally {
            NeoForge.EVENT_BUS.unregister(nativeCanceller);
            EventBus.unregister(specific);
            EventBus.unregister(plain);
        }
        helper.assertTrue(seen.equals(List.of("specific:true", "plain:true")), "seen: " + seen);
        helper.succeed();
    }
}
