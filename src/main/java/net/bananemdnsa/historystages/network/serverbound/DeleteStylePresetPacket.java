package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Deletes a style preset and every stage's reference to it; the stages keep their own values. */
public record DeleteStylePresetPacket(String id) {

    public static void encode(DeleteStylePresetPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.id);
    }

    public static DeleteStylePresetPacket decode(FriendlyByteBuf buffer) {
        return new DeleteStylePresetPacket(buffer.readUtf());
    }

    public static void handle(DeleteStylePresetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
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
        ctx.get().setPacketHandled(true);
    }
}
