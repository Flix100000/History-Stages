package net.bananemdnsa.historystages.network.serverbound;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageFileGuard;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.prune.RegistryUnknownIdCheck;
import net.bananemdnsa.historystages.data.prune.UnknownEntryPruner;
import net.bananemdnsa.historystages.data.prune.UnknownIdCheck;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.EditorFeedbackPacket;
import net.bananemdnsa.historystages.network.clientbound.PruneUnknownPreviewPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Takes the entries of uninstalled mods out of every stage (#142). Sent twice by the overview:
 * first to count them for the confirmation, then to do it.
 *
 * <p>Decided with the server's registries, the same ones that print the "missing mod?" warnings
 * on load, so what gets removed is exactly what was warned about.
 */
public record PruneUnknownEntriesPacket(boolean execute) {

    public static void encode(PruneUnknownEntriesPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.execute);
    }

    public static PruneUnknownEntriesPacket decode(FriendlyByteBuf buf) {
        return new PruneUnknownEntriesPacket(buf.readBoolean());
    }

    public static void handle(PruneUnknownEntriesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            UnknownIdCheck check = new RegistryUnknownIdCheck(
                    player.server.registryAccess(), player.server.getRecipeManager());
            if (msg.execute) {
                execute(player, check);
            } else {
                preview(player, check);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void preview(ServerPlayer player, UnknownIdCheck check) {
        int entries = 0;
        int stages = 0;
        for (StageEntry entry : allStages()) {
            int removed = UnknownEntryPruner.count(entry, check);
            if (removed > 0) {
                entries += removed;
                stages++;
            }
        }
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new PruneUnknownPreviewPacket(entries, stages));
    }

    private static void execute(ServerPlayer player, UnknownIdCheck check) {
        // Starts from the files, not from memory: a stage edited by hand since the last load is
        // then pruned with that edit in it instead of being overwritten by the stale copy.
        StageManager.reloadStages();

        Tally tally = new Tally();
        pruneAll(player, check, StageManager.getStages(), false, tally);
        pruneAll(player, check, StageManager.getIndividualStages(), true, tally);
        int entries = tally.entries;
        int stages = tally.stages;
        List<String> skipped = tally.skipped;

        // Once for all stages; the same follow-up a single save runs.
        StageManager.reloadStages();
        net.bananemdnsa.historystages.events.lock.StructureLockHandler.invalidateAll();
        net.bananemdnsa.historystages.events.lock.BiomeLockHandler.invalidateAll();
        net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
        net.bananemdnsa.historystages.util.lock.SpawnControlGate.rebuild();
        PacketHandler.sendDefinitionsToAll(new SyncStageDefinitionsPacket(StageManager.getStages()));
        StageData data = StageData.get(player.serverLevel());
        PacketHandler.sendToAll(new SyncStagesPacket(new ArrayList<>(data.getUnlockedStages())));
        PacketHandler.reloadForLockChange(player.server);

        if (stages > 0) {
            PacketHandler.sendEditorFeedback(EditorFeedbackPacket.success(
                    "editor.historystages.toast.pruned.title",
                    "editor.historystages.toast.pruned.message",
                    String.valueOf(entries), String.valueOf(stages)), player);
        } else if (skipped.isEmpty()) {
            PacketHandler.sendEditorFeedback(EditorFeedbackPacket.info(
                    "editor.historystages.toast.pruned.title",
                    "editor.historystages.prune.none"), player);
        }
        if (!skipped.isEmpty()) {
            PacketHandler.sendEditorFeedback(EditorFeedbackPacket.error(
                    "editor.historystages.toast.stage_changed_on_disk.title",
                    "editor.historystages.toast.prune_skipped.message",
                    String.join(", ", skipped)), player);
        }
    }

    private static final class Tally {
        int entries;
        int stages;
        final List<String> skipped = new ArrayList<>();
    }

    private static void pruneAll(ServerPlayer player, UnknownIdCheck check, Map<String, StageEntry> source,
                                 boolean individual, Tally tally) {
        StageScope scope = individual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        // Iterates a snapshot so a save that touches the map cannot break the loop.
        for (Map.Entry<String, StageEntry> e : new ArrayList<>(source.entrySet())) {
            String id = e.getKey();
            StageEntry copy = UnknownEntryPruner.detached(e.getValue());
            UnknownEntryPruner.Result result = UnknownEntryPruner.prune(copy, check);
            if (!result.changed()) continue;

            byte[] onDisk = StageManager.stageFileBytes(id, individual, null);
            if (!StageFileGuard.mayWrite(player.getUUID(), id, scope, onDisk)) {
                tally.skipped.add(id);
                continue;
            }
            boolean saved = individual
                    ? StageManager.saveIndividualStage(id, copy, null)
                    : StageManager.saveStage(id, copy, null);
            if (!saved) {
                tally.skipped.add(id);
                continue;
            }
            StageFileGuard.consume(player.getUUID(), id, scope);
            tally.entries += result.removed();
            tally.stages++;
        }
    }

    private static List<StageEntry> allStages() {
        List<StageEntry> all = new ArrayList<>(StageManager.getStages().values());
        all.addAll(StageManager.getIndividualStages().values());
        return all;
    }
}
