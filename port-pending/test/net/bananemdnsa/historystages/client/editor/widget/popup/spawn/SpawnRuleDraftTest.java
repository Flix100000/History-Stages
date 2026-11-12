package net.bananemdnsa.historystages.client.editor.widget.popup.spawn;

import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.spawn.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpawnRuleDraftTest {

    @Test
    void anUntouchedDraftIsThePlainLock() {
        assertEquals(new EntitySpawnLockEntry("a:b"), SpawnRuleDraft.from(null).toEntry("a:b"));
    }

    @Test
    void aFullRuleSurvivesTheRoundTrip() {
        EntitySpawnLockEntry entry = new EntitySpawnLockEntry("minecraft:zombie", List.of("natural", "spawner"),
                GenerationPhase.AFTER_UNLOCK,
                new SpawnConditions(new IdFilter(FilterMode.EXCLUDE, List.of("minecraft:the_end")),
                        IdFilter.only(List.of("#minecraft:is_forest")), SkyCondition.HIDDEN, new IntRange(-64, 40),
                        TimeOfDay.NIGHT, new IntRange(0, 7), WeatherCondition.RAIN, Set.of(0, 4)),
                new ExtraBiomeSpawns(List.of("minecraft:mushroom_fields"), new SpawnWeight(95, 4, 4), true));
        assertEquals(entry, SpawnRuleDraft.from(entry).toEntry("minecraft:zombie"));
    }

    @Test
    void aFilterModeWithoutIdsIsNoCondition() {
        SpawnRuleDraft draft = SpawnRuleDraft.from(null);
        draft.biomeMode = FilterMode.ONLY;
        draft.moonOn = true;
        assertTrue(draft.toEntry("a:b").getConditions().isEmpty());
        assertEquals(0, draft.locationCount());
    }

    @Test
    void theLastLockedSourceCannotBeUnticked() {
        SpawnRuleDraft draft = SpawnRuleDraft.from(new EntitySpawnLockEntry("a:b", List.of("natural")));
        draft.toggleSource("natural");
        assertEquals(List.of("natural"), draft.toEntry("a:b").getLockSources());
        draft.toggleSource("spawner");
        assertEquals(List.of("natural", "spawner"), draft.toEntry("a:b").getLockSources());
    }

    @Test
    void countsFeedTheTabBadges() {
        SpawnRuleDraft draft = SpawnRuleDraft.from(null);
        draft.sky = SkyCondition.VISIBLE;
        draft.heightOn = true;
        draft.time = TimeOfDay.DAY;
        draft.extraIds.add("minecraft:desert");
        assertEquals(2, draft.locationCount());
        assertEquals(1, draft.timeCount());
    }

    /** What a mob shows for a fixed row and what it stores again must be two different drafts. */
    @Test
    void copiedRowsCarryTheirValueAndLeaveTheOthersAlone() {
        SpawnRuleDraft mob = SpawnRuleDraft.from(new EntitySpawnLockEntry("a:b", List.of("natural", "spawner")));
        mob.time = TimeOfDay.DAY;
        SpawnRuleDraft stage = SpawnRuleDraft.from(null);
        stage.lockedSources.remove("spawner");
        stage.time = TimeOfDay.NIGHT;
        stage.heightOn = true;
        stage.heightMin = 0;

        mob.copyRowsFrom(Set.of(FixedSpawnRule.source("spawner"), FixedSpawnRule.HEIGHT), stage);

        assertFalse(mob.locksSource("spawner"), "spawner is fixed: the stage's value");
        assertTrue(mob.locksSource("natural"), "natural is not fixed: the mob's value");
        assertEquals(TimeOfDay.DAY, mob.time, "time is not fixed");
        assertEquals(new IntRange(0, 320), mob.toEntry("a:b").getConditions().height());
    }

    @Test
    void theFixedValuesKeepNoSourceLocked() {
        SpawnRuleDraft draft = SpawnRuleDraft.from(null);
        for (String source : EntitySpawnLockEntry.ALL_SOURCES) draft.toggleSourceFreely(source);

        EntitySpawnLockEntry values = draft.toFixedValues();

        for (String source : EntitySpawnLockEntry.ALL_SOURCES) assertFalse(values.blocksSource(source));
    }

    @Test
    void fixedRowsAreLeftOutOfTheTabCounts() {
        SpawnRuleDraft draft = SpawnRuleDraft.from(null);
        draft.sky = SkyCondition.HIDDEN;
        draft.weather = WeatherCondition.RAIN;

        assertEquals(0, draft.locationCount(Set.of(FixedSpawnRule.SKY)));
        assertEquals(1, draft.timeCount(Set.of(FixedSpawnRule.SKY)));
    }
}
