package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IndividualStageData extends SavedData {
    private final Map<UUID, Set<String>> playerStages = new HashMap<>();
    /** Game time per player and stage; see {@code StageData.unlockTimes}. */
    private final Map<UUID, Map<String, Long>> unlockTimes = new HashMap<>();
    private static final String DATA_NAME = "historystages_individual";

    public static final Map<UUID, Set<String>> SERVER_CACHE = new ConcurrentHashMap<>();

    /**
     * Bumped on every change to {@link #SERVER_CACHE}. Same reasoning as its counterpart in
     * {@code StageData}: a counter beside the data cannot be forgotten the way a notification at
     * five call sites can.
     *
     * <p>One counter for all players rather than one each. A single player unlocking something
     * invalidates every cached mask, which is a handful of rebuilds — against the alternative of
     * a per-player counter that has to be created, found and cleaned up for players who never
     * unlock anything.
     */
    private static final java.util.concurrent.atomic.AtomicLong VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    /** Changes whenever any player's individual set does. Never persisted, never sent. */
    public static long cacheVersion() {
        return VERSION.get();
    }

    public IndividualStageData() {
        SERVER_CACHE.clear();
        VERSION.incrementAndGet();
    }

    public static IndividualStageData load(CompoundTag nbt, HolderLookup.Provider registries) {
        IndividualStageData data = new IndividualStageData();
        SERVER_CACHE.clear();

        CompoundTag playersTag = nbt.getCompound("players");
        CompoundTag allTimes = nbt.getCompound("unlockTimes");
        for (String uuidStr : playersTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            Set<String> stages = new HashSet<>();
            ListTag list = playersTag.getList(uuidStr, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                stages.add(list.getString(i));
            }

            data.playerStages.put(uuid, stages);

            CompoundTag times = allTimes.getCompound(uuidStr);
            for (String stage : stages) {
                if (times.contains(stage, Tag.TAG_LONG)) {
                    data.unlockTimes.computeIfAbsent(uuid, k -> new HashMap<>())
                            .put(stage, times.getLong(stage));
                }
            }
            SERVER_CACHE.put(uuid, ConcurrentHashMap.newKeySet());
            SERVER_CACHE.get(uuid).addAll(stages);
            VERSION.incrementAndGet();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            ListTag list = new ListTag();
            for (String stage : entry.getValue()) {
                list.add(StringTag.valueOf(stage));
            }
            playersTag.put(entry.getKey().toString(), list);
        }
        nbt.put("players", playersTag);

        CompoundTag allTimes = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Long>> entry : unlockTimes.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag times = new CompoundTag();
            entry.getValue().forEach(times::putLong);
            allTimes.put(entry.getKey().toString(), times);
        }
        nbt.put("unlockTimes", allTimes);
        return nbt;
    }

    public void refreshCache() {
        Map<UUID, Set<String>> newCache = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(entry.getValue());
            newCache.put(entry.getKey(), set);
        }
        SERVER_CACHE.keySet().retainAll(newCache.keySet());
        SERVER_CACHE.putAll(newCache);
        VERSION.incrementAndGet();
    }

    public static IndividualStageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            IndividualStageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(
                            new SavedData.Factory<>(IndividualStageData::new, IndividualStageData::load, null),
                            DATA_NAME
                    );
            data.refreshCache();
            return data;
        }
        return new IndividualStageData();
    }

    public void addStage(UUID player, String stage) {
        // Only a real unlock gets a time. Several callers add without checking first, and
        // re-stamping would make an old stage look like the newest one.
        if (playerStages.computeIfAbsent(player, k -> new HashSet<>()).add(stage)) {
            Long now = UnlockClock.now();
            if (now != null) unlockTimes.computeIfAbsent(player, k -> new HashMap<>()).put(stage, now);
        }
        SERVER_CACHE.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(stage);
        VERSION.incrementAndGet();
        setDirty();
    }

    public boolean removeStage(UUID player, String stage) {
        Set<String> stages = playerStages.get(player);
        if (stages != null && stages.remove(stage)) {
            Map<String, Long> times = unlockTimes.get(player);
            if (times != null) times.remove(stage);
            VERSION.incrementAndGet();
            Set<String> cached = SERVER_CACHE.get(player);
            if (cached != null) {
                cached.remove(stage);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public boolean hasStage(UUID player, String stage) {
        Set<String> stages = playerStages.get(player);
        return stages != null && stages.contains(stage);
    }

    public static boolean hasStageCached(UUID player, String stage) {
        Set<String> stages = SERVER_CACHE.get(player);
        return stages != null && stages.contains(stage);
    }

    public Set<String> getUnlockedStages(UUID player) {
        Set<String> stages = playerStages.get(player);
        return stages != null ? new HashSet<>(stages) : new HashSet<>();
    }

    /** A copy, never null. Stages unlocked before times were recorded are absent. */
    public Map<String, Long> getUnlockTimes(UUID player) {
        Map<String, Long> times = unlockTimes.get(player);
        return times != null ? new HashMap<>(times) : new HashMap<>();
    }

    public Set<UUID> getAllPlayersWithStage(String stage) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Set<String>> entry : playerStages.entrySet()) {
            if (entry.getValue().contains(stage)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
