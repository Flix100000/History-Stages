package net.bananemdnsa.historystages.data.lock.spawn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpawnValueTypesTest {

    @Test
    void enumsRoundTripAndRejectUnknownValues() {
        assertEquals(FilterMode.EXCLUDE, FilterMode.parse(FilterMode.EXCLUDE.serialize()));
        assertEquals(SkyCondition.HIDDEN, SkyCondition.parse("hidden"));
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.parse("night"));
        assertEquals(WeatherCondition.THUNDER, WeatherCondition.parse("thunder"));
        assertNull(FilterMode.parse("sometimes"));
        assertNull(WeatherCondition.parse(null));
    }

    @Test
    void aReversedRangeIsSwappedAndBothEndsCount() {
        IntRange range = new IntRange(40, -64);
        assertEquals(-64, range.min());
        assertTrue(range.contains(-64));
        assertTrue(range.contains(40));
        assertFalse(range.contains(41));
    }

    @Test
    void onlyFilterPassesListedIdsAndTags() {
        IdFilter filter = new IdFilter(FilterMode.ONLY, List.of("minecraft:swamp", "#minecraft:is_forest"));
        assertTrue(filter.allows("minecraft:swamp", List.of()));
        assertTrue(filter.allows("minecraft:birch_forest", List.of("minecraft:is_forest")));
        assertFalse(filter.allows("minecraft:desert", List.of("minecraft:is_hot")));
    }

    @Test
    void excludeFilterIsTheInverse() {
        IdFilter filter = new IdFilter(FilterMode.EXCLUDE, List.of("#minecraft:is_forest"));
        assertFalse(filter.allows("minecraft:forest", List.of("minecraft:is_forest")));
        assertTrue(filter.allows("minecraft:desert", List.of()));
    }

    @Test
    void spawnWeightClampsToSaneValues() {
        SpawnWeight weight = new SpawnWeight(0, 5, 2);
        assertEquals(1, weight.weight());
        assertEquals(5, weight.minGroup());
        assertEquals(5, weight.maxGroup(), "max below min is lifted to min");
    }

    @Test
    void extraBiomesMatchIdsAndTags() {
        ExtraBiomeSpawns extra = new ExtraBiomeSpawns(List.of("#c:is_mushroom"), null, false);
        assertTrue(extra.matchesBiome("minecraft:mushroom_fields", List.of("c:is_mushroom")));
        assertFalse(extra.matchesBiome("minecraft:plains", List.of()));
    }

    @Test
    void dayIsTheFirst13000TicksOfEachDay() {
        assertTrue(context(0).isDay());
        assertTrue(context(12999).isDay());
        assertFalse(context(13000).isDay());
        assertTrue(context(24000 + 100).isDay(), "day time keeps counting past 24000");
    }

    private static SpawnContext context(long dayTime) {
        return new SpawnContext("minecraft:overworld", "minecraft:plains", List.of(), 64, 15, true,
                dayTime, false, false, 0);
    }

    @Test
    void nullListsAreTreatedAsEmptyRatherThanThrowing() {
        SpawnContext ctx = new SpawnContext("minecraft:overworld", "minecraft:plains", null, 64, 15, true,
                0, false, false, 0);
        assertEquals(List.of(), ctx.biomeTags());

        IdFilter filter = new IdFilter(FilterMode.ONLY, null);
        assertEquals(List.of(), filter.ids());

        ExtraBiomeSpawns extra = new ExtraBiomeSpawns(null, null, false);
        assertEquals(List.of(), extra.ids());
    }
}
