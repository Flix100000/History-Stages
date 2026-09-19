package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneSelection;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.events.lock.ZoneLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Marking zone corners with the configured item, driven the way the server drives it.
 *
 * <p>Through {@link ForgeHooks} rather than by calling the handler: those are the exact calls the
 * server's own click handling makes, so whether the handler is subscribed, what it is subscribed
 * to and what runs ahead of it are all part of what is under test. The command path marks through
 * the same store and needs none of that, which is why it could work while the item did not.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneMarkingTests {

    private ZoneMarkingTests() {}

    /** Swallows what marking sends back — the actionbar line and the selection sync. */
    private static final class SilentConnection extends Connection {
        SilentConnection() {
            super(PacketFlow.CLIENTBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
    }

    /** An operator, sneaking, with the default marker item in hand. */
    private static ServerPlayer marker(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new SilentConnection(), player);
        // Not PlayerList.op: the game test server hands out operator level 0, which is below the
        // bar marking sits behind. Level 4 is what a singleplayer host with cheats gets.
        helper.getLevel().getServer().getPlayerList().getOps()
                .add(new ServerOpListEntry(player.getGameProfile(), 4, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        return player;
    }

    private static void release(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().deop(player.getGameProfile());
        ZoneSelection.clear(player.getUUID());
    }

    @GameTest(template = "empty")
    public static void sneakLeftClickWithTheMarkerSetsTheFirstCorner(GameTestHelper helper) {
        ServerPlayer player = marker(helper);
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            ForgeHooks.onLeftClickBlock(player, pos, Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);

            ZoneSelection.Selection selection = ZoneSelection.of(player.getUUID());
            if (selection == null || !selection.hasFirst()) {
                helper.fail("sneak + left click with a stick set no first corner");
                return;
            }
            helper.succeed();
        } finally {
            release(helper, player);
        }
    }

    @GameTest(template = "empty")
    public static void sneakRightClickWithTheMarkerSetsTheSecondCorner(GameTestHelper helper) {
        ServerPlayer player = marker(helper);
        try {
            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

            ZoneSelection.Selection selection = ZoneSelection.of(player.getUUID());
            if (selection == null || !selection.hasSecond()) {
                helper.fail("sneak + right click with a stick set no second corner");
                return;
            }
            helper.succeed();
        } finally {
            release(helper, player);
        }
    }

    /**
     * The marker on a block some stage locks.
     *
     * <p>Every lock that watches clicks — a locked block, a zone, a structure, a biome — refuses
     * the interaction and, in doing so, turned the click away before marking ever saw it. Stone is
     * the case that gives it away in practice: it is what most of the world is made of.
     */
    @GameTest(template = "empty")
    public static void markingWorksOnABlockAStageLocks(GameTestHelper helper) {
        ServerPlayer player = marker(helper);
        try {
            GameTestStages.global("marker_on_locked_block", stage -> stage.setItemEntries(
                    new ArrayList<>(List.of(new ItemEntry("minecraft:stone")))));
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

            ForgeHooks.onLeftClickBlock(player, pos, Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

            ZoneSelection.Selection selection = ZoneSelection.of(player.getUUID());
            if (selection == null || !selection.hasFirst() || !selection.hasSecond()) {
                helper.fail("stone sits on a locked stage and the marker on it set "
                        + (selection == null ? "no corner at all"
                                : "first=" + selection.hasFirst() + " second=" + selection.hasSecond())
                        + " - a lock turned the marking click away");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            release(helper, player);
        }
    }

    /**
     * The marker inside a locked zone, which by default refuses both clicks.
     *
     * <p>The zone lock cancels the click outright rather than flagging it, and a cancelled event
     * never reaches a listener that does not ask for cancelled ones — so this is the order the two
     * listeners run in, not a flag one of them reads. Drawing the next zone while standing in the
     * last one is the ordinary way to work.
     */
    @GameTest(template = "empty")
    public static void markingWorksInsideALockedZone(GameTestHelper helper) {
        ServerPlayer player = marker(helper);
        try {
            ZoneEntry zone = new ZoneEntry();
            zone.setName("around the marker");
            zone.setDimension(helper.getLevel().dimension().location().toString());
            zone.setShapes(List.of(ZoneShape.cube(
                    -30_000_000, 0, -30_000_000, 30_000_000, 0, 30_000_000, true)));
            GameTestStages.global("marker_in_zone", stage ->
                    stage.setZones(new ArrayList<>(List.of(zone))));
            // The zone lock reads the player's standing from its own tick, not from the click.
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));

            BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
            ForgeHooks.onLeftClickBlock(player, pos, Direction.UP,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

            ZoneSelection.Selection selection = ZoneSelection.of(player.getUUID());
            if (selection == null || !selection.hasFirst() || !selection.hasSecond()) {
                helper.fail("inside a locked zone the marker set "
                        + (selection == null ? "no corner at all"
                                : "first=" + selection.hasFirst() + " second=" + selection.hasSecond())
                        + " - the zone lock took the click first");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
            release(helper, player);
        }
    }
}
