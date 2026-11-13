package net.bananemdnsa.historystages.fabric.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.bananemdnsa.historystages.platform.event.entity.player.PlayerInteractEvent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Platform-bus priorities against other mods' Fabric callbacks. The bridge registers one listener
 * per priority in its own callback phase, HIGHEST and HIGH before Fabric's default phase, NORMAL in
 * it, LOW and LOWEST after it. A "native" listener here is a plain default-phase callback, which is
 * what another mod registers.
 *
 * <p>Fabric callbacks cannot be unregistered, so the native listeners are hung up once and only
 * act while a test has armed them, and only for that test's player.
 */
public class BridgePriorityTests {

    private static final BlockPos POS = BlockPos.ZERO;

    private static final AtomicReference<Function<Player, InteractionResult>> NATIVE_USE_BLOCK = new AtomicReference<>();
    private static final AtomicReference<Consumer<Player>> NATIVE_USE_ENTITY = new AtomicReference<>();
    private static boolean nativeListenersRegistered;

    private static synchronized void registerNativeListeners() {
        if (nativeListenersRegistered) {
            return;
        }
        nativeListenersRegistered = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Function<Player, InteractionResult> armed = NATIVE_USE_BLOCK.get();
            return armed == null ? InteractionResult.PASS : armed.apply(player);
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Consumer<Player> armed = NATIVE_USE_ENTITY.get();
            if (armed != null) {
                armed.accept(player);
            }
            return InteractionResult.PASS;
        });
    }

    /** HIGHEST runs before another mod's default-phase callback, LOWEST after it. */
    @GameTest(skyAccess = true)
    public void platformPrioritiesInterleaveWithNativeListeners(GameTestHelper helper) {
        registerNativeListeners();
        ServerPlayer player = TestPlayers.createConnected(helper);
        List<String> seen = new ArrayList<>();
        Consumer<PlayerInteractEvent.RightClickBlock> highest = mine(player, event -> seen.add("platform-highest"));
        Consumer<PlayerInteractEvent.RightClickBlock> lowest = mine(player, event -> seen.add("platform-lowest"));
        NATIVE_USE_BLOCK.set(clicker -> {
            if (clicker == player) {
                seen.add("native-normal");
            }
            return InteractionResult.PASS;
        });
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGHEST, false, highest);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, false, lowest);
        try {
            BridgeCancelTests.rightClickChest(helper, player);
        } finally {
            NATIVE_USE_BLOCK.set(null);
            EventBus.unregister(highest);
            EventBus.unregister(lowest);
        }
        helper.assertTrue(seen.equals(List.of("platform-highest", "native-normal", "platform-lowest")),
                "wrong order: " + seen);
        helper.succeed();
    }

    /**
     * Another mod's callback answers FAIL. Fabric stops the chain on any answer other than PASS, so
     * unlike on NeoForge a LOWEST handler with receiveCanceled never hears about it. HIGHEST still
     * ran first and saw no cancel, and the refusal holds.
     */
    @GameTest(skyAccess = true)
    public void nativeRefusalEndsTheChain(GameTestHelper helper) {
        registerNativeListeners();
        ServerPlayer player = TestPlayers.createConnected(helper);
        List<String> seen = new ArrayList<>();
        Consumer<PlayerInteractEvent.RightClickBlock> highest =
                mine(player, event -> seen.add("platform-highest:" + event.isCanceled()));
        Consumer<PlayerInteractEvent.RightClickBlock> lowestObserver =
                mine(player, event -> seen.add("platform-lowest-observer:" + event.isCanceled()));
        NATIVE_USE_BLOCK.set(clicker -> {
            if (clicker != player) {
                return InteractionResult.PASS;
            }
            seen.add("native-normal");
            return InteractionResult.FAIL;
        });
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGHEST, true, highest);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, true, lowestObserver);
        try {
            BridgeCancelTests.rightClickChest(helper, player);
        } finally {
            NATIVE_USE_BLOCK.set(null);
            EventBus.unregister(highest);
            EventBus.unregister(lowestObserver);
        }
        helper.assertTrue(seen.equals(List.of("platform-highest:false", "native-normal")), "seen: " + seen);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "the native refusal did not hold");
        helper.succeed();
    }

    /**
     * A platform cancel at HIGHEST ends the Fabric chain too, but the bridge first hands the
     * cancelled event to the lower platform priorities, so a receiveCanceled LOWEST handler still
     * sees it, as on NeoForge. Other mods' default-phase callbacks do not run.
     */
    @GameTest(skyAccess = true)
    public void platformCancelStillReachesLowerReceiveCanceled(GameTestHelper helper) {
        registerNativeListeners();
        ServerPlayer player = TestPlayers.createConnected(helper);
        List<String> seen = new ArrayList<>();
        Consumer<PlayerInteractEvent.RightClickBlock> highest = mine(player, event -> {
            seen.add("platform-highest-cancel");
            event.setCanceled(true);
        });
        Consumer<PlayerInteractEvent.RightClickBlock> lowestObserver =
                mine(player, event -> seen.add("platform-lowest-observer:" + event.isCanceled()));
        Consumer<PlayerInteractEvent.RightClickBlock> lowestSkipped = mine(player, event -> seen.add("platform-lowest-skipped"));
        NATIVE_USE_BLOCK.set(clicker -> {
            if (clicker == player) {
                seen.add("native-normal");
            }
            return InteractionResult.PASS;
        });
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.HIGHEST, false, highest);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, true, lowestObserver);
        EventBus.addListener(PlayerInteractEvent.RightClickBlock.class, EventPriority.LOWEST, false, lowestSkipped);
        try {
            BridgeCancelTests.rightClickChest(helper, player);
        } finally {
            NATIVE_USE_BLOCK.set(null);
            EventBus.unregister(highest);
            EventBus.unregister(lowestObserver);
            EventBus.unregister(lowestSkipped);
        }
        helper.assertTrue(seen.equals(List.of("platform-highest-cancel", "platform-lowest-observer:true")),
                "seen: " + seen);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "the platform cancel did not hold");
        helper.succeed();
    }

    /**
     * One interact packet is one Fabric {@code UseEntityCallback}, and the bridge turns it into
     * exactly one platform {@code EntityInteractSpecific} and one {@code EntityInteract}.
     */
    @GameTest(skyAccess = true)
    public void entityInteractIsPostedOncePerInteraction(GameTestHelper helper) {
        registerNativeListeners();
        ServerPlayer player = TestPlayers.createConnected(helper);
        Cow cow = helper.spawn(EntityTypes.COW, POS.above());
        player.snapTo(cow.getX(), cow.getY(), cow.getZ() - 1.0, 0.0F, 0.0F);
        AtomicInteger nativeCount = new AtomicInteger();
        AtomicInteger specificCount = new AtomicInteger();
        AtomicInteger plainCount = new AtomicInteger();
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
        NATIVE_USE_ENTITY.set(clicker -> {
            if (clicker == player) {
                nativeCount.incrementAndGet();
            }
        });
        EventBus.addListener(PlayerInteractEvent.EntityInteractSpecific.class, specific);
        EventBus.addListener(PlayerInteractEvent.EntityInteract.class, plain);
        try {
            player.connection.handleInteract(
                    new ServerboundInteractPacket(cow.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
        } finally {
            NATIVE_USE_ENTITY.set(null);
            EventBus.unregister(specific);
            EventBus.unregister(plain);
        }
        String counts = "native=" + nativeCount + " specific=" + specificCount + " plain=" + plainCount;
        helper.assertTrue(nativeCount.get() == 1 && specificCount.get() == 1 && plainCount.get() == 1, counts);
        helper.succeed();
    }

    private static <T extends PlayerInteractEvent> Consumer<T> mine(ServerPlayer player, Consumer<T> body) {
        return event -> {
            if (event.getEntity() == player) {
                body.accept(event);
            }
        };
    }
}
