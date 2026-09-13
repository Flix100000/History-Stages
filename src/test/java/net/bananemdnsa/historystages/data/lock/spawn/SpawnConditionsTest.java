package net.bananemdnsa.historystages.data.lock.spawn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpawnConditionsTest {

    private static final SpawnContext NIGHT_CAVE = new SpawnContext("minecraft:overworld",
            "minecraft:forest", List.of("minecraft:is_forest"), 20, 3, false, 18000, true, false, 4);

    private static SpawnConditions only(java.util.function.UnaryOperator<Builder> fill) {
        return fill.apply(new Builder()).build();
    }

    @Test
    void emptyConditionsMatchEverything() {
        assertTrue(SpawnConditions.EMPTY.isEmpty());
        assertTrue(SpawnConditions.EMPTY.matches(NIGHT_CAVE, false));
        assertEquals(0, SpawnConditions.EMPTY.count());
    }

    @Test
    void eachConditionCanRejectOnItsOwn() {
        assertFalse(only(b -> b.dimensions(IdFilter.only(List.of("minecraft:the_nether")))).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.biomes(new IdFilter(FilterMode.EXCLUDE, List.of("#minecraft:is_forest")))).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.sky(SkyCondition.VISIBLE)).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.height(new IntRange(30, 60))).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.time(TimeOfDay.DAY)).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.light(new IntRange(8, 15))).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.weather(WeatherCondition.CLEAR)).matches(NIGHT_CAVE, false));
        assertFalse(only(b -> b.moon(Set.of(0))).matches(NIGHT_CAVE, false));
    }

    @Test
    void allMatchingConditionsTogetherPass() {
        SpawnConditions all = only(b -> b.dimensions(IdFilter.only(List.of("minecraft:overworld")))
                .biomes(IdFilter.only(List.of("#minecraft:is_forest"))).sky(SkyCondition.HIDDEN)
                .height(new IntRange(-64, 40)).time(TimeOfDay.NIGHT).light(new IntRange(0, 7))
                .weather(WeatherCondition.RAIN).moon(Set.of(4)));
        assertTrue(all.matches(NIGHT_CAVE, false));
        assertEquals(8, all.count());
    }

    @Test
    void skipBiomeIgnoresOnlyTheBiomeCondition() {
        SpawnConditions c = only(b -> b.biomes(IdFilter.only(List.of("minecraft:swamp"))).time(TimeOfDay.NIGHT));
        assertFalse(c.matches(NIGHT_CAVE, false));
        assertTrue(c.matches(NIGHT_CAVE, true));
        assertFalse(only(b -> b.biomes(IdFilter.only(List.of("minecraft:swamp"))).time(TimeOfDay.DAY))
                .matches(NIGHT_CAVE, true));
    }

    @Test
    void withoutDimensionsKeepsTheRest() {
        SpawnConditions c = only(b -> b.dimensions(IdFilter.only(List.of("a:b"))).time(TimeOfDay.DAY));
        assertEquals(only(b -> b.time(TimeOfDay.DAY)), c.withDimensions(null));
    }

    @Test
    void anIdFilterWithNoIdsNormalisesToNull() {
        SpawnConditions c = new SpawnConditions(new IdFilter(FilterMode.EXCLUDE, List.of()), null, null, null,
                null, null, null, Set.of());
        assertNull(c.dimensions());
        assertTrue(c.isEmpty());
    }

    /** Test-only convenience; the record constructor with eight nullable slots is unreadable in asserts. */
    private static final class Builder {
        IdFilter dimensions, biomes; SkyCondition sky; IntRange height; TimeOfDay time; IntRange light;
        WeatherCondition weather; Set<Integer> moon = Set.of();
        Builder dimensions(IdFilter v) { dimensions = v; return this; }
        Builder biomes(IdFilter v) { biomes = v; return this; }
        Builder sky(SkyCondition v) { sky = v; return this; }
        Builder height(IntRange v) { height = v; return this; }
        Builder time(TimeOfDay v) { time = v; return this; }
        Builder light(IntRange v) { light = v; return this; }
        Builder weather(WeatherCondition v) { weather = v; return this; }
        Builder moon(Set<Integer> v) { moon = v; return this; }
        SpawnConditions build() { return new SpawnConditions(dimensions, biomes, sky, height, time, light, weather, moon); }
    }
}
