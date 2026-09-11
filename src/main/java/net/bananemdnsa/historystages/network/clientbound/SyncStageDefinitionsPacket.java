package net.bananemdnsa.historystages.network.clientbound;
import net.bananemdnsa.historystages.network.EditorDataCache;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.network.PacketJson;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.graph.GraphLayoutData;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Syncs all stage definitions (not just unlocked stages) from server to client.
 * Sent on player login so the client knows which items/blocks/entities are locked.
 *
 * <p>Also carries the folder layout of both trees (stage id → folder path, and every
 * folder including empty ones), because the client never sees the config files on a
 * dedicated server — the folder tree only reaches it through this sync.
 *
 * <p>And both stage graph settings files, for the same reason: the positions from
 * {@code graph_layout.json} and the per-stage descriptions and style overrides from
 * {@code graph_stages.json}. They ride along here rather than in their own packet because this is
 * already both the login sync and the post-admin-save broadcast, so they reach clients on both
 * paths with no new plumbing. Each payload is the JSON that its own data class reads and writes —
 * one serialiser for the file and the wire, rather than two that can disagree.
 *
 * <p>Every JSON field travels gzipped. Written plainly, a large pack's stage set went past the
 * fixed character cap on a string write and threw while the login packet was being encoded — the
 * player was simply dropped. Stage JSON is the same handful of keys repeated once per entry, so it
 * compresses about tenfold, which buys far more headroom than raising the cap would and keeps the
 * packet well under the frame limit at the same time.
 */
