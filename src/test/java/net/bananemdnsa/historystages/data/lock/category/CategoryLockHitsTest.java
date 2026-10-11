package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.lock.LockHit;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                new LockHit<>("bronze", StageScope.GLOBAL, "minecraft:the_nether", false),
                new LockHit<>("iron", StageScope.GLOBAL, "minecraft:the_nether", true)), hits,
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
    }

    @Test
    void anUngatedSubjectHasNoHits() {
        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", gating("minecraft:the_nether"));

        assertEquals(List.of(), CategoryLockResolver.hits(dimensions(), "minecraft:overworld",
                stages, StageScope.GLOBAL, StageStateView.NONE_UNLOCKED));
    }
}
