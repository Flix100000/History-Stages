package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.ExtraBiomeSpawns;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable snapshot of the spawn rules that apply right now. Free of Minecraft types so it can be
 * unit-tested; {@link SpawnControlGate} reads the level into a {@link SpawnContext}.
 *
 * <p>Holds only active entries: a while-locked rule of a locked stage, or an after-unlock rule of an
 * unlocked one. Unlike structure generation, an after-unlock spawn rule does nothing before the
 * unlock — "from the Iron Age on, zombies also spawn in mushroom fields" must not mean "no zombies
 * until the Iron Age".
 */
public final class SpawnRuleSet {

    public static final SpawnRuleSet EMPTY = new SpawnRuleSet(Map.of(), List.of(), Set.of());

    /** An extra-biome rule that is currently active, with the conditions it still has to meet. */
    public record ActiveExtra(String entityId, ExtraBiomeSpawns spawns, SpawnConditions conditions) {}

    private final Map<String, List<EntitySpawnLockEntry>> activeByEntity;
    private final List<ActiveExtra> extras;
    private final Set<String> placementOverrides;

    private SpawnRuleSet(Map<String, List<EntitySpawnLockEntry>> activeByEntity, List<ActiveExtra> extras,
                         Set<String> placementOverrides) {
        this.activeByEntity = activeByEntity;
        this.extras = extras;
        this.placementOverrides = placementOverrides;
    }

    public static SpawnRuleSet build(Map<String, List<EntitySpawnLockEntry>> entriesByStage, Set<String> unlocked) {
        Map<String, List<EntitySpawnLockEntry>> active = new HashMap<>();
        List<ActiveExtra> extras = new ArrayList<>();
        Set<String> overrides = new HashSet<>();

        // Sorted, so the order extras are offered in does not depend on hash order.
        for (String stageId : new TreeSet<>(entriesByStage.keySet())) {
            boolean stageUnlocked = unlocked.contains(stageId);
            for (EntitySpawnLockEntry entry : entriesByStage.get(stageId)) {
                if (entry == null || entry.getId() == null || entry.getId().isEmpty()) continue;
                boolean isActive = (entry.getPhase() == GenerationPhase.WHILE_LOCKED) != stageUnlocked;
                if (!isActive) continue;

                active.computeIfAbsent(entry.getId(), k -> new ArrayList<>()).add(entry);
                ExtraBiomeSpawns extra = entry.getExtraBiomes();
                if (extra != null) {
                    extras.add(new ActiveExtra(entry.getId(), extra, entry.getConditions()));
                    if (extra.ignoreSpawnRules()) overrides.add(entry.getId());
                }
            }
        }
        if (active.isEmpty()) return EMPTY;

        // Deep-freeze: chunk generation can reach this from worker threads.
        Map<String, List<EntitySpawnLockEntry>> frozen = new HashMap<>();
        active.forEach((id, list) -> frozen.put(id, List.copyOf(list)));
        return new SpawnRuleSet(Map.copyOf(frozen), List.copyOf(extras), Set.copyOf(overrides));
    }

    public boolean isActive() {
        return !activeByEntity.isEmpty();
    }

    public boolean hasRulesFor(String entityId) {
        return activeByEntity.containsKey(entityId);
    }

    /** @param source null when the spawn reason is unknown; every entry covers it then */
    public boolean isAllowed(String entityId, String source, SpawnContext ctx) {
        List<EntitySpawnLockEntry> entries = activeByEntity.get(entityId);
        if (entries == null) return true;
        for (EntitySpawnLockEntry entry : entries) {
            if (source != null && !entry.blocksSource(source)) continue;
            if (!entryAllows(entry, ctx)) return false;
        }
        return true;
    }

    static boolean entryAllows(EntitySpawnLockEntry entry, SpawnContext ctx) {
        ExtraBiomeSpawns extra = entry.getExtraBiomes();
        if (extra != null && extra.matchesBiome(ctx.biomeId(), ctx.biomeTags())) {
            return entry.getConditions().matches(ctx, true);
        }
        // No conditions: a plain lock blocks, but a rule that only adds biomes restricts nothing.
        if (entry.getConditions().isEmpty()) return extra != null;
        return entry.getConditions().matches(ctx, false);
    }

    public List<ActiveExtra> extras() {
        return extras;
    }

    /** Cheap pre-check for the placement events, which fire for every spawn attempt. */
    public boolean hasPlacementOverrides(String entityId) {
        return placementOverrides.contains(entityId);
    }

    public boolean forcesPlacement(String entityId, SpawnContext ctx) {
        if (!placementOverrides.contains(entityId)) return false;
        for (ActiveExtra extra : extras) {
            if (extra.entityId().equals(entityId) && extra.spawns().ignoreSpawnRules()
                    && extra.spawns().matchesBiome(ctx.biomeId(), ctx.biomeTags())
                    && extra.conditions().matches(ctx, true)) {
                return true;
            }
        }
        return false;
    }
}
