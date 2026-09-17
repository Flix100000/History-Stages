package net.bananemdnsa.historystages.data.saveddata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StageData extends SavedData {
    private final List<String> unlockedStages = new ArrayList<>();
    /** Game time each stage was unlocked at. Stages unlocked before times were recorded have none. */
    private final Map<String, Long> unlockTimes = new HashMap<>();
    private static final String DATA_NAME = "historystages_global";

    public static final Set<String> SERVER_CACHE = ConcurrentHashMap.newKeySet();

    /**
     * Mirror of the unlock times for the sync packet, which is built in many places that only
     * have {@link #SERVER_CACHE} to hand.
     */
    public static final Map<String, Long> SERVER_UNLOCK_TIMES = new ConcurrentHashMap<>();

    /**
     * Bumped on every change to {@link #SERVER_CACHE}, so anything derived from it can tell that
     * it went stale without being told.
     *
     * <p>A notification would have to be remembered at each of the places that write the cache; a
     * counter that lives beside the data cannot be forgotten in the same way. {@code UnlockedStateGuardTest} keeps
 * the mutations inside this class, which is what makes the counter trustworthy.
     */
    private static final java.util.concurrent.atomic.AtomicLong VERSION =
            new java.util.concurrent.atomic.AtomicLong();

    /** Changes whenever the global unlocked set does. Never persisted, never sent. */
    public static long cacheVersion() {
        return VERSION.get();
    }

    /**
     * Replaces the cache with exactly these stages. The pedestal used to clear and refill
     * {@link #SERVER_CACHE} itself, which left anything derived from it holding stale data.
     */
    public static void replaceCache(java.util.Collection<String> stages) {
        SERVER_CACHE.clear();
        SERVER_CACHE.addAll(stages);
        VERSION.incrementAndGet();
    }

    public StageData() {
        SERVER_CACHE.clear();
        SERVER_UNLOCK_TIMES.clear();
        VERSION.incrementAndGet();
    }

    public static StageData load(CompoundTag nbt) {
        StageData data = new StageData();
        ListTag list = nbt.getList("stages", Tag.TAG_STRING);
        SERVER_CACHE.clear();
        for (int i = 0; i < list.size(); i++) {
            String stage = list.getString(i);
            data.unlockedStages.add(stage);
            SERVER_CACHE.add(stage);
        }
        // Older saves have no such tag; getCompound hands back an empty one.
        CompoundTag times = nbt.getCompound("unlockTimes");
        for (String stage : data.unlockedStages) {
            if (times.contains(stage, Tag.TAG_LONG)) data.unlockTimes.put(stage, times.getLong(stage));
        }
        SERVER_UNLOCK_TIMES.putAll(data.unlockTimes);
        VERSION.incrementAndGet();
        net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (String s : unlockedStages) {
            list.add(StringTag.valueOf(s));
        }
        nbt.put("stages", list);
        CompoundTag times = new CompoundTag();
        unlockTimes.forEach(times::putLong);
        nbt.put("unlockTimes", times);
        return nbt;
    }

    /**
     * Replaces the cache contents atomically: adds new entries first, then removes stale ones.
     * This avoids the brief empty-cache window that clear()+addAll() would cause.
     */
    public static void refreshCache(List<String> stages) {
        Set<String> newSet = ConcurrentHashMap.newKeySet();
        newSet.addAll(stages);
        // Compared before the swap, and the counter only moves when the set really is different.
        // This runs on every StageData.get(), which happens many times a tick, and a caller that
        // throws its work away whenever the counter moves — the recipe gate does — would then
        // rebuild constantly. Nothing derived from the cache can be stale while the cache is
        // unchanged, so saying nothing here is not a shortcut.
        boolean changed = !SERVER_CACHE.equals(newSet);
        SERVER_CACHE.addAll(newSet);
        SERVER_CACHE.retainAll(newSet);
        if (changed) VERSION.incrementAndGet();
    }

    public static StageData get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            StageData data = serverLevel.getServer().overworld().getDataStorage()
                    .computeIfAbsent(StageData::load, StageData::new, DATA_NAME);

            refreshCache(data.unlockedStages);
            // load() of a second instance (a save/load round trip) replaces the mirror, so it is
            // put back to the instance that is actually live.
            SERVER_UNLOCK_TIMES.keySet().retainAll(data.unlockTimes.keySet());
            SERVER_UNLOCK_TIMES.putAll(data.unlockTimes);
            // The generation counters have no level of their own and worldgen threads must not
            // reach into data storage, so they are primed from here.
            StructureGenerationCountData.get(serverLevel);

            return data;
        }
        return new StageData();
    }

    public void addStage(String stage) {
        if (!unlockedStages.contains(stage)) {
            unlockedStages.add(stage);
            SERVER_CACHE.add(stage);
            Long now = UnlockClock.now();
            if (now != null) {
                unlockTimes.put(stage, now);
                SERVER_UNLOCK_TIMES.put(stage, now);
            }
            VERSION.incrementAndGet();
            // Before the rebuild: the reset lookup needs the snapshot that still describes the
            // phase being left behind.
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.onStageLockChanged(stage, true);
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            setDirty();
        }
    }

    public void removeStage(String stage) {
        if (unlockedStages.remove(stage)) {
            SERVER_CACHE.remove(stage);
            unlockTimes.remove(stage);
            SERVER_UNLOCK_TIMES.remove(stage);
            VERSION.incrementAndGet();
            // Before the rebuild, for the same reason as in addStage.
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.onStageLockChanged(stage, false);
            net.bananemdnsa.historystages.util.lock.StructureGenerationGate.rebuild();
            setDirty();
        }
    }

    public boolean hasStage(String stage) {
        return unlockedStages.contains(stage);
    }

    public List<String> getUnlockedStages() {
        return new ArrayList<>(unlockedStages);
    }

    /** A copy. Stages unlocked before times were recorded are absent. */
    public Map<String, Long> getUnlockTimes() {
        return new HashMap<>(unlockTimes);
    }
}