public record SyncStageDefinitionsPacket(Map<String, StageEntry> stages,
                                         Map<String, StageEntry> individualStages,
                                         Map<String, String> stagePaths,
                                         Map<String, String> individualStagePaths,
                                         Set<String> folders,
                                         Set<String> individualFolders,
                                         String graphLayout,
                                         String graphStages,
                                         boolean graphGlobalFrozen,
                                         boolean graphIndividualFrozen) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();
    private static final java.lang.reflect.Type PATH_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final java.lang.reflect.Type FOLDER_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages(),
                new HashMap<>(StageManager.getStagePaths()),
                new HashMap<>(StageManager.getIndividualStagePaths()),
                new HashSet<>(StageManager.getFolders()),
                new HashSet<>(StageManager.getIndividualFolders()),
                GraphLayoutData.toJson(GraphLayoutData.get()),
                GraphStageData.toJson(GraphStageData.get()),
                GraphLayoutData.get().globalFrozen(),
                GraphLayoutData.get().individualFrozen());
    }

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages) {
        this(stages, individualStages,
                new HashMap<>(StageManager.getStagePaths()),
                new HashMap<>(StageManager.getIndividualStagePaths()),
                new HashSet<>(StageManager.getFolders()),
                new HashSet<>(StageManager.getIndividualFolders()),
                GraphLayoutData.toJson(GraphLayoutData.get()),
                GraphStageData.toJson(GraphStageData.get()),
                GraphLayoutData.get().globalFrozen(),
                GraphLayoutData.get().individualFrozen());
    }

    public static final CustomPacketPayload.Type<SyncStageDefinitionsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_stage_definitions"));

    public static final StreamCodec<FriendlyByteBuf, SyncStageDefinitionsPacket> STREAM_CODEC =
            StreamCodec.of(SyncStageDefinitionsPacket::encode, SyncStageDefinitionsPacket::decode);

    /**
     * Character ceiling for the two stage maps and the two graph files.
     *
     * <p>Generous because it no longer costs anything on the wire — gzip turns stage JSON, which
     * is the same dozen keys repeated per entry, into something between a tenth and a twentieth of
     * its size. It is a guard against a runaway file, not a budget anyone is expected to spend.
     */
    private static final int MAX_JSON_CHARS = 8 * 1024 * 1024;

    /** For folder paths and folder names, which scale with the tree rather than its contents. */
    private static final int MAX_SMALL_JSON_CHARS = 1024 * 1024;

    private static void encode(FriendlyByteBuf buffer, SyncStageDefinitionsPacket msg) {
        PacketJson.write(buffer, GSON.toJson(msg.stages), MAX_JSON_CHARS, "stages");
        PacketJson.write(buffer, GSON.toJson(msg.individualStages), MAX_JSON_CHARS, "individual stages");
        PacketJson.write(buffer, GSON.toJson(msg.stagePaths), MAX_SMALL_JSON_CHARS, "stage paths");
        PacketJson.write(buffer, GSON.toJson(msg.individualStagePaths), MAX_SMALL_JSON_CHARS, "individual stage paths");
        PacketJson.write(buffer, GSON.toJson(msg.folders), MAX_SMALL_JSON_CHARS, "folders");
        PacketJson.write(buffer, GSON.toJson(msg.individualFolders), MAX_SMALL_JSON_CHARS, "individual folders");
        PacketJson.write(buffer, msg.graphLayout != null ? msg.graphLayout : "{}", MAX_JSON_CHARS, "graph layout");
        PacketJson.write(buffer, msg.graphStages != null ? msg.graphStages : "{}", MAX_JSON_CHARS, "graph stages");
        // Frozen-ness cannot be read back out of the position JSON: an unfrozen tree carries
        // computed positions too, so a non-empty section proves nothing off disk.
        buffer.writeBoolean(msg.graphGlobalFrozen);
        buffer.writeBoolean(msg.graphIndividualFrozen);
    }

    private static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
        Map<String, StageEntry> stages = GSON.fromJson(PacketJson.read(buffer, MAX_JSON_CHARS, "stages"), MAP_TYPE);
        if (stages == null) stages = new HashMap<>();
        Map<String, StageEntry> individualStages = GSON.fromJson(PacketJson.read(buffer, MAX_JSON_CHARS, "individual stages"), MAP_TYPE);
        if (individualStages == null) individualStages = new HashMap<>();
        Map<String, String> stagePaths = GSON.fromJson(PacketJson.read(buffer, MAX_SMALL_JSON_CHARS, "stage paths"), PATH_MAP_TYPE);
        if (stagePaths == null) stagePaths = new HashMap<>();
        Map<String, String> individualStagePaths = GSON.fromJson(PacketJson.read(buffer, MAX_SMALL_JSON_CHARS, "individual stage paths"), PATH_MAP_TYPE);
        if (individualStagePaths == null) individualStagePaths = new HashMap<>();
        Set<String> folders = GSON.fromJson(PacketJson.read(buffer, MAX_SMALL_JSON_CHARS, "folders"), FOLDER_SET_TYPE);
        if (folders == null) folders = new HashSet<>();
        Set<String> individualFolders = GSON.fromJson(PacketJson.read(buffer, MAX_SMALL_JSON_CHARS, "individual folders"), FOLDER_SET_TYPE);
        if (individualFolders == null) individualFolders = new HashSet<>();
        String graphLayout = PacketJson.read(buffer, MAX_JSON_CHARS, "graph layout");
        String graphStages = PacketJson.read(buffer, MAX_JSON_CHARS, "graph stages");
        boolean graphGlobalFrozen = buffer.readBoolean();
        boolean graphIndividualFrozen = buffer.readBoolean();
        return new SyncStageDefinitionsPacket(stages, individualStages, stagePaths,
                individualStagePaths, folders, individualFolders, graphLayout, graphStages,
                graphGlobalFrozen, graphIndividualFrozen);
    }

    public static void handle(SyncStageDefinitionsPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
            StageManager.setStagePaths(msg.stagePaths, msg.individualStagePaths);
            StageManager.setFolders(msg.folders, msg.individualFolders);
            GraphLayoutData.Snapshot layout = GraphLayoutData.fromJson(msg.graphLayout);
            GraphLayoutData.setFromSync(layout.global(), layout.individual(),
                    msg.graphGlobalFrozen, msg.graphIndividualFrozen);
            GraphStageData.set(GraphStageData.fromJson(msg.graphStages));
            StageManager.rebuildDualPhase();
            EditorDataCache.setStages(new HashMap<>(msg.stages));

            // Stage definitions (and thus which node exists / which state it resolves to)
            // just changed — every previously resolved node style is stale.
            StageGraphConfig.invalidateCache();
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions + "
                    + msg.individualStages.size() + " individual stage definitions from server.");

            // Stage definitions changed at runtime — invalidate the creative tab cache so
            // newly-non-AUTO stages get their scroll, and former non-AUTO stages lose theirs.
            // Mirror vanilla CreativeModeInventoryScreen.tryRebuildTabContents: after the
            // rebuild, re-register the creative search reloaders with the fresh item list,
            // otherwise the search field keeps the stale (or empty) captured list.
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                net.minecraft.core.HolderLookup.Provider registryAccess = mc.level.registryAccess();
                boolean rebuilt = net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                        mc.player.connection.enabledFeatures(),
                        mc.options.operatorItemsTab().get() && mc.player.canUseGameMasterBlocks(),
                        registryAccess);
                if (rebuilt) {
                    java.util.List<net.minecraft.world.item.ItemStack> searchItems = java.util.List.copyOf(
                            net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayItems());
                    net.minecraft.client.multiplayer.SessionSearchTrees searchTrees =
                            mc.player.connection.searchTrees();
                    searchTrees.updateCreativeTooltips(registryAccess, searchItems);
                    searchTrees.updateCreativeTags(searchItems);
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
