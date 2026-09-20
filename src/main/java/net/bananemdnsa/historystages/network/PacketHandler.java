package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.OpenLecternScrollPacket;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStatesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncLockBordersPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTradeGoodsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.network.serverbound.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.CreateFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteStagePacket;
import net.bananemdnsa.historystages.network.serverbound.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveFoldersPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveStagesPacket;
import net.bananemdnsa.historystages.network.serverbound.PedestalControlPacket;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.RenameFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestEditorDataPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestIndividualStatesPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStageDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestTradeGoodsPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestZoneSelectionPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStagePacket;
import net.bananemdnsa.historystages.network.serverbound.TakeLecternScrollPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleIndividualStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStructureVizPacket;

import net.bananemdnsa.historystages.platform.IPayloadContext;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.ServerHolder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.BiConsumer;

public class PacketHandler {

    /**
     * One packet, described once.
     *
     * <p>A payload is declared in two places on Fabric: the codec, which both sides need, and the
     * receiver, which only the receiving side registers. Writing a packet out twice is how one
     * ends up with a codec and no receiver, and a packet that travels and is then dropped without
     * a word. These two lists are the single declaration, and each side walks them for its half.
     */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {}

    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {}

    public static final List<Clientbound<?>> CLIENTBOUND = List.of(
            new Clientbound<>(SyncStagesPacket.TYPE, SyncStagesPacket.STREAM_CODEC, SyncStagesPacket::handle),
            new Clientbound<>(StageUnlockedToastPacket.TYPE, StageUnlockedToastPacket.STREAM_CODEC, StageUnlockedToastPacket::handle),
            new Clientbound<>(EditorSyncPacket.TYPE, EditorSyncPacket.STREAM_CODEC, EditorSyncPacket::handle),
            new Clientbound<>(SyncStageDefinitionsPacket.TYPE, SyncStageDefinitionsPacket.STREAM_CODEC, SyncStageDefinitionsPacket::handle),
            new Clientbound<>(SyncConfigPacket.TYPE, SyncConfigPacket.STREAM_CODEC, SyncConfigPacket::handle),
            new Clientbound<>(SyncGraphConfigPacket.TYPE, SyncGraphConfigPacket.STREAM_CODEC, SyncGraphConfigPacket::handle),
            new Clientbound<>(SyncVisualConfigPacket.TYPE, SyncVisualConfigPacket.STREAM_CODEC, SyncVisualConfigPacket::handle),
            new Clientbound<>(SyncIndividualStagesPacket.TYPE, SyncIndividualStagesPacket.STREAM_CODEC, SyncIndividualStagesPacket::handle),
            new Clientbound<>(SyncTemporaryCountsPacket.TYPE, SyncTemporaryCountsPacket.STREAM_CODEC, SyncTemporaryCountsPacket::handle),
            new Clientbound<>(SyncIndividualStatesPacket.TYPE, SyncIndividualStatesPacket.STREAM_CODEC, SyncIndividualStatesPacket::handle),
            new Clientbound<>(OpenLecternScrollPacket.TYPE, OpenLecternScrollPacket.STREAM_CODEC, OpenLecternScrollPacket::handle),
            new Clientbound<>(SyncDependencyStatusPacket.TYPE, SyncDependencyStatusPacket.STREAM_CODEC, SyncDependencyStatusPacket::handle),
            new Clientbound<>(SyncStructureRegistryPacket.TYPE, SyncStructureRegistryPacket.STREAM_CODEC, SyncStructureRegistryPacket::handle),
            new Clientbound<>(LockFeedbackPacket.TYPE, LockFeedbackPacket.STREAM_CODEC, LockFeedbackPacket::handle),
            new Clientbound<>(TradeLockedPacket.TYPE, TradeLockedPacket.STREAM_CODEC, TradeLockedPacket::handle),
            new Clientbound<>(SyncTradeGoodsPacket.TYPE, SyncTradeGoodsPacket.STREAM_CODEC, SyncTradeGoodsPacket::handle),
            new Clientbound<>(EditorFeedbackPacket.TYPE, EditorFeedbackPacket.STREAM_CODEC, EditorFeedbackPacket::handle),
            new Clientbound<>(SyncLockBordersPacket.TYPE, SyncLockBordersPacket.STREAM_CODEC, SyncLockBordersPacket::handle),
            new Clientbound<>(SyncZoneSelectionPacket.TYPE, SyncZoneSelectionPacket.STREAM_CODEC, SyncZoneSelectionPacket::handle),
            new Clientbound<>(SyncZoneShapesPacket.TYPE, SyncZoneShapesPacket.STREAM_CODEC, SyncZoneShapesPacket::handle));

