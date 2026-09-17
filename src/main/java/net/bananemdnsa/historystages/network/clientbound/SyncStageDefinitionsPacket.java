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
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
public class SyncStageDefinitionsPacket {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, StageEntry>>() {}.getType();
    private static final Type PATH_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type FOLDER_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private final Map<String, StageEntry> stages;
    private final Map<String, StageEntry> individualStages;
    private final Map<String, String> stagePaths;
    private final Map<String, String> individualStagePaths;
    private final Set<String> folders;
    private final Set<String> individualFolders;
    private final String graphLayout;
    private final String graphStages;
    private final boolean graphGlobalFrozen;
    private final boolean graphIndividualFrozen;

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages) {
        this(stages, StageManager.getIndividualStages());
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

    public SyncStageDefinitionsPacket(Map<String, StageEntry> stages, Map<String, StageEntry> individualStages,
                                       Map<String, String> stagePaths, Map<String, String> individualStagePaths,
                                       Set<String> folders, Set<String> individualFolders,
                                       String graphLayout, String graphStages,
                                       boolean graphGlobalFrozen, boolean graphIndividualFrozen) {
        this.stages = stages;
        this.individualStages = individualStages;
        this.stagePaths = stagePaths;
        this.individualStagePaths = individualStagePaths;
        this.folders = folders;
        this.individualFolders = individualFolders;
        this.graphLayout = graphLayout;
        this.graphStages = graphStages;
        this.graphGlobalFrozen = graphGlobalFrozen;
        this.graphIndividualFrozen = graphIndividualFrozen;
    }

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

    public Map<String, StageEntry> stages() { return stages; }

    public Map<String, StageEntry> individualStages() { return individualStages; }

    public Map<String, String> stagePaths() { return stagePaths; }

    public Map<String, String> individualStagePaths() { return individualStagePaths; }

    public Set<String> folders() { return folders; }

    public Set<String> individualFolders() { return individualFolders; }

    public String graphLayout() { return graphLayout; }

    public String graphStages() { return graphStages; }

    public boolean graphGlobalFrozen() { return graphGlobalFrozen; }

    public boolean graphIndividualFrozen() { return graphIndividualFrozen; }

    public static void encode(SyncStageDefinitionsPacket msg, FriendlyByteBuf buffer) {
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

    public static SyncStageDefinitionsPacket decode(FriendlyByteBuf buffer) {
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

    public static void handle(SyncStageDefinitionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Replace client-side stage definitions with the server's data
            StageManager.setStages(msg.stages);
            StageManager.setIndividualStages(msg.individualStages);
            StageManager.setStagePaths(msg.stagePaths, msg.individualStagePaths);
            StageManager.setFolders(msg.folders, msg.individualFolders);
            GraphLayoutData.Snapshot layout = GraphLayoutData.fromJson(msg.graphLayout);
            GraphLayoutData.setFromSync(layout.global(), layout.individual(),
                    msg.graphGlobalFrozen, msg.graphIndividualFrozen);
            GraphStageData.set(GraphStageData.fromJson(msg.graphStages));
            StageManager.rebuildDualPhase();
            // Keep editor cache in sync so open editors always show current data
            EditorDataCache.setStages(new HashMap<>(msg.stages));

            // Stage definitions (and thus which node exists / which state it resolves to)
            // just changed — every previously resolved node style is stale.
            StageGraphConfig.invalidateCache();
            System.out.println("[HistoryStages] Received " + msg.stages.size() + " stage definitions + "
                    + msg.individualStages.size() + " individual stage definitions from server.");
        });
        ctx.get().setPacketHandled(true);
    }
}
