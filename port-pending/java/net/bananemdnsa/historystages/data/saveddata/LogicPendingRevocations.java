package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Revocations a "revoke when" block owes to players who were offline when it fired.
 *
 * <p>A revoke block fires at the moment its condition turns true. When that moment comes from a
 * global stage, every player's individual stages are asked, including those not online; for
 * them the revocation waits here and is carried out at their next login, with the toast and the
 * dropped items they would have got on the spot.
 */
public class LogicPendingRevocations extends SavedData {

    private static final String DATA_NAME = "historystages_pending_revocations";

    private final Map<UUID, Set<String>> pending = new HashMap<>();

    public static LogicPendingRevocations get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LogicPendingRevocations::new, LogicPendingRevocations::load), DATA_NAME);
    }

    public void add(UUID player, String stageId) {
        if (pending.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(stageId)) setDirty();
    }

    /** Takes and forgets everything owed to this player. */
    public Set<String> take(UUID player) {
        Set<String> owed = pending.remove(player);
        if (owed == null) return Set.of();
        setDirty();
        return owed;
    }

    public boolean has(UUID player, String stageId) {
        Set<String> owed = pending.get(player);
        return owed != null && owed.contains(stageId);
    }

    public static LogicPendingRevocations load(CompoundTag nbt, HolderLookup.Provider registries) {
        LogicPendingRevocations data = new LogicPendingRevocations();
        CompoundTag players = nbt.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ListTag list = players.getList(key, Tag.TAG_STRING);
            Set<String> stages = new LinkedHashSet<>();
            for (int i = 0; i < list.size(); i++) stages.add(list.getString(i));
            if (!stages.isEmpty()) data.pending.put(uuid, stages);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> e : pending.entrySet()) {
            ListTag list = new ListTag();
            for (String stage : e.getValue()) list.add(StringTag.valueOf(stage));
            players.put(e.getKey().toString(), list);
        }
        nbt.put("players", players);
        return nbt;
    }
}
