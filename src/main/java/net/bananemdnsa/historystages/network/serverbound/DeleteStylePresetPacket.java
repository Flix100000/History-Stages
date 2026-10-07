package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Deletes a style preset and every stage's reference to it; the stages keep their own values. */
public record DeleteStylePresetPacket(String id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteStylePresetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "delete_style_preset"));

    public static final StreamCodec<FriendlyByteBuf, DeleteStylePresetPacket> STREAM_CODEC =
            StreamCodec.of((buffer, msg) -> buffer.writeUtf(msg.id), buffer -> new DeleteStylePresetPacket(buffer.readUtf()));

    public static void handle(DeleteStylePresetPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            GraphStageData.Snapshot data = GraphStageData.get();
            GraphStageData.Preset preset = data.presets().get(msg.id);
            if (preset == null) return;

            GraphStageData.set(data.withoutPreset(msg.id));
            GraphStageData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.preset.toast.deleted.title",
                            "editor.historystages.graph.preset.toast.deleted.message",
                            preset.name),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
