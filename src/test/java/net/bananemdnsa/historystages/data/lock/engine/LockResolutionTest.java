package net.bananemdnsa.historystages.data.lock.engine;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockResolutionTest {

    private static final StageStateView BRONZE_ONLY = StageStateView.of(Set.of("bronze"));
    private static final StageScope G = StageScope.GLOBAL;

    @AfterEach
    void clearFlags() {
        InterchangeableStages.setForTest(Set.of(), Set.of());
    }

    @Test
    void ungatedSubjectIsNeverLocked() {
        assertFalse(LockResolution.isLocked(G, List.of(), BRONZE_ONLY));
        assertFalse(LockResolution.isLockedLenient(List.of(), BRONZE_ONLY, List.of(), BRONZE_ONLY));
    }

    @Test
    void strictLocksOnASingleMissingStage() {
        assertTrue(LockResolution.isLocked(G, List.of("bronze", "iron"), BRONZE_ONLY));
    }

    @Test
    void strictUnlocksOnlyWhenEveryGatingStageIsUnlocked() {
        assertFalse(LockResolution.isLocked(G, List.of("bronze"), BRONZE_ONLY));
    }

    @Test
    void strictAcrossScopesLocksWhenEitherScopeIsMissing() {
        StageStateView noneIndividual = StageStateView.NONE_UNLOCKED;
        assertTrue(LockResolution.isLocked(List.of("bronze"), BRONZE_ONLY, List.of("quest"), noneIndividual));
        assertFalse(LockResolution.isLocked(List.of("bronze"), BRONZE_ONLY, List.of(), noneIndividual));
    }

    @Test
    void lenientUnlocksAsSoonAsOneGatingStageIsUnlocked() {
        // Gated by two stages, one of them unlocked -> LENIENT treats it as available.
        assertFalse(LockResolution.isLockedLenient(
                List.of("bronze", "iron"), BRONZE_ONLY, List.of(), StageStateView.NONE_UNLOCKED));
    }

    @Test
    void lenientLocksWhenNoGatingStageIsUnlockedInEitherScope() {
        assertTrue(LockResolution.isLockedLenient(
                List.of("iron"), BRONZE_ONLY, List.of("quest"), StageStateView.NONE_UNLOCKED));
    }

    @Test
    void lenientCountsAnUnlockedIndividualStageToo() {
        StageStateView questDone = StageStateView.of(Set.of("quest"));
        assertFalse(LockResolution.isLockedLenient(
                List.of("iron"), BRONZE_ONLY, List.of("quest"), questDone));
    }

    @Test
    void missingStagesKeepsEngineOrderAndDropsUnlockedOnes() {
        List<String> missing = LockResolution.missingStages(G, List.of("iron", "bronze", "steel"), BRONZE_ONLY);
        assertEquals(List.of("iron", "steel"), missing);
    }

    @Test
    void missingStagesReturnsEmptyListWhenAllUnlocked() {
        assertEquals(List.of(), LockResolution.missingStages(G, List.of("bronze"), BRONZE_ONLY));
    }

    // ---- interchangeable stages ---------------------------------------------------------

    @Test
    void oneUnlockedFlaggedStageIsEnough() {
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        assertFalse(LockResolution.isLocked(G, List.of("bronze", "iron"), BRONZE_ONLY));
        assertEquals(List.of(), LockResolution.missingStages(G, List.of("bronze", "iron"), BRONZE_ONLY));
    }

    @Test
    void flaggedGroupWithNothingUnlockedStaysLocked() {
        InterchangeableStages.setForTest(Set.of("iron", "steel"), Set.of());
        assertTrue(LockResolution.isLocked(G, List.of("iron", "steel"), BRONZE_ONLY));
        assertEquals(List.of("iron", "steel"),
                LockResolution.missingStages(G, List.of("iron", "steel"), BRONZE_ONLY));
    }

    @Test
    void unflaggedStageKeepsItsLockEvenWhenTheGroupIsSatisfied() {
        // (bronze OR iron) AND endgame
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        List<String> gating = List.of("bronze", "endgame", "iron");
        assertTrue(LockResolution.isLocked(G, gating, BRONZE_ONLY));
        assertEquals(List.of("endgame"), LockResolution.missingStages(G, gating, BRONZE_ONLY));

        StageStateView both = StageStateView.of(Set.of("bronze", "endgame"));
        assertFalse(LockResolution.isLocked(G, gating, both));
    }

    @Test
    void missingSplitsRequiredFromTheOpenGroup() {
        InterchangeableStages.setForTest(Set.of("iron", "steel"), Set.of());
        LockResolution.Missing missing =
                LockResolution.missing(G, List.of("iron", "endgame", "steel"), BRONZE_ONLY);
        assertEquals(List.of("endgame"), missing.required());
        assertEquals(List.of("iron", "steel"), missing.anyOf());
        assertFalse(missing.isEmpty());
    }

    @Test
    void missingDropsTheGroupOnceOneMemberIsUnlocked() {
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        LockResolution.Missing missing =
                LockResolution.missing(G, List.of("bronze", "iron"), BRONZE_ONLY);
        assertTrue(missing.isEmpty());
    }

    @Test
    void flagsAreReadPerScope() {
        // Flagged only as an individual stage: the global question stays AND.
        InterchangeableStages.setForTest(Set.of(), Set.of("bronze", "iron"));
        assertTrue(LockResolution.isLocked(G, List.of("bronze", "iron"), BRONZE_ONLY));
        assertFalse(LockResolution.isLocked(StageScope.INDIVIDUAL, List.of("bronze", "iron"), BRONZE_ONLY));
    }
}
