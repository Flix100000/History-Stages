package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import net.bananemdnsa.historystages.data.lock.engine.InterchangeableStages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLockRulesTest {

    private static final StageStateView NOTHING = StageStateView.NONE_UNLOCKED;

    private static Function<String, List<SlotLockEntry>> entries(Map<String, List<SlotLockEntry>> byStage) {
        return id -> byStage.getOrDefault(id, List.of());
    }

    private static SlotLockEntry player(SlotLockMode mode, Integer... slots) {
        return new SlotLockEntry("player", List.of(slots), mode);
    }

    /** Without flags set, the first lookup scans the live stage store, which needs Minecraft. */
    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void clearFlags() {
        InterchangeableStages.setForTest(Set.of(), Set.of());
    }

    @Test
    void unlockedStageLeavesSlotOpen() {
        SlotLockState state = SlotLockRules.resolve(List.of("bronze"), StageStateView.of(Set.of("bronze")),
                List.of(), NOTHING,
                entries(Map.of("bronze", List.of(player(SlotLockMode.HIDE, 3)))), "player", 3);
        assertEquals(SlotLockState.OPEN, state);
    }

    @Test
    void lockedStageWithOverlayGivesOverlay() {
        SlotLockState state = SlotLockRules.resolve(List.of("bronze"), NOTHING, List.of(), NOTHING,
                entries(Map.of("bronze", List.of(player(SlotLockMode.OVERLAY, 3)))), "player", 3);
        assertEquals(SlotLockState.OVERLAY, state);
    }

    @Test
    void hideBeatsOverlayAcrossStages() {
        SlotLockState state = SlotLockRules.resolve(List.of("bronze", "iron"), NOTHING, List.of(), NOTHING,
                entries(Map.of(
                        "bronze", List.of(player(SlotLockMode.OVERLAY, 3)),
                        "iron", List.of(player(SlotLockMode.HIDE, 3)))), "player", 3);
        assertEquals(SlotLockState.HIDDEN, state);
    }

    /** A stage that names the slot under another target must not lend its mode. */
    @Test
    void modeComesOnlyFromEntriesNamingThisSlot() {
        SlotLockState state = SlotLockRules.resolve(List.of("bronze"), NOTHING, List.of(), NOTHING,
                entries(Map.of("bronze", List.of(
                        player(SlotLockMode.OVERLAY, 3),
                        new SlotLockEntry("minecraft:chest", List.of(3), SlotLockMode.HIDE)))), "player", 3);
        assertEquals(SlotLockState.OVERLAY, state);
    }

    @Test
    void individualStageLocksPlayerSlot() {
        SlotLockState state = SlotLockRules.resolve(List.of(), NOTHING, List.of("mine"), NOTHING,
                entries(Map.of("mine", List.of(player(SlotLockMode.HIDE, 0)))), "player", 0);
        assertTrue(state.blocks());
    }

    @Test
    void interchangeableStagesFollowLockResolution() {
        InterchangeableStages.setForTest(Set.of("bronze", "iron"), Set.of());
        SlotLockState state = SlotLockRules.resolve(List.of("bronze", "iron"), StageStateView.of(Set.of("iron")),
                List.of(), NOTHING,
                entries(Map.of(
                        "bronze", List.of(player(SlotLockMode.HIDE, 3)),
                        "iron", List.of(player(SlotLockMode.HIDE, 3)))), "player", 3);
        assertEquals(SlotLockState.OPEN, state);
    }

    /** Look and hint come from a stage that still locks the slot, not from one already passed. */
    @Test
    void governingEntryComesFromAStillLockedStage() {
        SlotLockEntry bronze = player(SlotLockMode.OVERLAY, 3);
        bronze.setHint("bronze");
        SlotLockEntry iron = player(SlotLockMode.OVERLAY, 3);
        iron.setHint("iron");
        SlotLockEntry governing = SlotLockRules.governing(List.of("bronze", "iron"), StageStateView.of(Set.of("bronze")),
                List.of(), NOTHING,
                entries(Map.of("bronze", List.of(bronze), "iron", List.of(iron))), "player", 3);
        assertEquals("iron", governing.hint());
    }

    /** When Hide wins, the look comes from the hiding entry, not from an overlay one beside it. */
    @Test
    void governingEntryMatchesTheWinningMode() {
        SlotLockEntry overlay = player(SlotLockMode.OVERLAY, 3);
        SlotLockEntry hide = player(SlotLockMode.HIDE, 3);
        SlotLockEntry governing = SlotLockRules.governing(List.of("a", "b"), NOTHING, List.of(), NOTHING,
                entries(Map.of("a", List.of(overlay), "b", List.of(hide))), "player", 3);
        assertEquals(SlotLockMode.HIDE, governing.mode());
    }

    /** A stage that only locks must not cover the look another stage took the trouble to style. */
    @Test
    void customisedLookWinsOverDefault() {
        SlotLockEntry plain = player(SlotLockMode.OVERLAY, 3);
        SlotLockEntry styled = player(SlotLockMode.OVERLAY, 3);
        var look = new net.bananemdnsa.historystages.data.lock.SlotLook();
        look.setColor("#AA0000");
        styled.setLook(look);
        // "aaa" sorts first, so only the customised-first rule can pick "zzz".
        SlotLockEntry governing = SlotLockRules.governing(List.of("aaa", "zzz"), NOTHING, List.of(), NOTHING,
                entries(Map.of("aaa", List.of(plain), "zzz", List.of(styled))), "player", 3);
        assertEquals(styled, governing);
    }

    @Test
    void hintCountsAsCustomised() {
        SlotLockEntry plain = player(SlotLockMode.OVERLAY, 3);
        SlotLockEntry hinted = player(SlotLockMode.OVERLAY, 3);
        hinted.setHint("soon");
        SlotLockEntry governing = SlotLockRules.governing(List.of("aaa", "zzz"), NOTHING, List.of(), NOTHING,
                entries(Map.of("aaa", List.of(plain), "zzz", List.of(hinted))), "player", 3);
        assertEquals("soon", governing.hint());
    }

    /** Engine order comes from a hash map; the answer must not depend on it. */
    @Test
    void tieIsBrokenByStageIdAlphabetically() {
        SlotLockEntry a = player(SlotLockMode.OVERLAY, 3);
        a.setHint("from b_stage");
        SlotLockEntry b = player(SlotLockMode.OVERLAY, 3);
        b.setHint("from a_stage");
        var byStage = entries(Map.of("b_stage", List.of(a), "a_stage", List.of(b)));
        assertEquals("from a_stage", SlotLockRules.governing(List.of("b_stage", "a_stage"), NOTHING,
                List.of(), NOTHING, byStage, "player", 3).hint());
        assertEquals("from a_stage", SlotLockRules.governing(List.of("a_stage", "b_stage"), NOTHING,
                List.of(), NOTHING, byStage, "player", 3).hint());
    }

    @Test
    void openSlotHasNoGoverningEntry() {
        assertEquals(null, SlotLockRules.governing(List.of(), NOTHING, List.of(), NOTHING,
                entries(Map.of()), "player", 3));
    }

    @Test
    void ungatedSlotIsOpen() {
        assertEquals(SlotLockState.OPEN,
                SlotLockRules.resolve(List.of(), NOTHING, List.of(), NOTHING, entries(Map.of()), "player", 3));
    }
}