    public static final List<Serverbound<?>> SERVERBOUND = List.of(
            new Serverbound<>(RequestEditorDataPacket.TYPE, RequestEditorDataPacket.STREAM_CODEC, RequestEditorDataPacket::handle),
            new Serverbound<>(RequestTradeGoodsPacket.TYPE, RequestTradeGoodsPacket.STREAM_CODEC, RequestTradeGoodsPacket::handle),
            new Serverbound<>(RequestTemporaryCountsPacket.TYPE, RequestTemporaryCountsPacket.STREAM_CODEC, RequestTemporaryCountsPacket::handle),
            new Serverbound<>(RequestIndividualStatesPacket.TYPE, RequestIndividualStatesPacket.STREAM_CODEC, RequestIndividualStatesPacket::handle),
            new Serverbound<>(SaveStagePacket.TYPE, SaveStagePacket.STREAM_CODEC, SaveStagePacket::handle),
            new Serverbound<>(DeleteStagePacket.TYPE, DeleteStagePacket.STREAM_CODEC, DeleteStagePacket::handle),
            new Serverbound<>(CreateFolderPacket.TYPE, CreateFolderPacket.STREAM_CODEC, CreateFolderPacket::handle),
            new Serverbound<>(RenameFolderPacket.TYPE, RenameFolderPacket.STREAM_CODEC, RenameFolderPacket::handle),
            new Serverbound<>(DeleteFolderPacket.TYPE, DeleteFolderPacket.STREAM_CODEC, DeleteFolderPacket::handle),
            new Serverbound<>(MoveStagesPacket.TYPE, MoveStagesPacket.STREAM_CODEC, MoveStagesPacket::handle),
            new Serverbound<>(MoveFoldersPacket.TYPE, MoveFoldersPacket.STREAM_CODEC, MoveFoldersPacket::handle),
            new Serverbound<>(ToggleStageLockPacket.TYPE, ToggleStageLockPacket.STREAM_CODEC, ToggleStageLockPacket::handle),
            new Serverbound<>(ToggleIndividualStageLockPacket.TYPE, ToggleIndividualStageLockPacket.STREAM_CODEC, ToggleIndividualStageLockPacket::handle),
            new Serverbound<>(SaveConfigPacket.TYPE, SaveConfigPacket.STREAM_CODEC, SaveConfigPacket::handle),
            new Serverbound<>(SaveGraphConfigPacket.TYPE, SaveGraphConfigPacket.STREAM_CODEC, SaveGraphConfigPacket::handle),
            new Serverbound<>(CheckDependencyPacket.TYPE, CheckDependencyPacket.STREAM_CODEC, CheckDependencyPacket::handle),
            new Serverbound<>(RequestStageDependencyPacket.TYPE, RequestStageDependencyPacket.STREAM_CODEC, RequestStageDependencyPacket::handle),
            new Serverbound<>(DepositDependencyPacket.TYPE, DepositDependencyPacket.STREAM_CODEC, DepositDependencyPacket::handle),
            new Serverbound<>(RequestStructureDebugPacket.TYPE, RequestStructureDebugPacket.STREAM_CODEC, RequestStructureDebugPacket::handle),
            new Serverbound<>(ToggleStructureVizPacket.TYPE, ToggleStructureVizPacket.STREAM_CODEC, ToggleStructureVizPacket::handle),
            new Serverbound<>(RequestClusterShapesPacket.TYPE, RequestClusterShapesPacket.STREAM_CODEC, RequestClusterShapesPacket::handle),
            new Serverbound<>(SaveGraphPositionsPacket.TYPE, SaveGraphPositionsPacket.STREAM_CODEC, SaveGraphPositionsPacket::handle),
            new Serverbound<>(RearrangeGraphPacket.TYPE, RearrangeGraphPacket.STREAM_CODEC, RearrangeGraphPacket::handle),
            new Serverbound<>(SaveStageGraphInfoPacket.TYPE, SaveStageGraphInfoPacket.STREAM_CODEC, SaveStageGraphInfoPacket::handle),
            new Serverbound<>(SaveStageGraphStylePacket.TYPE, SaveStageGraphStylePacket.STREAM_CODEC, SaveStageGraphStylePacket::handle),
            new Serverbound<>(PedestalControlPacket.TYPE, PedestalControlPacket.STREAM_CODEC, PedestalControlPacket::handle),
            new Serverbound<>(TakeLecternScrollPacket.TYPE, TakeLecternScrollPacket.STREAM_CODEC, TakeLecternScrollPacket::handle),
            new Serverbound<>(RequestZoneSelectionPacket.TYPE, RequestZoneSelectionPacket.STREAM_CODEC, RequestZoneSelectionPacket::handle));

