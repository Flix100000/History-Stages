package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the SpawnControl dialog is editing. Unlike the entry it keeps switched-off values around, so
 * flipping "Height" to Any and back does not throw away the numbers.
 *
 * <p>Free of Minecraft on purpose: the pages draw it, this class decides what it means.
 */
public final class SpawnRuleDraft {

    final Set<String> lockedSources = new HashSet<>();
    GenerationPhase phase = GenerationPhase.WHILE_LOCKED;

    FilterMode dimensionMode;
    final List<String> dimensionIds = new ArrayList<>();
    FilterMode biomeMode;
    final List<String> biomeIds = new ArrayList<>();
    SkyCondition sky;
    boolean heightOn;
    int heightMin = -64;
    int heightMax = 320;

    TimeOfDay time;
    boolean lightOn;
    int lightMin = 0;
    int lightMax = 7;
    WeatherCondition weather;
    boolean moonOn;
    final Set<Integer> moonPhases = new TreeSet<>();

    final List<String> extraIds = new ArrayList<>();
    boolean customWeight;
    int weight = 100;
    int minGroup = 1;
    int maxGroup = 4;
    boolean ignoreSpawnRules;

    private SpawnRuleDraft() {}

    public static SpawnRuleDraft from(EntitySpawnLockEntry entry) {
        SpawnRuleDraft d = new SpawnRuleDraft();
        if (entry == null || !entry.hasLockSources()) {
            d.lockedSources.addAll(EntitySpawnLockEntry.ALL_SOURCES);
        } else {
            d.lockedSources.addAll(entry.getLockSources());
        }
        if (entry == null) return d;

        d.phase = entry.getPhase();
        SpawnConditions c = entry.getConditions();
        if (c.dimensions() != null) {
            d.dimensionMode = c.dimensions().mode();
            d.dimensionIds.addAll(c.dimensions().ids());
        }
        if (c.biomes() != null) {
            d.biomeMode = c.biomes().mode();
            d.biomeIds.addAll(c.biomes().ids());
        }
        d.sky = c.sky();
        if (c.height() != null) {
            d.heightOn = true;
            d.heightMin = c.height().min();
            d.heightMax = c.height().max();
        }
        d.time = c.time();
        if (c.light() != null) {
            d.lightOn = true;
            d.lightMin = c.light().min();
            d.lightMax = c.light().max();
        }
        d.weather = c.weather();
        if (!c.moonPhases().isEmpty()) {
            d.moonOn = true;
            d.moonPhases.addAll(c.moonPhases());
        }

        ExtraBiomeSpawns extra = entry.getExtraBiomes();
        if (extra != null) {
            d.extraIds.addAll(extra.ids());
            d.ignoreSpawnRules = extra.ignoreSpawnRules();
            if (extra.weight() != null) {
                d.customWeight = true;
                d.weight = extra.weight().weight();
                d.minGroup = extra.weight().minGroup();
                d.maxGroup = extra.weight().maxGroup();
            }
        }
        return d;
    }

    public EntitySpawnLockEntry toEntry(String entityId) {
        List<String> sources = null;
        if (lockedSources.size() < EntitySpawnLockEntry.ALL_SOURCES.size()) {
            sources = EntitySpawnLockEntry.ALL_SOURCES.stream().filter(lockedSources::contains).toList();
        }
        SpawnConditions conditions = new SpawnConditions(
                filter(dimensionMode, dimensionIds),
                filter(biomeMode, biomeIds),
                sky,
                heightOn ? new IntRange(heightMin, heightMax) : null,
                time,
                lightOn ? new IntRange(lightMin, lightMax) : null,
                weather,
                moonOn ? moonPhases : Set.of());
        ExtraBiomeSpawns extra = extraIds.isEmpty() ? null : new ExtraBiomeSpawns(extraIds,
                customWeight ? new SpawnWeight(weight, minGroup, maxGroup) : null, ignoreSpawnRules);
        return new EntitySpawnLockEntry(entityId, sources, phase, conditions, extra);
    }

    public GenerationPhase phase() {
        return phase;
    }

    public void setPhase(GenerationPhase phase) {
        this.phase = phase;
    }

    /** An entry with no locked source reads back as "all locked", so the last tick stays. */
    public void toggleSource(String source) {
        if (!lockedSources.contains(source)) {
            lockedSources.add(source);
        } else if (lockedSources.size() > 1) {
            lockedSources.remove(source);
        }
    }

    public int locationCount() {
        SpawnConditions c = toEntry("").getConditions();
        return count(c.dimensions() != null, c.biomes() != null, c.sky() != null, c.height() != null);
    }

    public int timeCount() {
        SpawnConditions c = toEntry("").getConditions();
        return count(c.time() != null, c.light() != null, c.weather() != null, !c.moonPhases().isEmpty());
    }

    private static int count(boolean... set) {
        int n = 0;
        for (boolean b : set) if (b) n++;
        return n;
    }

    private static IdFilter filter(FilterMode mode, List<String> ids) {
        return mode == null || ids.isEmpty() ? null : new IdFilter(mode, ids);
    }
}
