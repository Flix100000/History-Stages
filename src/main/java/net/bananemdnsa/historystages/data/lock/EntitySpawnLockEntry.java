package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.lock.spawn.ExtraBiomeSpawns;
import net.bananemdnsa.historystages.data.lock.spawn.FilterMode;
import net.bananemdnsa.historystages.data.lock.spawn.IdFilter;
import net.bananemdnsa.historystages.data.lock.spawn.SpawnConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A spawn rule for one entity: which spawn sources it covers, in which lock phase it applies, the
 * conditions under which the entity may still spawn, and biomes it additionally spawns in.
 *
 * <p>In JSON the sources are written as {@code unlock_sources} (the complement — sources that are
 * NOT locked). {@code null} / no field means all sources are locked.
 */
public class EntitySpawnLockEntry {

    /** Canonical ordered list of all recognised spawn sources (medium granularity). */
    public static final List<String> ALL_SOURCES = List.of(
            "natural", "spawner", "structure", "breeding", "summon", "spawn_egg"
    );

    private final String id;
    private final List<String> lockSources; // null = all sources locked, empty = treated as all locked
    private final GenerationPhase phase;
    private final SpawnConditions conditions;
    private final ExtraBiomeSpawns extraBiomes; // null = none
    private final List<String> readProblems; // JSON-read diagnostics only; not equals/hashCode, not serialized

    public EntitySpawnLockEntry(String id) {
        this(id, null, null);
    }

    public EntitySpawnLockEntry(String id, List<String> lockSources) {
        this(id, lockSources, null);
    }

    /** The pre-SpawnControl shape: an allow-list of dimensions is an "only in" condition. */
    public EntitySpawnLockEntry(String id, List<String> lockSources, List<String> unlockDimensions) {
        this(id, lockSources, GenerationPhase.WHILE_LOCKED,
                unlockDimensions != null && !unlockDimensions.isEmpty()
                        ? SpawnConditions.EMPTY.withDimensions(IdFilter.only(unlockDimensions))
                        : SpawnConditions.EMPTY,
                null);
    }

    public EntitySpawnLockEntry(String id, List<String> lockSources, GenerationPhase phase,
                                SpawnConditions conditions, ExtraBiomeSpawns extraBiomes) {
        this(id, lockSources, phase, conditions, extraBiomes, List.of());
    }

    private EntitySpawnLockEntry(String id, List<String> lockSources, GenerationPhase phase,
                                 SpawnConditions conditions, ExtraBiomeSpawns extraBiomes, List<String> readProblems) {
        this.id = id;
        this.lockSources = (lockSources != null && !lockSources.isEmpty()) ? List.copyOf(lockSources) : null;
        this.phase = phase != null ? phase : GenerationPhase.WHILE_LOCKED;
        this.conditions = conditions != null ? conditions : SpawnConditions.EMPTY;
        this.extraBiomes = extraBiomes != null && !extraBiomes.ids().isEmpty() ? extraBiomes : null;
        this.readProblems = List.copyOf(readProblems);
    }

    public String getId() { return id; }

    /** Returns null if all sources are locked, otherwise the explicit list of locked sources. */
    public List<String> getLockSources() { return lockSources; }

    public boolean hasLockSources() { return lockSources != null && !lockSources.isEmpty(); }

    /** True if the given source is blocked by this entry. */
    public boolean blocksSource(String source) {
        return lockSources == null || lockSources.contains(source);
    }

    public GenerationPhase getPhase() { return phase; }

    public SpawnConditions getConditions() { return conditions; }

    /** Null when the entry adds no biomes. */
    public ExtraBiomeSpawns getExtraBiomes() { return extraBiomes; }

    /** The legacy allow-list view: only an "only in" dimension condition has one. */
    public List<String> getUnlockDimensions() {
        IdFilter dims = conditions.dimensions();
        return dims != null && dims.mode() == FilterMode.ONLY ? new ArrayList<>(dims.ids()) : null;
    }

    public boolean hasUnlockDimensions() { return getUnlockDimensions() != null; }

    /** True if the dimension condition keeps the entity from spawning there. */
    public boolean blocksDimension(String dimension) {
        IdFilter dims = conditions.dimensions();
        return dims == null || !dims.allows(dimension, List.of());
    }

    /** Whether the pre-SpawnControl JSON shape can hold this entry without losing anything. */
    public boolean isLegacyExpressible() {
        IdFilter dims = conditions.dimensions();
        return phase == GenerationPhase.WHILE_LOCKED && extraBiomes == null
                && conditions.withDimensions(null).isEmpty()
                && (dims == null || dims.mode() == FilterMode.ONLY);
    }

    /** Never null. Problems the JSON reader dropped rather than failed on; see the adapter. */
    public List<String> getReadProblems() { return readProblems; }

    /** Copy carrying read-time diagnostics — used only by the adapter, so it stays out of equals/hashCode. */
    public EntitySpawnLockEntry withReadProblems(List<String> problems) {
        return new EntitySpawnLockEntry(id, lockSources, phase, conditions, extraBiomes, problems);
    }

    public EntitySpawnLockEntry copy() {
        return new EntitySpawnLockEntry(id, lockSources, phase, conditions, extraBiomes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntitySpawnLockEntry other)) return false;
        return Objects.equals(id, other.id) && Objects.equals(lockSources, other.lockSources)
                && phase == other.phase && conditions.equals(other.conditions)
                && Objects.equals(extraBiomes, other.extraBiomes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lockSources, phase, conditions, extraBiomes);
    }
}
