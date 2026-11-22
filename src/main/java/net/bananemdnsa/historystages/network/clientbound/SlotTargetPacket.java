package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.util.lock.SlotLockMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client which block the menu it just opened belongs to. The client's copy of a barrel
 * menu holds a plain container, so on its own it could only ever guess the menu type.
 */
public record SlotTargetPacket(int containerId, String target) {

    public static void encode(SlotTargetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeUtf(msg.target);
    }

    public static SlotTargetPacket decode(FriendlyByteBuf buf) {
        return new SlotTargetPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(SlotTargetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientHandler.apply(msg);
        });
        ctx.get().setPacketHandled(true);
    }

    /** Kept apart so a dedicated server never loads the client classes it names. */
    private static final class ClientHandler {
        static void apply(SlotTargetPacket msg) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            var menu = player.containerMenu;
            if (menu != null && menu.containerId == msg.containerId) {
                ((SlotLockMenu) menu).historystages$setSlotTarget(msg.target);
            }
        }
    }
}
