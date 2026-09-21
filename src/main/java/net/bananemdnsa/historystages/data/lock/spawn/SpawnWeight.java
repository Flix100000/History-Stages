package net.bananemdnsa.historystages.data.lock.spawn;

public record SpawnWeight(int weight, int minGroup, int maxGroup) {

    /** For an entity that no biome lists, so there is nothing to copy. */
    public static final SpawnWeight FALLBACK = new SpawnWeight(10, 1, 1);

    public SpawnWeight {
        weight = Math.max(1, weight);
        minGroup = Math.max(1, minGroup);
        maxGroup = Math.max(minGroup, maxGroup);
    }
}
