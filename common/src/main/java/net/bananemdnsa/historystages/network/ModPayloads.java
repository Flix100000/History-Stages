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
import dev.architectury.networking.NetworkManager;
import net.bananemdnsa.historystages.platform.IPayloadContext;
import net.bananemdnsa.historystages.platform.Services;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.BiConsumer;

/**
 * Every payload, registered over Architectury's NetworkManager. Called once from
 * {@code HistoryStages.init()}.
 *
 * <p>NeoForge's registrar took a protocol version ("2" since the gzipped definitions sync), so a
 * 5.x client got the "incompatible mod" screen. Architectury has no such version; a mismatched
 * client now fails to decode instead.
 */
public final class ModPayloads {

    private ModPayloads() {}

    public static void register() {
        clientbound(SyncStagesPacket.TYPE, SyncStagesPacket.STREAM_CODEC, SyncStagesPacket::handle);
        clientbound(StageUnlockedToastPacket.TYPE, StageUnlockedToastPacket.STREAM_CODEC, StageUnlockedToastPacket::handle);
        clientbound(EditorSyncPacket.TYPE, EditorSyncPacket.STREAM_CODEC, EditorSyncPacket::handle);
        clientbound(SyncStageDefinitionsPacket.TYPE, SyncStageDefinitionsPacket.STREAM_CODEC, SyncStageDefinitionsPacket::handle);
        clientbound(SyncConfigPacket.TYPE, SyncConfigPacket.STREAM_CODEC, SyncConfigPacket::handle);
        clientbound(SyncGraphConfigPacket.TYPE, SyncGraphConfigPacket.STREAM_CODEC, SyncGraphConfigPacket::handle);
        clientbound(SyncVisualConfigPacket.TYPE, SyncVisualConfigPacket.STREAM_CODEC, SyncVisualConfigPacket::handle);
        clientbound(SyncIndividualStagesPacket.TYPE, SyncIndividualStagesPacket.STREAM_CODEC, SyncIndividualStagesPacket::handle);
        clientbound(SyncTemporaryCountsPacket.TYPE, SyncTemporaryCountsPacket.STREAM_CODEC, SyncTemporaryCountsPacket::handle);
        clientbound(SyncIndividualStatesPacket.TYPE, SyncIndividualStatesPacket.STREAM_CODEC, SyncIndividualStatesPacket::handle);
        clientbound(SyncLostStagesPacket.TYPE, SyncLostStagesPacket.STREAM_CODEC, SyncLostStagesPacket::handle);
        clientbound(OpenLecternScrollPacket.TYPE, OpenLecternScrollPacket.STREAM_CODEC, OpenLecternScrollPacket::handle);
        serverbound(RequestEditorDataPacket.TYPE, RequestEditorDataPacket.STREAM_CODEC, RequestEditorDataPacket::handle);
        serverbound(RequestTradeGoodsPacket.TYPE, RequestTradeGoodsPacket.STREAM_CODEC, RequestTradeGoodsPacket::handle);
        serverbound(RequestTemporaryCountsPacket.TYPE, RequestTemporaryCountsPacket.STREAM_CODEC, RequestTemporaryCountsPacket::handle);
        serverbound(RequestIndividualStatesPacket.TYPE, RequestIndividualStatesPacket.STREAM_CODEC, RequestIndividualStatesPacket::handle);
        serverbound(SaveStagePacket.TYPE, SaveStagePacket.STREAM_CODEC, SaveStagePacket::handle);
        serverbound(DeleteStagePacket.TYPE, DeleteStagePacket.STREAM_CODEC, DeleteStagePacket::handle);
        serverbound(CreateFolderPacket.TYPE, CreateFolderPacket.STREAM_CODEC, CreateFolderPacket::handle);
        serverbound(RenameFolderPacket.TYPE, RenameFolderPacket.STREAM_CODEC, RenameFolderPacket::handle);
        serverbound(DeleteFolderPacket.TYPE, DeleteFolderPacket.STREAM_CODEC, DeleteFolderPacket::handle);
        serverbound(MoveStagesPacket.TYPE, MoveStagesPacket.STREAM_CODEC, MoveStagesPacket::handle);
        serverbound(MoveFoldersPacket.TYPE, MoveFoldersPacket.STREAM_CODEC, MoveFoldersPacket::handle);
        serverbound(ToggleStageLockPacket.TYPE, ToggleStageLockPacket.STREAM_CODEC, ToggleStageLockPacket::handle);
        serverbound(ToggleIndividualStageLockPacket.TYPE, ToggleIndividualStageLockPacket.STREAM_CODEC, ToggleIndividualStageLockPacket::handle);
        serverbound(SaveConfigPacket.TYPE, SaveConfigPacket.STREAM_CODEC, SaveConfigPacket::handle);
        serverbound(SaveGraphConfigPacket.TYPE, SaveGraphConfigPacket.STREAM_CODEC, SaveGraphConfigPacket::handle);
        serverbound(CheckDependencyPacket.TYPE, CheckDependencyPacket.STREAM_CODEC, CheckDependencyPacket::handle);
        serverbound(RequestStageDependencyPacket.TYPE, RequestStageDependencyPacket.STREAM_CODEC, RequestStageDependencyPacket::handle);
        serverbound(DepositDependencyPacket.TYPE, DepositDependencyPacket.STREAM_CODEC, DepositDependencyPacket::handle);
        serverbound(RequestStructureDebugPacket.TYPE, RequestStructureDebugPacket.STREAM_CODEC, RequestStructureDebugPacket::handle);
        serverbound(ToggleStructureVizPacket.TYPE, ToggleStructureVizPacket.STREAM_CODEC, ToggleStructureVizPacket::handle);
        serverbound(RequestClusterShapesPacket.TYPE, RequestClusterShapesPacket.STREAM_CODEC, RequestClusterShapesPacket::handle);
        serverbound(SaveGraphPositionsPacket.TYPE, SaveGraphPositionsPacket.STREAM_CODEC, SaveGraphPositionsPacket::handle);
        serverbound(RearrangeGraphPacket.TYPE, RearrangeGraphPacket.STREAM_CODEC, RearrangeGraphPacket::handle);
        serverbound(SaveStageGraphInfoPacket.TYPE, SaveStageGraphInfoPacket.STREAM_CODEC, SaveStageGraphInfoPacket::handle);
        serverbound(SaveStageGraphStylePacket.TYPE, SaveStageGraphStylePacket.STREAM_CODEC, SaveStageGraphStylePacket::handle);
        serverbound(SaveStylePresetPacket.TYPE, SaveStylePresetPacket.STREAM_CODEC, SaveStylePresetPacket::handle);
        serverbound(DeleteStylePresetPacket.TYPE, DeleteStylePresetPacket.STREAM_CODEC, DeleteStylePresetPacket::handle);
        serverbound(AssignStylePresetPacket.TYPE, AssignStylePresetPacket.STREAM_CODEC, AssignStylePresetPacket::handle);
        serverbound(SaveDisguisePacket.TYPE, SaveDisguisePacket.STREAM_CODEC, SaveDisguisePacket::handle);
        serverbound(PedestalControlPacket.TYPE, PedestalControlPacket.STREAM_CODEC, PedestalControlPacket::handle);
        serverbound(TakeLecternScrollPacket.TYPE, TakeLecternScrollPacket.STREAM_CODEC, TakeLecternScrollPacket::handle);
        clientbound(SyncDependencyStatusPacket.TYPE, SyncDependencyStatusPacket.STREAM_CODEC, SyncDependencyStatusPacket::handle);
        clientbound(SyncStructureRegistryPacket.TYPE, SyncStructureRegistryPacket.STREAM_CODEC, SyncStructureRegistryPacket::handle);
        clientbound(LockFeedbackPacket.TYPE, LockFeedbackPacket.STREAM_CODEC, LockFeedbackPacket::handle);
        clientbound(TradeLockedPacket.TYPE, TradeLockedPacket.STREAM_CODEC, TradeLockedPacket::handle);
        clientbound(SyncTradeGoodsPacket.TYPE, SyncTradeGoodsPacket.STREAM_CODEC, SyncTradeGoodsPacket::handle);
        clientbound(EditorFeedbackPacket.TYPE, EditorFeedbackPacket.STREAM_CODEC, EditorFeedbackPacket::handle);
        clientbound(SyncLockBordersPacket.TYPE, SyncLockBordersPacket.STREAM_CODEC, SyncLockBordersPacket::handle);
        clientbound(net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket.TYPE, net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket.STREAM_CODEC, net.bananemdnsa.historystages.network.clientbound.SyncZoneSelectionPacket::handle);
        serverbound(net.bananemdnsa.historystages.network.serverbound.RequestZoneSelectionPacket.TYPE, net.bananemdnsa.historystages.network.serverbound.RequestZoneSelectionPacket.STREAM_CODEC, net.bananemdnsa.historystages.network.serverbound.RequestZoneSelectionPacket::handle);
        clientbound(net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket.TYPE, net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket.STREAM_CODEC, net.bananemdnsa.historystages.network.clientbound.SyncZoneShapesPacket::handle);
    }

    // Receivers for clientbound payloads only exist on the client; the server just needs the codec.
    private static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        if (Services.isClient()) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), type, codec,
                    (payload, context) -> handler.accept(payload, IPayloadContext.of(context)));
        } else {
            NetworkManager.registerS2CPayloadType(type, codec);
        }
    }

    private static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        NetworkManager.registerReceiver(NetworkManager.c2s(), type, codec,
                (payload, context) -> handler.accept(payload, IPayloadContext.of(context)));
    }
}
