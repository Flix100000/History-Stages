package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.network.PacketReach;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client asks a pedestal to start or pause. Everything is re-checked server-side; the
 * button the player sees is a display, not an authority.
 */
public class PedestalControlPacket {
    private final BlockPos pos;
    private final boolean start;

    public PedestalControlPacket(BlockPos pos, boolean start) {
        this.pos = pos;
        this.start = start;
    }

    public static void encode(PedestalControlPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.start);
    }

    public static PedestalControlPacket decode(FriendlyByteBuf buf) {
        return new PedestalControlPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PedestalControlPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            // Reach check: refuse anything the player could not plausibly be using.
            BlockEntity be = PacketReach.blockEntityInReach(player, packet.pos);
            if (!(be instanceof ResearchPedestalBlockEntity pedestal)) return;

            if (packet.start) {
                pedestal.tryStart(player);
            } else {
                pedestal.pause();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
