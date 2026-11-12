package net.bananemdnsa.historystages.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.api.lock.LockActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixed lock actions: each one is set to the stage's value — locked or allowed — on every entry
 * of its kind, while the entry's other actions stay its own. The entry lists are never rewritten,
 * so releasing a fixed action brings the entry's own value back.
 */
class FixedLockActionsTest {

    private static final Gson GSON = new Gson();

    private static Map<String, Boolean> fixed(Object... pairs) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Boolean) pairs[i + 1]);
        return map;
    }

    @Test
    void withNothingFixedEachEntryDecides() {
        StageEntry stage = new StageEntry();

        assertNull(stage.getFixedItemLockActions());
        assertEquals(List.of("pickup"), stage.effectiveItemLockActions(List.of("pickup")));
        assertNull(stage.effectiveItemLockActions(null), "no list on either side still means every action");
    }

    @Test
    void aFixedLockIsAddedToANarrowedEntry() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true));

        assertEquals(List.of("pickup", "use"), stage.effectiveItemLockActions(List.of("pickup")));
    }

    @Test
    void aFixedAllowTakesTheActionAwayEvenFromAnEntryLockingEverything() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("pickup", false));

        List<String> effective = stage.effectiveItemLockActions(null);

        assertFalse(effective.contains("pickup"));
        assertEquals(LockActions.ITEM.size() - 1, effective.size(),
                "every other action of the item vocabulary stays locked");
    }

    @Test
    void aFixedAllowRemovesTheActionFromANarrowedEntry() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", false, "place", true));

        assertEquals(List.of("pickup", "place"), stage.effectiveItemLockActions(List.of("use", "pickup")));
    }

    @Test
    void fluidsUseTheirOwnMapAndVocabulary() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true));

        assertEquals(List.of("ingredient"), stage.effectiveFluidLockActions(List.of("ingredient")),
                "fixing an item action must not reach the fluids");

        stage.setFixedFluidLockActions(fixed("loot", false));
        assertEquals(Map.of("use", true), stage.getFixedItemLockActions(), "setting fluids must not touch items");
        assertEquals(LockActions.FLUID.size() - 1, stage.effectiveFluidLockActions(null).size());
    }

    /**
     * The point of leaving the entry lists alone: a save while actions are fixed must write them
     * back exactly as they were, or releasing a fixed action later finds the entry changed.
     */
    @Test
    void entryListsSurviveASaveWhileActionsAreFixed() {
        StageEntry stage = new StageEntry();
        stage.setItemEntries(List.of(new ItemEntry("minecraft:bow", null, List.of("use"))));
        stage.setFluidEntries(List.of(new FluidEntry("minecraft:lava", List.of("pickup"), null, null)));
        stage.setFixedItemLockActions(fixed("use", false));
        stage.setFixedFluidLockActions(fixed("use", true));

        StageEntry restored = GSON.fromJson(stage.toJson(), StageEntry.class);

        assertEquals(Map.of("use", false), restored.getFixedItemLockActions());
        assertEquals(Map.of("use", true), restored.getFixedFluidLockActions());
        assertEquals(List.of("use"), restored.getItemEntries().get(0).getLockActions());
        assertEquals(List.of("pickup"), restored.getFluidEntries().get(0).getLockActions());

        restored.setFixedItemLockActions(null);
        assertEquals(List.of("use"),
                restored.effectiveItemLockActions(restored.getItemEntries().get(0).getLockActions()));
    }

    @Test
    void theFileNamesEachActionWithItsValue() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true, "pickup", false));

        String json = stage.toCompactJson();

        assertTrue(json.contains("\"fixed_lock_actions\":{\"items\":{\"use\":true,\"pickup\":false}}"), json);
    }

    @Test
    void releasingEverythingLeavesNoKeyBehind() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true));
        stage.setFixedFluidLockActions(fixed("use", true));
        stage.setFixedItemLockActions(Map.of());
        stage.setFixedFluidLockActions(null);

        assertFalse(stage.toJson().contains("fixed_lock_actions"));
    }

    @Test
    void interactionsUseTheirOwnMapAndVocabulary() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true));
        stage.setFixedInteractionLockActions(fixed("trade", false, "breed", true));

        assertEquals(Map.of("use", true), stage.getFixedItemLockActions(), "setting interactions must not touch items");
        assertEquals(List.of("mount", "breed"), stage.effectiveInteractionLockActions(List.of("mount", "trade")));
        assertEquals(
                net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry.ALL_ACTIONS.size() - 1,
                stage.effectiveInteractionLockActions(null).size(),
                "a mob locking everything loses exactly the action fixed as allowed");
    }

    @Test
    void interactionsSurviveTheFile() {
        StageEntry stage = new StageEntry();
        stage.setFixedInteractionLockActions(fixed("trade", false));

        String json = stage.toCompactJson();
        StageEntry restored = GSON.fromJson(stage.toJson(), StageEntry.class);

        assertTrue(json.contains("\"fixed_lock_actions\":{\"interactions\":{\"trade\":false}}"), json);
        assertEquals(Map.of("trade", false), restored.getFixedInteractionLockActions());
    }

    @Test
    void copyIsDeep() {
        StageEntry stage = new StageEntry();
        stage.setFixedItemLockActions(fixed("use", true));

        StageEntry copy = stage.copy();
        stage.setFixedItemLockActions(fixed("place", true));

        assertEquals(Map.of("use", true), copy.getFixedItemLockActions());
    }
}
