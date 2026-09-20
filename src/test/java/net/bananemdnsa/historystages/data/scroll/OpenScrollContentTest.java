package net.bananemdnsa.historystages.data.scroll;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScrollContentTest {

    /** Stand-in for the item registry: no Minecraft needed to test grouping and dedupe. */
    private static OpenScrollContent.TagResolver tags(Map<String, List<String>> contents) {
        return tagId -> contents.getOrDefault(tagId, List.of());
    }

    private static StageEntry stage() {
        StageEntry entry = new StageEntry();
        entry.setDisplayName("Bronze Age");
        entry.setIcon("minecraft:copper_ingot");
        return entry;
    }

    @Test
    void directItemsKeepTheirConfigOrder() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:iron_sword", "minecraft:anvil"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of("minecraft:iron_sword", "minecraft:anvil"), doc.itemIds());
    }

    @Test
    void tagItemsFollowTheDirectOnes() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:anvil"));
        entry.setTags(List.of("c:ingots"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry,
                tags(Map.of("c:ingots", List.of("minecraft:iron_ingot", "minecraft:gold_ingot"))));

        assertEquals(List.of("minecraft:anvil", "minecraft:iron_ingot", "minecraft:gold_ingot"),
                doc.itemIds());
    }

    @Test
    void anItemListedDirectlyAndViaATagAppearsOnce() {
        StageEntry entry = stage();
        entry.setItems(List.of("minecraft:iron_ingot"));
        entry.setTags(List.of("c:ingots"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry,
                tags(Map.of("c:ingots", List.of("minecraft:iron_ingot", "minecraft:gold_ingot"))));

        assertEquals(List.of("minecraft:iron_ingot", "minecraft:gold_ingot"), doc.itemIds());
    }

    @Test
    void everyEntityLockKindLandsInOneChapter() {
        StageEntry entry = stage();
        EntityLocks locks = new EntityLocks();
        locks.setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        locks.setAttacklock(List.of("minecraft:creeper"));
        locks.setInteractionlock(List.of(new EntityInteractionLockEntry("minecraft:villager")));
        entry.setEntities(locks);

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of("minecraft:zombie", "minecraft:creeper", "minecraft:villager"),
                doc.creatures().stream().map(OpenScrollEntry::id).toList());
    }

    @Test
    void anEntityInSeveralLockListsAppearsOnceWithEveryMarker() {
        StageEntry entry = stage();
        EntityLocks locks = new EntityLocks();
        locks.setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie")));
        locks.setAttacklock(List.of("minecraft:zombie"));
        entry.setEntities(locks);

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(1, doc.creatures().size());
        assertEquals(java.util.Set.of(OpenScrollMarker.SPAWN, OpenScrollMarker.ATTACK),
                doc.creatures().get(0).markers());
    }

    @Test
    void anAfterUnlockSpawnRuleIsNoLockAndGetsNoMarker() {
        StageEntry entry = stage();
        EntityLocks locks = new EntityLocks();
        locks.setSpawnlock(List.of(new EntitySpawnLockEntry("minecraft:zombie", null,
                GenerationPhase.AFTER_UNLOCK, null, null)));
        entry.setEntities(locks);

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertTrue(doc.creatures().isEmpty());
    }

    @Test
    void worldGroupsKeepTheirLabelsAndDropEmptyOnes() {
        StageEntry entry = stage();
        entry.setDimensions(List.of("minecraft:the_nether"));
        entry.setBiomes(List.of("minecraft:crimson_forest"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(2, doc.world().size(), "an empty structures group must not take a heading");
        assertEquals(OpenScrollContent.DIMENSIONS_KEY, doc.world().get(0).labelKey());
        assertEquals(OpenScrollContent.BIOMES_KEY, doc.world().get(1).labelKey());
        assertEquals(2, doc.worldCount());
    }

    @Test
    void zonesAndTradesLandInTheWorldChapterAfterThePlaces() {
        StageEntry entry = stage();
        entry.setBiomes(List.of("minecraft:crimson_forest"));
        entry.setZones(List.of(zone("Old Village", "minecraft:overworld")));
        entry.setTradeProfessionEntries(List.of(new TradeProfessionEntry("minecraft:librarian")));
        entry.setTradeOffers(List.of(new TradeOfferEntry("minecraft:cartographer", 2,
                "minecraft:filled_map", "minecraft:emerald", "minecraft:compass")));
        entry.setTradeLevels(List.of("5"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of(OpenScrollContent.BIOMES_KEY, OpenScrollContent.ZONES_KEY,
                        OpenScrollContent.TRADE_PROFESSIONS_KEY, OpenScrollContent.TRADE_OFFERS_KEY,
                        OpenScrollContent.TRADE_LEVELS_KEY),
                doc.world().stream().map(OpenScrollWorldGroup::labelKey).toList());
        assertEquals(5, doc.worldCount());
    }

    @Test
    void aStageThatOnlyGatesTradesIsNotAnEmptyScroll() {
        StageEntry entry = stage();
        entry.setTradeLevels(List.of("3"));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertFalse(doc.isEmpty(OpenScrollChapter.WORLD));
    }

    @Test
    void aZoneNameInTwoDimensionsIsOneRowAndAnUnnamedZoneNone() {
        StageEntry entry = stage();
        entry.setZones(List.of(zone("Fortress", "minecraft:overworld"),
                zone("Fortress", "minecraft:the_nether"), zone("", "minecraft:overworld")));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(List.of("Fortress"), doc.world().get(0).ids());
    }

    @Test
    void theSameOfferNarrowedTwiceIsOneRow() {
        StageEntry entry = stage();
        com.google.gson.JsonObject mending = new com.google.gson.JsonObject();
        mending.addProperty("enchantment", "minecraft:mending");
        entry.setTradeOffers(List.of(
                new TradeOfferEntry("minecraft:librarian", 1, "minecraft:enchanted_book",
                        "minecraft:emerald", "minecraft:book"),
                new TradeOfferEntry("minecraft:librarian", 1, "minecraft:enchanted_book",
                        "minecraft:emerald", "minecraft:book", mending)));

        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, entry, tags(Map.of()));

        assertEquals(1, doc.worldCount());
    }

    @Test
    void aProfessionRowKeepsItsLevelNarrowing() {
        String narrowed = OpenScrollContent.professionRow(
                new TradeProfessionEntry("minecraft:librarian", List.of("4", "5")));
        String every = OpenScrollContent.professionRow(new TradeProfessionEntry("minecraft:librarian"));

        assertEquals("minecraft:librarian", OpenScrollContent.professionId(narrowed));
        assertEquals(List.of("4", "5"), OpenScrollContent.professionLevels(narrowed));
        assertEquals("minecraft:librarian", OpenScrollContent.professionId(every));
        assertTrue(OpenScrollContent.professionLevels(every).isEmpty());
    }

    private static ZoneEntry zone(String name, String dimension) {
        ZoneEntry zone = new ZoneEntry();
        zone.setName(name);
        zone.setDimension(dimension);
        return zone;
    }

    @Test
    void theOverviewCarriesIconNameAndDescription() {
        OpenScrollDocument doc = OpenScrollContent.build("bronze", true, stage(), tags(Map.of()),
                "Where copper met tin.");

        assertEquals("minecraft:copper_ingot", doc.iconId());
        assertEquals("Bronze Age", doc.displayName());
        assertEquals("Where copper met tin.", doc.description());
        assertTrue(doc.individual());
    }

    @Test
    void aStageThatLocksNothingYieldsAnEmptyButUsableDocument() {
        OpenScrollDocument doc = OpenScrollContent.build("bronze", false, stage(), tags(Map.of()));

        assertTrue(doc.isEmpty(OpenScrollChapter.ITEMS));
        assertTrue(doc.isEmpty(OpenScrollChapter.CREATURES));
        assertTrue(doc.isEmpty(OpenScrollChapter.WORLD));
        assertFalse(doc.isEmpty(OpenScrollChapter.OVERVIEW), "the overview always has something to say");
    }

    @Test
    void aMissingStageStillProducesADocumentSoTheScreenCanExplainItself() {
        OpenScrollDocument doc = OpenScrollContent.unknown("ghost");

        assertEquals("ghost", doc.stageId());
        assertTrue(doc.itemIds().isEmpty());
        assertTrue(doc.displayName().isEmpty());
    }
}
