package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock-trigger progress: which lock triggers of an open stage have already fired. Cleared every
 * time the stage unlocks, because lock triggers only count while the stage is open.
 */
public class LockTriggerProgressData extends SavedData {

    private static final String DATA_NAME = "historystages_lock_progress";

    private final Map<String, Set<Long>> global = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Set<Long>>> individual = new ConcurrentHashMap<>();

    public static LockTriggerProgressData get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(LockTriggerProgressData::new, LockTriggerProgressData::load),
                    DATA_NAME);
        }
        return new LockTriggerProgressData();
    }

    public Set<Long> global(String stageId) {
        return global.computeIfAbsent(stageId, s -> ConcurrentHashMap.newKeySet());
    }

    public Set<Long> individual(UUID player, String stageId) {
        return individual.computeIfAbsent(player, p -> new ConcurrentHashMap<>())
                .computeIfAbsent(stageId, s -> ConcurrentHashMap.newKeySet());
    }

    /** Read-only lookup: empty when nothing was recorded, and never creates an entry. */
    public Set<Long> peekIndividual(UUID player, String stageId) {
        Map<String, Set<Long>> m = individual.get(player);
        Set<Long> s = m == null ? null : m.get(stageId);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    public void clearGlobal(String stageId) {
        if (global.remove(stageId) != null) setDirty();
    }

    public void clearIndividual(UUID player, String stageId) {
        Map<String, Set<Long>> m = individual.get(player);
        if (m != null && m.remove(stageId) != null) setDirty();
    }

    /** Drops every stage entry whose id is not in {@code keep}. Called on stage-config reload. */
    public void pruneOrphans(Set<String> keep) {
        boolean changed = global.keySet().removeIf(s -> !keep.contains(s));
        for (Map<String, Set<Long>> m : individual.values()) changed |= m.keySet().removeIf(s -> !keep.contains(s));
        if (changed) setDirty();
    }

    /** Mark dirty after a set returned by {@link #global} / {@link #individual} was mutated. */
    public void touch() { setDirty(); }

    public static LockTriggerProgressData load(CompoundTag nbt, HolderLookup.Provider registries) {
        LockTriggerProgressData data = new LockTriggerProgressData();
        readStages(nbt.getList("global", Tag.TAG_COMPOUND), data.global);
        ListTag players = nbt.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pTag = players.getCompound(i);
            Map<String, Set<Long>> m = new ConcurrentHashMap<>();
            readStages(pTag.getList("stages", Tag.TAG_COMPOUND), m);
            data.individual.put(pTag.getUUID("uuid"), m);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.put("global", writeStages(global));
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, Set<Long>>> e : individual.entrySet()) {
            ListTag stages = writeStages(e.getValue());
            if (stages.isEmpty()) continue;
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", e.getKey());
            pTag.put("stages", stages);
            players.add(pTag);
        }
        nbt.put("players", players);
        return nbt;
    }

    private static void readStages(ListTag list, Map<String, Set<Long>> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag sTag = list.getCompound(i);
            Set<Long> set = ConcurrentHashMap.newKeySet();
            for (long s : sTag.getLongArray("sigs")) set.add(s);
            into.put(sTag.getString("id"), set);
        }
    }

    private static ListTag writeStages(Map<String, Set<Long>> from) {
        ListTag out = new ListTag();
        for (Map.Entry<String, Set<Long>> e : from.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag sTag = new CompoundTag();
            sTag.putString("id", e.getKey());
            sTag.put("sigs", new LongArrayTag(e.getValue().stream().mapToLong(Long::longValue).toArray()));
            out.add(sTag);
        }
        return out;
    }
}
