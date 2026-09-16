package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncDependencyStatusPacket;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.network.PacketReach;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.minecraft.core.BlockPos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server: Request dependency status check for a specific stage.
 * Server responds with SyncDependencyStatusPacket.
 */
public class CheckDependencyPacket {
    private final String stageId;
    private final boolean isIndividual;
    private final BlockPos pos;

    public CheckDependencyPacket(String stageId, boolean isIndividual, BlockPos pos) {
        this.stageId = stageId;
        this.isIndividual = isIndividual;
        this.pos = pos;
    }

    public static void encode(CheckDependencyPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.isIndividual);
        buf.writeBlockPos(packet.pos);
    }

    public static CheckDependencyPacket decode(FriendlyByteBuf buf) {
        return new CheckDependencyPacket(buf.readUtf(), buf.readBoolean(), buf.readBlockPos());
    }

    public static void handle(CheckDependencyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null)
                return;

            StageEntry entry = packet.isIndividual
                    ? StageManager.getIndividualStages().get(packet.stageId)
                    : StageManager.getStages().get(packet.stageId);

            if (entry == null)
                return;

            // Try to read deposited NBT from the pedestal scroll for accurate status. The
            // position is the client's, so it goes through PacketReach: a pedestal the player
            // cannot be standing at simply yields no scroll data, and the answer still goes out
            // rather than being dropped.
            CompoundTag depositedTag = null;
            double costReduction = 0.0;
            BlockEntity be = PacketReach.blockEntityInReach(player, packet.pos);
            if (be instanceof ResearchPedestalBlockEntity pedestal) {
                ItemStack scroll = pedestal.getScrollStack();
                CompoundTag scrollTag = (scroll.hasTag() ? scroll.getTag().copy() : new net.minecraft.nbt.CompoundTag());
                if (scrollTag.contains("DepositedDependencies")) {
                    depositedTag = scrollTag.getCompound("DepositedDependencies");
                }
                costReduction = scrollTag.contains("LockedCostReduction")
                        ? ResearchPedestalBlockEntity.getLockedCostReduction(scrollTag)
                        : pedestal.getActiveBooster().costReduction();
            }

            RequirementResult result = DependencyChecker.checkAll(entry, player, player.level(),
                    packet.isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL,
                    depositedTag, costReduction);
            PacketHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SyncDependencyStatusPacket(packet.stageId, packet.isIndividual, result));
        });
        ctx.get().setPacketHandled(true);
    }
}
