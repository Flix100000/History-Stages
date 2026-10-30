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
 * Team-shared stage changes owed to players who were offline when a teammate unlocked or lost the
 * stage. Carried out at their next login, where the usual "blocked for this player" checks run
 * against the real player.
 *
 * <p>A stage is never owed both ways at once: owing it as a grant cancels an owed loss and the
 * other way round, so only the teammate's latest change reaches the absent player.
 */
public class TeamSharePending extends SavedData {

    private static final String DATA_NAME = "historystages_team_share_pending";

    private final Map<UUID, Set<String>> grants = new HashMap<>();
    private final Map<UUID, Set<String>> losses = new HashMap<>();

    public static TeamSharePending get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TeamSharePending::new, TeamSharePending::load, null), DATA_NAME);
    }

    public void addGrant(UUID player, String stageId) {
        boolean changed = remove(losses, player, stageId);
        changed |= grants.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(stageId);
        if (changed) setDirty();
    }

    public void addLoss(UUID player, String stageId) {
        boolean changed = remove(grants, player, stageId);
        changed |= losses.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(stageId);
        if (changed) setDirty();
    }

    /** Takes and forgets every grant owed to this player. */
    public Set<String> takeGrants(UUID player) {
        return take(grants, player);
    }

    /** Takes and forgets every loss owed to this player. */
    public Set<String> takeLosses(UUID player) {
        return take(losses, player);
    }

    private Set<String> take(Map<UUID, Set<String>> map, UUID player) {
        Set<String> owed = map.remove(player);
        if (owed == null) return Set.of();
        setDirty();
        return owed;
    }

    private static boolean remove(Map<UUID, Set<String>> map, UUID player, String stageId) {
        Set<String> set = map.get(player);
        if (set == null || !set.remove(stageId)) return false;
        if (set.isEmpty()) map.remove(player);
        return true;
    }

    public static TeamSharePending load(CompoundTag nbt, HolderLookup.Provider registries) {
        TeamSharePending data = new TeamSharePending();
        read(nbt.getCompound("grants"), data.grants);
        read(nbt.getCompound("losses"), data.losses);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.put("grants", write(grants));
        nbt.put("losses", write(losses));
        return nbt;
    }

    private static void read(CompoundTag players, Map<UUID, Set<String>> into) {
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
            if (!stages.isEmpty()) into.put(uuid, stages);
        }
    }

    private static CompoundTag write(Map<UUID, Set<String>> map) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> e : map.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            ListTag list = new ListTag();
            for (String stage : e.getValue()) list.add(StringTag.valueOf(stage));
            players.put(e.getKey().toString(), list);
        }
        return players;
    }
}
