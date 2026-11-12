package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stages lost for good through a lock trigger with {@code re_unlockable: false}, globally and per
 * player. Normal unlock paths refuse a marked stage; only an admin unlock clears the mark.
 */
public class LostStagesData extends SavedData {

    private static final String DATA_NAME = "historystages_lost_stages";

    private final Set<String> global = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> individual = new ConcurrentHashMap<>();

    public static LostStagesData get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(LostStagesData::new, LostStagesData::load), DATA_NAME);
        }
        return new LostStagesData();
    }

    public boolean isLostGlobal(String stageId) { return global.contains(stageId); }

    public boolean isLostIndividual(UUID player, String stageId) {
        Set<String> s = individual.get(player);
        return s != null && s.contains(stageId);
    }

    /** All four mutators return whether anything changed, so callers can skip a pointless sync. */
    public boolean markGlobal(String stageId) {
        boolean changed = global.add(stageId);
        if (changed) setDirty();
        return changed;
    }

    public boolean markIndividual(UUID player, String stageId) {
        boolean changed = individual.computeIfAbsent(player, p -> ConcurrentHashMap.newKeySet()).add(stageId);
        if (changed) setDirty();
        return changed;
    }

    public boolean clearGlobal(String stageId) {
        boolean changed = global.remove(stageId);
        if (changed) setDirty();
        return changed;
    }

    public boolean clearIndividual(UUID player, String stageId) {
        Set<String> s = individual.get(player);
        boolean changed = s != null && s.remove(stageId);
        if (changed) setDirty();
        return changed;
    }

    public Set<String> globalSnapshot() { return Set.copyOf(global); }

    public Set<String> individualSnapshot(UUID player) {
        Set<String> s = individual.get(player);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    /** Drops every mark whose stage id is not in {@code keep}. Returns whether anything was dropped. */
    public boolean pruneOrphans(Set<String> keep) {
        boolean changed = global.removeIf(s -> !keep.contains(s));
        for (Set<String> s : individual.values()) changed |= s.removeIf(id -> !keep.contains(id));
        if (changed) setDirty();
        return changed;
    }

    public static LostStagesData load(CompoundTag nbt, HolderLookup.Provider registries) {
        LostStagesData data = new LostStagesData();
        readIds(nbt.getList("global", Tag.TAG_STRING), data.global);
        ListTag players = nbt.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag pTag = players.getCompound(i);
            Set<String> ids = ConcurrentHashMap.newKeySet();
            readIds(pTag.getList("stages", Tag.TAG_STRING), ids);
            data.individual.put(pTag.getUUID("uuid"), ids);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.put("global", writeIds(global));
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Set<String>> e : individual.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", e.getKey());
            pTag.put("stages", writeIds(e.getValue()));
            players.add(pTag);
        }
        nbt.put("players", players);
        return nbt;
    }

    private static void readIds(ListTag list, Set<String> into) {
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }

    private static ListTag writeIds(Set<String> ids) {
        ListTag out = new ListTag();
        for (String id : ids) out.add(StringTag.valueOf(id));
        return out;
    }
}
