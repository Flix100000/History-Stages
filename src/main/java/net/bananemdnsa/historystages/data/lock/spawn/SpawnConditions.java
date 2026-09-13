package net.bananemdnsa.historystages.data.lock.spawn;

import java.util.Set;

/**
 * Where and when an entity may still spawn. Every slot is optional; an empty slot means "any".
 * All set slots have to hold.
 */
public record SpawnConditions(IdFilter dimensions, IdFilter biomes, SkyCondition sky, IntRange height,
                              TimeOfDay time, IntRange light, WeatherCondition weather,
                              Set<Integer> moonPhases) {

    public static final SpawnConditions EMPTY =
            new SpawnConditions(null, null, null, null, null, null, null, Set.of());

    public SpawnConditions {
        // An IdFilter with no ids matches nothing it was meant to name; treat it as absent so a
        // save/reload round trip can't turn "no condition" into "always false".
        dimensions = dimensions != null && dimensions.ids().isEmpty() ? null : dimensions;
        biomes = biomes != null && biomes.ids().isEmpty() ? null : biomes;
        moonPhases = moonPhases == null ? Set.of() : Set.copyOf(moonPhases);
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public int count() {
        int n = 0;
        if (dimensions != null) n++;
        if (biomes != null) n++;
        if (sky != null) n++;
        if (height != null) n++;
        if (time != null) n++;
        if (light != null) n++;
        if (weather != null) n++;
        if (!moonPhases.isEmpty()) n++;
        return n;
    }

    public SpawnConditions withDimensions(IdFilter filter) {
        return new SpawnConditions(filter, biomes, sky, height, time, light, weather, moonPhases);
    }

    /**
     * @param skipBiome true for a spawn in one of the entry's extra biomes — the biome condition
     *                  would otherwise exclude the very biome the entry adds
     */
    public boolean matches(SpawnContext ctx, boolean skipBiome) {
        if (dimensions != null && !dimensions.allows(ctx.dimension(), java.util.List.of())) return false;
        if (!skipBiome && biomes != null && !biomes.allows(ctx.biomeId(), ctx.biomeTags())) return false;
        if (sky != null && sky.visible() != ctx.skyVisible()) return false;
        if (height != null && !height.contains(ctx.y())) return false;
        if (time != null && (time == TimeOfDay.DAY) != ctx.isDay()) return false;
        if (light != null && !light.contains(ctx.light())) return false;
        if (weather != null && !weather.matches(ctx.raining(), ctx.thundering())) return false;
        return moonPhases.isEmpty() || moonPhases.contains(ctx.moonPhase());
    }
}
