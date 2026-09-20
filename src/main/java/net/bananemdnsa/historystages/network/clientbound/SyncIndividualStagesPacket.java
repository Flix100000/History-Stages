package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SyncIndividualStagesPacket(Set<String> unlockedStages, Map<String, Long> unlockTimes) implements CustomPacketPayload {

    public static SyncIndividualStagesPacket of(IndividualStageData data, UUID player) {
        return new SyncIndividualStagesPacket(data.getUnlockedStages(player), data.getUnlockTimes(player));
    }

    public static final CustomPacketPayload.Type<SyncIndividualStagesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_individual_stages"));

    public static final StreamCodec<FriendlyByteBuf, SyncIndividualStagesPacket> STREAM_CODEC =
            StreamCodec.of(SyncIndividualStagesPacket::encode, SyncIndividualStagesPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncIndividualStagesPacket msg) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
        SyncStagesPacket.writeTimes(buffer, msg.unlockTimes);
    }

    private static SyncIndividualStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Set<String> stages = new HashSet<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncIndividualStagesPacket(stages, SyncStagesPacket.readTimes(buffer));
    }

    public static void handle(SyncIndividualStagesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientIndividualStageCache.setUnlockedStages(msg.unlockedStages, msg.unlockTimes);

            // Trigger visual refresh for lock icons
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.levelRenderer != null) {
                mc.levelRenderer.allChanged();
            }

            // The vanilla recipe book is filtered as it is built, and nothing else rebuilds it
            // on an individual unlock — no recipe packet is sent for one.
            net.bananemdnsa.historystages.client.ClientRecipeBookRefresh.rebuild();
            net.bananemdnsa.historystages.client.ClientFluidRecipeIndex.refresh();

            // JEI hiding (Issue #64): refresh visibility after individual-stage cache updated.
            if (FabricLoader.getInstance().isModLoaded("jei")) {
                try {
                    net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
                } catch (Throwable ignored) {}
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
