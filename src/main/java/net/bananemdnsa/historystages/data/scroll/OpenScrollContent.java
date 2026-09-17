package net.bananemdnsa.historystages.data.scroll;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link StageEntry} into the {@link OpenScrollDocument} the open scroll screen draws.
 *
 * <p>The only thing this needs from Minecraft is "which items are in this tag", and that arrives
 * through {@link TagResolver}. Everything else — grouping, deduplicating, counting — is plain data
 * work and is unit-tested without a running game.
 */
public final class OpenScrollContent {

    private OpenScrollContent() {}

    public static final String DIMENSIONS_KEY = "gui.historystages.open_scroll.world.dimensions";
    public static final String STRUCTURES_KEY = "gui.historystages.open_scroll.world.structures";
    public static final String BIOMES_KEY = "gui.historystages.open_scroll.world.biomes";
    /**
     * Fluids sit in the world group rather than beside the items, because the group renders a
     * plain id list and the item list renders icons — a fluid id has no item icon to draw.
     */
    public static final String FLUIDS_KEY = "gui.historystages.open_scroll.world.fluids";
    public static final String ZONES_KEY = "gui.historystages.open_scroll.world.zones";
    /**
     * Trades get three groups rather than one. Their rows are short on purpose — a profession, an
     * item, a level name — and under a single heading nobody could tell which of the three a
     * "Librarian" or a "Journeyman" row is.
     */
    public static final String TRADE_PROFESSIONS_KEY = "gui.historystages.open_scroll.world.trade_professions";
    public static final String TRADE_OFFERS_KEY = "gui.historystages.open_scroll.world.trade_offers";
    public static final String TRADE_LEVELS_KEY = "gui.historystages.open_scroll.world.trade_levels";

    /** The item ids behind an item tag. The client implementation asks the item registry. */
    public interface TagResolver {
        List<String> itemsInTag(String tagId);
    }

    public static OpenScrollDocument build(String stageId, boolean individual, StageEntry entry,
                                           TagResolver tags) {
        return build(stageId, individual, entry, tags, "");
    }

    public static OpenScrollDocument build(String stageId, boolean individual, StageEntry entry,
                                           TagResolver tags, String description) {
        return new OpenScrollDocument(stageId, individual,
                entry.getIcon(),
                entry.getDisplayName() == null ? "" : entry.getDisplayName(),
                description == null ? "" : description,
                items(entry, tags), creatures(entry.getEntities()), world(entry));
    }

    /** A document for a scroll whose stage no longer exists, so the screen can say so. */
    public static OpenScrollDocument unknown(String stageId) {
        return new OpenScrollDocument(stageId, false, "", "", "", List.of(), List.of(), List.of());
    }

    /** Direct items first, then the ones behind the tags, each in config order, no repeats. */
    private static List<String> items(StageEntry entry, TagResolver tags) {
        LinkedHashSet<String> out = new LinkedHashSet<>(entry.getAllItemIds());
        for (String tag : entry.getNbtFreeTags()) {
            out.addAll(tags.itemsInTag(tag));
        }
        return new ArrayList<>(out);
    }

    /** All three lock kinds in one list; an entity in several of them keeps every marker. */
    private static List<OpenScrollEntry> creatures(EntityLocks locks) {
        Map<String, EnumSet<OpenScrollMarker>> byId = new LinkedHashMap<>();
        for (EntitySpawnLockEntry spawn : locks.getSpawnlock()) {
            if (spawn.getPhase() == GenerationPhase.AFTER_UNLOCK) continue;
            byId.computeIfAbsent(spawn.getId(), k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.SPAWN);
        }
        for (String id : locks.getAttacklock()) {
            byId.computeIfAbsent(id, k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.ATTACK);
        }
        for (String id : locks.getInteractionlockIds()) {
            byId.computeIfAbsent(id, k -> EnumSet.noneOf(OpenScrollMarker.class)).add(OpenScrollMarker.INTERACTION);
        }
        List<OpenScrollEntry> out = new ArrayList<>(byId.size());
        byId.forEach((id, markers) -> out.add(new OpenScrollEntry(id, markers)));
        return out;
    }

    /** Places first, then fluids, then trades — a group with nothing in it gets no heading. */
    private static List<OpenScrollWorldGroup> world(StageEntry entry) {
        List<OpenScrollWorldGroup> out = new ArrayList<>(8);
        addGroup(out, DIMENSIONS_KEY, entry.getDimensions());
        addGroup(out, STRUCTURES_KEY, entry.getStructures());
        addGroup(out, BIOMES_KEY, entry.getBiomes());
        addGroup(out, ZONES_KEY, zoneNames(entry));
        addGroup(out, FLUIDS_KEY, entry.getAllFluidIds());
        addGroup(out, TRADE_PROFESSIONS_KEY, entry.getTradeProfessionEntries().stream()
                .map(OpenScrollContent::professionRow).toList());
        // identity() leaves the criterion out, so two entries for the same trade that differ only
        // in which enchanted book they catch collapse into the one row a reader would expect.
        addGroup(out, TRADE_OFFERS_KEY, entry.getTradeOffers().stream()
                .map(TradeOfferEntry::identity).distinct().toList());
        addGroup(out, TRADE_LEVELS_KEY, entry.getTradeLevels());
        return out;
    }

    /**
     * The same name in two dimensions is two zones to the pack but one place to a reader, and a
     * zone with no name has nothing to show.
     */
    private static List<String> zoneNames(StageEntry entry) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ZoneEntry zone : entry.getZones()) {
            if (!zone.getName().isBlank()) names.add(zone.getName());
        }
        return new ArrayList<>(names);
    }

    /**
     * A profession row carries its level narrowing along, because a world group is a list of
     * strings: the id, then the gated levels after a space. No id contains a space.
     */
    public static String professionRow(TradeProfessionEntry profession) {
        return profession.hasLevels()
                ? profession.getId() + " " + String.join(",", profession.getLevels())
                : profession.getId();
    }

    public static String professionId(String row) {
        int space = row.indexOf(' ');
        return space < 0 ? row : row.substring(0, space);
    }

    /** The gated levels, or an empty list when the row gates every level. */
    public static List<String> professionLevels(String row) {
        int space = row.indexOf(' ');
        if (space < 0 || space == row.length() - 1) return List.of();
        return List.of(row.substring(space + 1).split(","));
    }

    private static void addGroup(List<OpenScrollWorldGroup> out, String key, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        out.add(new OpenScrollWorldGroup(key, ids));
    }
}
