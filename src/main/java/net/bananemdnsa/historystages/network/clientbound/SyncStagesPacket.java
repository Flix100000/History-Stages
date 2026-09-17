package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SyncStagesPacket {
    private final List<String> unlockedStages;
    private final Map<String, Long> unlockTimes;

    /**
     * Takes the times from the server's own record, so the many places that send this packet
     * need not pass them.
     */
    public SyncStagesPacket(List<String> unlockedStages) {
        this(unlockedStages, Map.copyOf(StageData.SERVER_UNLOCK_TIMES));
    }

    public SyncStagesPacket(List<String> unlockedStages, Map<String, Long> unlockTimes) {
        this.unlockedStages = unlockedStages;
        this.unlockTimes = unlockTimes;
    }

    public List<String> unlockedStages() {
        return unlockedStages;
    }

    public Map<String, Long> unlockTimes() {
        return unlockTimes;
    }

    // Wandelt die Liste in Daten um, die durch das Internet passen (Senden)
    public static void encode(SyncStagesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.unlockedStages.size());
        for (String stage : msg.unlockedStages) {
            buffer.writeUtf(stage);
        }
        writeTimes(buffer, msg.unlockTimes);
    }

    // Wandelt die Daten wieder in eine Liste um (Empfangen)
    public static SyncStagesPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientStageCache.setUnlockedStages(msg.unlockedStages, msg.unlockTimes);

            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        // Stage set just changed — the cached lock borders may be stale.
                        // Clear them; the server's next tick will re-send the correct list.
                        net.bananemdnsa.historystages.client.LockBorderClientCache.clear();

                        // Grafik-Refresh für Lock-Overlays
                        if (mc.levelRenderer != null) {
                            mc.levelRenderer.allChanged();
                        }

                        // Same for the vanilla recipe book. The global path also triggers a recipe
                        // resync, which rebuilds it too, but doing it here keeps the book correct
                        // without depending on that.
                        net.bananemdnsa.historystages.client.ClientRecipeBookRefresh.rebuild();
                        net.bananemdnsa.historystages.client.ClientFluidRecipeIndex.refresh();

                        // EMI extra reload (hat eigenen Reload-Mechanismus)
                        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
                            ExternalMods.refreshEMI();
                        }

                        // JEI hiding (Issue #64): refresh visibility after stage cache updated.
                        // Null-safe — no-op if JEI is not installed.
                        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                            try {
                                net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
                            } catch (Throwable ignored) {}
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ExternalMods {
        private static void refreshEMI() {
            try {
                Class<?> reloadManager = Class.forName("dev.emi.emi.runtime.EmiReloadManager");
                reloadManager.getMethod("reload").invoke(null);
            } catch (Throwable ignored) {}
        }
    }
}