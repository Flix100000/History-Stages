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

/**
 * Creates or replaces one style preset in {@code graph_stages.json}.
 *
 * <p>An {@code id} the file does not know yet creates a new preset, and the server picks the id
 * from the name — the client's guess is only a hint, so two authors creating "Boss" at the same
 * moment end up with two presets rather than one overwriting the other. The payload carries the
 * preset's {@code style}/{@code styles} as JSON, the same shape as the file.
 */
public record SaveStylePresetPacket(String id, String name, String json) {

    public static final int MAX_NAME_LENGTH = 32;



    public static void encode(SaveStylePresetPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.id == null ? "" : msg.id);
        buffer.writeUtf(msg.name == null ? "" : msg.name, 256);
        buffer.writeUtf(msg.json == null ? "{}" : msg.json, 8192);
    }

    public static SaveStylePresetPacket decode(FriendlyByteBuf buffer) {
        return new SaveStylePresetPacket(buffer.readUtf(), buffer.readUtf(256), buffer.readUtf(8192));
    }

    public static void handle(SaveStylePresetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            GraphStageData.Snapshot data = GraphStageData.get();
            String name = msg.name == null ? "" : msg.name.trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) return;

            boolean exists = msg.id != null && data.presets().containsKey(msg.id);
            String id = exists ? msg.id : GraphStageData.newPresetId(name, data.presets().keySet());
            if (data.presetNameTaken(name, id)) return;

            GraphStageData.Entry incoming = GraphStageData.entryFromJson(msg.json);
            GraphStageData.Preset stored = exists ? data.presets().get(id) : null;
            GraphStageData.Preset preset = new GraphStageData.Preset();
            preset.name = name;
            // Presets apply to both trees; their keys are the same, so the global spec checks them.
            preset.style = SaveStageGraphStylePacket.sanitizeAllStates(
                    incoming.style, stored == null ? null : stored.style, "global");
            if (preset.style.isEmpty()) preset.style = null;
            preset.styles = SaveStageGraphStylePacket.sanitizeStates(
                    incoming.styles, stored == null ? null : stored.styles, "global");
            if (preset.styles != null && preset.styles.isEmpty()) preset.styles = null;

            GraphStageData.set(data.withPreset(id, preset));
            GraphStageData.save();
            PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(
                            "editor.historystages.graph.preset.toast.saved.title",
                            "editor.historystages.graph.preset.toast.saved.message",
                            name),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
