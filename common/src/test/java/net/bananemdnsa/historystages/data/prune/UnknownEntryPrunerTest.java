package net.bananemdnsa.historystages.data.prune;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything in the namespace "gone" belongs to a mod that was taken out of the pack. Everything
 * else is installed.
 */
class UnknownEntryPrunerTest {

    private static final Gson GSON = new Gson();

    private static final UnknownIdCheck GONE = new UnknownIdCheck() {
        private boolean gone(String id) { return id.startsWith("gone:"); }
        @Override public boolean isUnknownItem(String id) { return gone(id); }
        @Override public boolean isUnknownMod(String modId) { return modId.equals("gone"); }
        @Override public boolean isUnknownEntity(String id) { return gone(id); }
        @Override public boolean isUnknownFluid(String id) { return gone(id); }
        @Override public boolean isUnknownEnchantment(String id) { return gone(id); }
        @Override public boolean isUnknownEffect(String id) { return gone(id); }
        @Override public boolean isUnknownRecipe(String id) { return gone(id); }
    };

    private static StageEntry stage(String json) {
        return GSON.fromJson(json, StageEntry.class);
    }

    @Test
    void unknownItemGoesKnownItemAndTagStay() {
        StageEntry entry = stage("""
                {"items": ["gone:a", "minecraft:stone"], "tags": ["gone:some_tag"]}
                """);

        UnknownEntryPruner.Result result = UnknownEntryPruner.prune(entry, GONE);

        assertEquals(List.of("minecraft:stone"), entry.getAllItemIds());
        assertEquals(List.of("gone:some_tag"), entry.getTags(), "a tag of a missing mod is empty, not unknown");
        assertEquals(1, result.removed());
        assertTrue(result.changed());
    }

    @Test
    void nbtAndOverridesStayOnTheirOwnEntry() {
        StageEntry entry = stage("""
                {"items": ["gone:a", {"id": "minecraft:stone", "nbt": {"CustomModelData": 3}, "name_text": "Rock"}]}
                """);

        UnknownEntryPruner.prune(entry, GONE);

        assertEquals(1, entry.getItemEntries().size());
        ItemEntry stone = entry.getItemEntries().get(0);
        assertEquals("minecraft:stone", stone.getId());
        assertTrue(stone.hasNbt());
        assertEquals("Rock", stone.getNameTextOverride());
    }

    @Test
    void missingModTakesItsLinkedEntriesAlong() {
        // The zombie and the bookshelf belong to an installed namespace on purpose: only the mod
        // link can take them out, so this fails if the cascade does not run.
        StageEntry entry = stage("""
                {"mods": ["gone", "minecraft"],
                 "mod_exceptions": ["gone:x"],
                 "entities": {"spawnlock": ["gone:zombie", "minecraft:pig"],
                              "modLinked": ["gone:zombie"]},
                 "structures": {"structures": ["gone:tower"], "mod_linked": ["gone:tower"]}}
                """);

        UnknownEntryPruner.Result result = UnknownEntryPruner.prune(entry, GONE);

        assertEquals(List.of("minecraft"), entry.getMods());
        assertTrue(entry.getModExceptionEntries().isEmpty());
        assertEquals(List.of("minecraft:pig"), entry.getEntities().getSpawnlockIds());
        assertTrue(entry.getEntities().getModLinked().isEmpty());
        assertTrue(entry.getStructures().isEmpty());
        assertTrue(entry.getStructureModLinked().isEmpty());
        // mod + exception + zombie + tower; the link bookkeeping is not counted.
        assertEquals(4, result.removed());
    }

    @Test
    void modLinkedEntryOfAnInstalledModIsNotTouched() {
        StageEntry entry = stage("""
                {"mods": ["gone"],
                 "entities": {"attacklock": ["othermod:wolf"], "modLinked": ["othermod:wolf"]}}
                """);

        UnknownEntryPruner.prune(entry, GONE);

        assertEquals(List.of("othermod:wolf"), entry.getEntities().getAttacklock());
        assertEquals(List.of("othermod:wolf"), entry.getEntities().getModLinked());
    }

    @Test
    void interactionFilterEmptiedByPruningDropsTheEntry() {
        // Kept, the entry would lock the villager for every held item instead of one.
        StageEntry entry = stage("""
                {"entities": {"interactionlock": [{"id": "minecraft:villager", "lock_items": ["gone:a"]}]}}
                """);

        UnknownEntryPruner.Result result = UnknownEntryPruner.prune(entry, GONE);

        assertTrue(entry.getEntities().getInteractionlock().isEmpty());
        assertEquals(2, result.removed(), "the filter item and the entry it emptied");
    }

    @Test
    void interactionFilterKeepsItsKnownItems() {
        StageEntry entry = stage("""
                {"entities": {"interactionlock": [{"id": "minecraft:villager", "lock_items": ["gone:a", "minecraft:stone"]}]}}
                """);

        UnknownEntryPruner.prune(entry, GONE);

        assertEquals(1, entry.getEntities().getInteractionlock().size());
        EntityInteractionLockEntry villager = entry.getEntities().getInteractionlock().get(0);
        assertNotNull(villager.getLockItems());
        assertEquals(List.of("minecraft:stone"), villager.getLockItems().stream().map(ItemEntry::getId).toList());
    }

    @Test
    void recipesOnlyGoWhenTheirModIsGone() {
        StageEntry entry = stage("""
                {"recipes": ["gone:r", "minecraft:r", "othermod:not_loaded_right_now"]}
                """);

        UnknownEntryPruner.prune(entry, GONE);

        assertEquals(List.of("minecraft:r", "othermod:not_loaded_right_now"), entry.getRecipes());
    }

    @Test
    void cleanStageReportsNoChange() {
        StageEntry entry = stage("""
                {"items": ["minecraft:stone"], "mods": ["minecraft"], "recipes": ["minecraft:r"]}
                """);

        UnknownEntryPruner.Result result = UnknownEntryPruner.prune(entry, GONE);

        assertFalse(result.changed());
        assertEquals(0, result.removed());
    }

    @Test
    void countLeavesTheStageAsItIs() {
        // The overview counts on the stages it shows; counting must not prune them.
        StageEntry entry = stage("""
                {"items": ["gone:a", "minecraft:stone"], "mods": ["gone"], "mod_exceptions": ["gone:x"]}
                """);

        assertEquals(3, UnknownEntryPruner.count(entry, GONE));
        assertEquals(List.of("gone:a", "minecraft:stone"), entry.getAllItemIds());
        assertEquals(List.of("gone"), entry.getMods());
        assertEquals(1, entry.getModExceptionEntries().size());
    }

    @Test
    void invalidIdsAreLeftAlone() {
        // Loading already reports and drops these; pruning must not guess about them.
        StageEntry entry = stage("""
                {"items": ["gone:Not Valid!"]}
                """);

        UnknownEntryPruner.prune(entry, GONE);

        assertEquals(List.of("gone:Not Valid!"), entry.getAllItemIds());
    }
}
