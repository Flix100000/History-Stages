package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageUnlockHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.function.Supplier;

public class ToggleStageLockPacket {
    private final String stageId;
    private final boolean unlock; // true = unlock, false = lock

    public ToggleStageLockPacket(String stageId, boolean unlock) {
        this.stageId = stageId;
        this.unlock = unlock;
    }

    public static void encode(ToggleStageLockPacket msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.stageId);
        buffer.writeBoolean(msg.unlock);
    }

    public static ToggleStageLockPacket decode(FriendlyByteBuf buffer) {
        return new ToggleStageLockPacket(buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(ToggleStageLockPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            if (!StageManager.getStages().containsKey(msg.stageId)) return;

            var entry = StageManager.getStages().get(msg.stageId);
            String displayName = entry != null ? entry.getDisplayName() : msg.stageId;

            // Through the helper, not rebuilt here. This handler used to do its own version of
            // unlockGlobal and the two drifted in both directions: the editor never played the
            // unlock sound or sent the toast, and every other caller — pedestal, command,
            // auto-trigger, quest reward — never cleared the structure and biome caches or
            // reloaded recipes. Everything either side had is now in the helper.
            if (msg.unlock) {
                StageUnlockHelper.unlockGlobal(msg.stageId, player.serverLevel());
            } else {
                StageUnlockHelper.relockGlobal(msg.stageId, player.serverLevel());
            }

            String titleKey = msg.unlock
                    ? "editor.historystages.toast.stage_unlocked_editor.title"
                    : "editor.historystages.toast.stage_locked_editor.title";
            PacketHandler.sendEditorFeedback(
                    EditorFeedbackPacket.success(titleKey,
                            "editor.historystages.toast.stage_lock_changed.message",
                            displayName),
                    player);
        });
        ctx.get().setPacketHandled(true);
    }
}
