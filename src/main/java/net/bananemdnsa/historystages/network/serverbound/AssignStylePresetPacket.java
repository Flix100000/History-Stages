package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Points many stages at one style preset — or at none, with an empty {@code presetId} — in one
 * go, so assigning a preset to a selection is one save and one broadcast rather than one per stage.
 * Only the reference changes; the stages' own style values stay.
 */
public record AssignStylePresetPacket(List<Target> targets, String presetId) implements CustomPacketPayload {

    /** One stage, by id and tree. */
    public record Target(String stageId, boolean individual) {}

    private static final int MAX_TARGETS = 4096;

    public static final CustomPacketPayload.Type<AssignStylePresetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "assign_style_preset"));

    public static final StreamCodec<FriendlyByteBuf, AssignStylePresetPacket> STREAM_CODEC =
            StreamCodec.of(AssignStylePresetPacket::encode, AssignStylePresetPacket::decode);

    private static void encode(FriendlyByteBuf buffer, AssignStylePresetPacket msg) {
        buffer.writeUtf(msg.presetId == null ? "" : msg.presetId);
        buffer.writeVarInt(msg.targets.size());
        for (Target target : msg.targets) {
            buffer.writeUtf(target.stageId());
            buffer.writeBoolean(target.individual());
        }
    }

    private static AssignStylePresetPacket decode(FriendlyByteBuf buffer) {
        String presetId = buffer.readUtf();
        int count = Math.min(buffer.readVarInt(), MAX_TARGETS);
        List<Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new Target(buffer.readUtf(), buffer.readBoolean()));
        }
        return new AssignStylePresetPacket(targets, presetId);
    }

    public static void handle(AssignStylePresetPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2)) return;

            GraphStageData.Snapshot data = GraphStageData.get();
            String presetId = msg.presetId == null || msg.presetId.isEmpty() ? null : msg.presetId;
            if (presetId != null && !data.presets().containsKey(presetId)) return;

            int changed = 0;
            for (Target target : msg.targets) {
                // Stage ids are file names; a value that fails this check must never reach
                // graph_stages.json.
                if (!StagePaths.isValidSegment(target.stageId())) continue;
                data = data.withAssignedPreset(target.stageId(), target.individual(), presetId);
                changed++;
            }
            if (changed == 0) return;

            GraphStageData.set(data);
            GraphStageData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.preset.toast.assigned.title",
                            presetId == null
                                    ? "editor.historystages.graph.preset.toast.cleared.message"
                                    : "editor.historystages.graph.preset.toast.assigned.message",
                            String.valueOf(changed),
                            presetId == null ? "" : data.presets().get(presetId).name),
                    player);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