    /**
     * Registers every codec, and the receivers for packets arriving at the server.
     *
     * <p>The clientbound receivers are not here: ClientPlayNetworking does not exist on a
     * dedicated server. They are bound in the client initializer, off the same list.
     *
     * <p><strong>Lost in the move:</strong> NeoForge gates a channel on a version string, and this
     * mod bumped it to "2" when the stage sync went gzipped, so an old client meets the
     * "incompatible mod" screen rather than a decoder exception. Fabric has no such gate: a 5.x
     * client meeting a 6.x server fails while decoding and is disconnected. Same outcome, worse
     * message, and nothing on this side can improve on it.
     */
    public static void register() {
        for (Clientbound<?> packet : CLIENTBOUND) {
            registerClientboundCodec(packet);
        }
        for (Serverbound<?> packet : SERVERBOUND) {
            registerServerbound(packet);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundCodec(Clientbound<T> packet) {
        PayloadTypeRegistry.playS2C().register(packet.type(), packet.codec());
    }

    private static <T extends CustomPacketPayload> void registerServerbound(Serverbound<T> packet) {
        PayloadTypeRegistry.playC2S().register(packet.type(), packet.codec());
        ServerPlayNetworking.registerGlobalReceiver(packet.type(),
                (payload, context) -> packet.handler().accept(payload, IPayloadContext.of(context.player())));
    }

    // ------------------------------------------------------------------ send

    private static void send(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, packet);
    }

    /**
     * Everyone currently online. NeoForge reaches the player list through its own hooks; here the
     * server comes from ServerHolder, and a null one means there is nobody to send to yet.
     */
    private static void sendAll(CustomPacketPayload packet) {
        MinecraftServer server = ServerHolder.get();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    public static void sendToAll(SyncStagesPacket packet) {
        sendAll(packet);
    }

    public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendToastToAll(StageUnlockedToastPacket packet) {
        sendAll(packet);
    }

    public static void sendToastToPlayer(StageUnlockedToastPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    /** Never broadcast: a zone selection is the marking player's own business. */
    public static void sendZoneSelection(SyncZoneSelectionPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    /** Also per player: which zones are locked differs between them, and so does what they see. */
    public static void sendZoneShapes(SyncZoneShapesPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendDefinitionsToPlayer(SyncStageDefinitionsPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendDefinitionsToAll(SyncStageDefinitionsPacket packet) {
        sendAll(packet);
    }

    public static void sendConfigToPlayer(SyncConfigPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendConfigToAll(SyncConfigPacket packet) {
        sendAll(packet);
    }

    public static void sendGraphConfigToPlayer(SyncGraphConfigPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendGraphConfigToAll(SyncGraphConfigPacket packet) {
        sendAll(packet);
    }

    public static void sendVisualConfigToPlayer(SyncVisualConfigPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendVisualConfigToAll(SyncVisualConfigPacket packet) {
        sendAll(packet);
    }

    public static void sendIndividualStagesToPlayer(SyncIndividualStagesPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendStructureRegistryToPlayer(SyncStructureRegistryPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    // Send lock feedback (dimension or mob) to a specific player — client decides display
    public static void sendLockFeedbackToPlayer(LockFeedbackPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    /** Why the trade window that just opened is empty. Sent right after the window itself. */
    public static void sendTradeLockedToPlayer(TradeLockedPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendLockBordersToPlayer(SyncLockBordersPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    public static void sendEditorFeedback(EditorFeedbackPacket packet, ServerPlayer player) {
        send(player, packet);
    }

    /**
     * Asks for whatever a change to what is gated needs. Carried out by
     * {@link #runRequestedLockReload} at the end of the tick.
     *
     * <p>Only asks, so that unlocking three stages in the same tick — a quest handing out a
     * bundle, an auto-trigger catching up — ends in one piece of work rather than three.
     */
    public static void reloadForLockChange(MinecraftServer server) {
        reloadRequested = true;
    }

    /**
     * Carries out a requested reload, at most once per tick, and picks the cheapest one that will
     * do.
     *
     * <p><strong>Resending is the normal case.</strong> The gate itself needs nothing: it is
     * consulted when a recipe is asked for, so it flips the moment the stage does. What the resend
     * is for is the clients — it is what makes JEI notice, and without it items hidden by a stage
     * never come back after an unlock. Two GameTests hold both halves of that.
     *
     * <p><strong>A full datapack reload only when the set of hidden recipes actually changed.</strong>
     * A machine that takes the whole recipe list and searches it itself — Create's basin, and most
     * modded machines — keeps that list until a datapack reload tells it to let go, so without one
     * it would take a lock correctly and then stay stuck after the unlock. But it only needs to be
     * told when the list would come out different, and {@code VisibleRecipes.gatedSetChanged}
     * answers exactly that by working the set out and comparing it. A stage gating blocks, biomes
     * or mobs costs nothing; so does saving a stage in the editor without changing what it gates.
     *
     * <p>The reason for being this careful: reloading every datapack on a large modpack freezes
     * the server for as long as it takes, and this can be reached from an auto-trigger, which
     * fires while people are playing. That is why commit {@code ca3988f} took the full reload out
     * in the first place — it is back only where nothing else will do.
     */
    public static void runRequestedLockReload(MinecraftServer server) {
        if (!reloadRequested) return;
        reloadRequested = false;

        if (!net.bananemdnsa.historystages.data.lock.VisibleRecipes.gatedSetChanged(
                server.getRecipeManager().getOrderedRecipes())) {
            resyncRecipes(server);
            return;
        }

        server.reloadResources(server.getPackRepository().getSelectedIds())
                .exceptionally(e -> {
                    DebugLogger.warn("Recipe Locks",
                            "Reloading datapacks after a stage change failed: " + e.getMessage()
                                    + ". Machines that keep their own copy of the recipe list may "
                                    + "still be going by the old one until /reload.");
                    return null;
                });
    }

    /**
     * Resends the recipe list, and the recipe book behind it.
     *
     * <p><strong>Never send the list on its own.</strong> {@code handleUpdateRecipes} throws away
     * every {@code RecipeCollection} the client had and builds new ones, and a new collection
     * knows none of the player's recipes until the book is resent — so the vanilla recipe book at
     * the crafting table goes blank and stays blank, past an F3+T, until the player rejoins.
     *
     * <p>Vanilla pairs the two everywhere it sends them: on join and in
     * {@code PlayerList.reloadResources}, which is also what covers the reload branch above.
     *
     * <p>{@code getOrderedRecipes} rather than {@code getRecipes} because that is what vanilla
     * puts in this packet — and because {@code getRecipes} is gated on the server now, so sending
     * it would take the locked recipes off every client and out of their recipe book.
     */
    private static void resyncRecipes(MinecraftServer server) {
        ClientboundUpdateRecipesPacket recipePacket = new ClientboundUpdateRecipesPacket(
                server.getRecipeManager().getOrderedRecipes());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(recipePacket);
            p.getRecipeBook().sendInitialRecipeBook(p);
        }
    }

    private static volatile boolean reloadRequested;
}
