package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.lock.LockHit;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.InterchangeableStages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CategoryLockResolver#hits}: the entries behind a lock answer, for addons that need more
 * than yes or no.
 */
class CategoryLockHitsTest {

    @SuppressWarnings("unchecked")
    private static LockCategory<String> dimensions() {
        return (LockCategory<String>) LockCategories.byId("historystages:dimensions");
    }

    private static StageEntry gating(String... dimensionIds) {
        StageEntry stage = new StageEntry();
        stage.setDimensions(List.of(dimensionIds));
        return stage;
    }

    @Test
    void reportsEveryMatchingEntryWithItsStageAndState() {
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));
        stages.put("unrelated", gating("minecraft:the_end"));
        stages.put("iron", gating("minecraft:the_nether"));

        List<LockHit<String>> hits = CategoryLockResolver.hits(dimensions(), "minecraft:the_nether",
                stages, StageScope.GLOBAL, StageStateView.of(Set.of("iron")));

        assertEquals(List.of(
                new LockHit<>("bronze", StageScope.GLOBAL, "minecraft:the_nether", false, true),
                new LockHit<>("iron", StageScope.GLOBAL, "minecraft:the_nether", true, false)), hits,
                "both gating stages, in stage order, the unlocked one marked as such — and nothing"
                        + " from the stage that gates something else");
    }

    @Test
    void anUnlockedStageStillReportsItsEntry() {
        // The difference to missingStages, and the reason this method exists: an entry that
        // grants needs to be seen precisely when its stage is open.
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));

        List<LockHit<String>> hits = CategoryLockResolver.hits(dimensions(), "minecraft:the_nether",
                stages, StageScope.GLOBAL, StageStateView.of(Set.of("bronze")));

        assertEquals(1, hits.size());
        assertTrue(hits.get(0).unlocked());
        assertFalse(hits.get(0).locks());
    }

    @Test
    void anUnlockedInterchangeableStageFreesTheRestOfItsGroup() {
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));
        stages.put("iron", gating("minecraft:the_nether"));
        stages.put("steel", gating("minecraft:the_nether"));

        List<LockHit<String>> hits = CategoryLockResolver.hits(dimensions(), "minecraft:the_nether",
                stages, StageScope.GLOBAL, StageStateView.of(Set.of("iron")));

        // bronze is still locked, but iron from the same group stands in for it. steel is not
        // interchangeable and keeps locking — the same answer missingStages gives.
        assertFalse(hits.get(0).unlocked());
        assertFalse(hits.get(0).locks(), "bronze is covered by iron");
        assertTrue(hits.get(2).locks(), "steel is required on its own");
        assertEquals(List.of("steel"), CategoryLockResolver.missingStages(dimensions(),
                "minecraft:the_nether", stages, StageScope.GLOBAL, StageStateView.of(Set.of("iron"))));
    }

    @Test
    void withoutAnUnlockedMemberTheWholeGroupStillLocks() {
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));
        stages.put("iron", gating("minecraft:the_nether"));

        List<LockHit<String>> hits = CategoryLockResolver.hits(dimensions(), "minecraft:the_nether",
                stages, StageScope.GLOBAL, StageStateView.NONE_UNLOCKED);

        assertTrue(hits.get(0).locks());
        assertTrue(hits.get(1).locks());
    }

    @AfterEach
    void resetInterchangeable() {
        InterchangeableStages.setForTest(Set.of(), Set.of());
    }

    @Test
    void anUngatedSubjectHasNoHits() {
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));

        assertEquals(List.of(), CategoryLockResolver.hits(dimensions(), "minecraft:overworld",
                stages, StageScope.GLOBAL, StageStateView.NONE_UNLOCKED));
    }
}
