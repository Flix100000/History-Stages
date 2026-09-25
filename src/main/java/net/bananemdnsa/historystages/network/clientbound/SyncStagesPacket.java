package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncStagesPacket(List<String> unlockedStages, Map<String, Long> unlockTimes) implements CustomPacketPayload {

    /**
     * Takes the times from the server's own record, so the many places that send this packet
     * need not pass them. Read here rather than in the encoder: in singleplayer a payload
     * reaches the client without ever being encoded.
     */
    public SyncStagesPacket(List<String> unlockedStages) {
        this(unlockedStages, Map.copyOf(StageData.SERVER_UNLOCK_TIMES));
    }

    public static final CustomPacketPayload.Type<SyncStagesPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stages"));

    public static final StreamCodec<FriendlyByteBuf, SyncStagesPacket> STREAM_CODEC =
            StreamCodec.of(SyncStagesPacket::encode, SyncStagesPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncStagesPacket msg) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
        writeTimes(buffer, msg.unlockTimes);
    }

    private static SyncStagesPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        List<String> stages = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            stages.add(buffer.readUtf());
        }
        return new SyncStagesPacket(stages, readTimes(buffer));
    }

    /** Shared with {@link SyncIndividualStagesPacket}, which carries its times the same way. */
    static void writeTimes(FriendlyByteBuf buffer, Map<String, Long> times) {
        buffer.writeInt(times.size());
        for (Map.Entry<String, Long> e : times.entrySet()) {
            buffer.writeUtf(e.getKey());
            buffer.writeLong(e.getValue());
        }
    }

    static Map<String, Long> readTimes(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, Long> times = new HashMap<>();
        for (int i = 0; i < size; i++) {
            times.put(buffer.readUtf(), buffer.readLong());
        }
        return times;
    }

    public static void handle(SyncStagesPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientStageCache.setUnlockedStages(msg.unlockedStages, msg.unlockTimes);

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    if (mc.levelRenderer != null) {
                        mc.levelRenderer.allChanged();
                    }

                    // Same for the vanilla recipe book. The global path also triggers a recipe
                    // resync, which rebuilds it too, but doing it here keeps the book correct
                    // without depending on that.
                    net.bananemdnsa.historystages.client.ClientRecipeBookRefresh.rebuild();
                    net.bananemdnsa.historystages.client.ClientFluidRecipeIndex.refresh();

                    // JEI hiding (Issue #64): refresh visibility after stage cache updated.
                    // Null-safe — no-op if JEI is not installed.
                    if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
                        try {
                            net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
                        } catch (Throwable ignored) {}
                    }
                    if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                        try {
                            net.bananemdnsa.historystages.compat.emi.EmiReloadBridge.reloadIfHiding();
                        } catch (Throwable ignored) {}
                    }

                    System.out.println("[HistoryStages] Hard-Reset & Mod-Sync completed.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
