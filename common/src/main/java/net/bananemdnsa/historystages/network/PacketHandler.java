package net.bananemdnsa.historystages.network;
import net.bananemdnsa.historystages.network.serverbound.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStructureVizPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.serverbound.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStageDependencyPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphConfigPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleIndividualStageLockPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteStagePacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStagePacket;
import net.bananemdnsa.historystages.network.serverbound.CreateFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.RenameFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteFolderPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveStagesPacket;
import net.bananemdnsa.historystages.network.serverbound.MoveFoldersPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestIndividualStatesPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestEditorDataPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestTradeGoodsPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphPositionsPacket;
import net.bananemdnsa.historystages.network.serverbound.RearrangeGraphPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.bananemdnsa.historystages.network.serverbound.SaveDisguisePacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphStylePacket;
import net.bananemdnsa.historystages.network.serverbound.SaveStylePresetPacket;
import net.bananemdnsa.historystages.network.serverbound.DeleteStylePresetPacket;
import net.bananemdnsa.historystages.network.serverbound.AssignStylePresetPacket;
import net.bananemdnsa.historystages.network.serverbound.PedestalControlPacket;
import net.bananemdnsa.historystages.network.serverbound.TakeLecternScrollPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncLockBordersPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.LockFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTradeGoodsPacket;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncTemporaryCountsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStatesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncLostStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.EditorSyncPacket;
import net.bananemdnsa.historystages.network.clientbound.StageUnlockedToastPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.OpenLecternScrollPacket;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import dev.architectury.networking.NetworkManager;
import net.bananemdnsa.historystages.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandler {

    public static void sendToAll(SyncStagesPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendToPlayer(SyncStagesPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendToastToAll(StageUnlockedToastPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendToastToPlayer(StageUnlockedToastPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    /** Never broadcast: a zone selection is the marking player's own business. */
    public static void sendZoneSelection(
            net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket packet,
            ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    /** Also per player: which zones are locked differs between them, and so does what they see. */
    public static void sendZoneShapes(
            net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket packet,
            ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendDefinitionsToPlayer(SyncStageDefinitionsPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendDefinitionsToAll(SyncStageDefinitionsPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendConfigToPlayer(SyncConfigPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendConfigToAll(SyncConfigPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendGraphConfigToPlayer(SyncGraphConfigPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendGraphConfigToAll(SyncGraphConfigPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendVisualConfigToPlayer(SyncVisualConfigPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendVisualConfigToAll(SyncVisualConfigPacket packet) {
        sendToAllPlayers(packet);
    }

    public static void sendIndividualStagesToPlayer(SyncIndividualStagesPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendLostStagesToPlayer(SyncLostStagesPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendStructureRegistryToPlayer(SyncStructureRegistryPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    // Send lock feedback (dimension or mob) to a specific player — client decides display
    public static void sendLockFeedbackToPlayer(LockFeedbackPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    /** Why the trade window that just opened is empty. Sent right after the window itself. */
    public static void sendTradeLockedToPlayer(TradeLockedPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendLockBordersToPlayer(SyncLockBordersPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendEditorFeedback(EditorFeedbackPacket packet, ServerPlayer player) {
        NetworkManager.sendToPlayer(player, packet);
    }

    public static void sendToServer(Object packet) {
        NetworkManager.sendToServer((CustomPacketPayload) packet);
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

    // NeoForge's PacketDistributor.sendToAllPlayers, which also no-ops without a server.
    private static void sendToAllPlayers(CustomPacketPayload payload) {
        MinecraftServer server = Services.currentServer();
        if (server != null) NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), payload);
    }
}
