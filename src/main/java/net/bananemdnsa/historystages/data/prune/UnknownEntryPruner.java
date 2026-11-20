package net.bananemdnsa.historystages.data.prune;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.lock.EntityAttackLockEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Takes the entries of uninstalled mods out of a stage (#142).
 *
 * <p>Works on the entry it is given; callers that need the original pass a {@link StageEntry#copy}.
 * Every entry carries its own NBT, lock actions and overrides in the stored form, so dropping a
 * list element cannot shift that data onto a neighbour — unlike the editor's index-keyed buffers.
 *
 * <p>Left alone on purpose: tags (a tag of a missing mod is empty, not unknown), dependencies
 * (dropping a requirement makes a stage easier), the icon, zones, dimensions and the
 * data-driven structure and biome lists except where a removed mod entry links them.
 */
public final class UnknownEntryPruner {

    public record Result(int removed, boolean changed) {}

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private UnknownEntryPruner() {}

    /**
     * How many entries {@link #prune} would take out, leaving {@code entry} as it is. Works on a
     * copy through JSON, the form a stage is saved in; {@link StageEntry#copy} shares lists.
     */
    public static int count(StageEntry entry, UnknownIdCheck check) {
        return prune(detached(entry), check).removed();
    }

    /** A copy that shares nothing with {@code entry}, safe to prune and to save. */
    public static StageEntry detached(StageEntry entry) {
        return GSON.fromJson(entry.toCompactJson(), StageEntry.class);
    }

    public static Result prune(StageEntry entry, UnknownIdCheck check) {
        Counter count = new Counter();

        // Mods first: the cascade below must see the links before anything else edits them.
        List<String> goneMods = new ArrayList<>();
        replace(entry.getModEntries(), keep(entry.getModEntries(), NamedLockEntry::getId,
                id -> UnknownIdCheck.isValidModId(id) && check.isUnknownMod(id), count, goneMods), entry::setModEntries);
        for (String mod : goneMods) dropModLinked(entry, mod + ":", count);

        replace(entry.getItemEntries(), keep(entry.getItemEntries(), ItemEntry::getId,
                unknown(check::isUnknownItem), count, null), entry::setItemEntries);
        replace(entry.getModExceptionEntries(), keep(entry.getModExceptionEntries(), ItemEntry::getId,
                unknown(check::isUnknownItem), count, null), entry::setModExceptionEntries);
        replace(entry.getFluidEntries(), keep(entry.getFluidEntries(), e -> e.getId(),
                unknown(check::isUnknownFluid), count, null), entry::setFluidEntries);
        replace(entry.getEnchantmentEntries(), keep(entry.getEnchantmentEntries(), e -> e.getId(),
                unknown(check::isUnknownEnchantment), count, null), entry::setEnchantmentEntries);
        replace(entry.getEffectEntries(), keep(entry.getEffectEntries(), e -> e.getId(),
                unknown(check::isUnknownEffect), count, null), entry::setEffectEntries);
        replace(entry.getRecipes(), keep(entry.getRecipes(), Function.identity(),
                unknown(check::isUnknownRecipe), count, null), entry::setRecipes);
        // Tags fail isValidId inside unknown(), so "#minecraft:crops" is never taken for a block.
        replace(entry.getCrops(), keep(entry.getCrops(), Function.identity(),
                unknown(check::isUnknownBlock), count, null), entry::setCrops);
        replace(entry.getTradeOffers(), keep(entry.getTradeOffers(), TradeOfferEntry::givesId,
                unknown(check::isUnknownItem), count, null), entry::setTradeOffers);

        pruneEntities(entry, check, count);
        return new Result(count.removed, count.removed > 0);
    }

    private static void pruneEntities(StageEntry entry, UnknownIdCheck check, Counter count) {
        EntityLocks locks = entry.getEntities();
        Predicate<String> gone = unknown(check::isUnknownEntity);
        int before = count.removed;

        List<EntitySpawnLockEntry> spawn = keep(locks.getSpawnlock(), EntitySpawnLockEntry::getId, gone, count, null);
        List<EntityAttackLockEntry> attack = keep(locks.getAttacklockEntries(), EntityAttackLockEntry::getId,
                gone, count, null);

        List<EntityInteractionLockEntry> interaction = new ArrayList<>();
        for (EntityInteractionLockEntry e : locks.getInteractionlock()) {
            if (gone.test(e.getId())) { count.removed++; continue; }
            if (!e.hasLockItems()) { interaction.add(e); continue; }
            List<ItemEntry> items = keep(e.getLockItems(), ItemEntry::getId, unknown(check::isUnknownItem), count, null);
            if (items.size() == e.getLockItems().size()) { interaction.add(e); continue; }
            // An empty filter means "any held item", which would lock more than before.
            if (items.isEmpty()) { count.removed++; continue; }
            interaction.add(new EntityInteractionLockEntry(e.getId(), e.getLockActions(), items, e.getNbt()));
        }

        if (count.removed == before) return;
        locks.setSpawnlock(spawn);
        locks.setAttacklockEntries(attack);
        locks.setInteractionlock(interaction);
        entry.setEntities(locks);
    }

    /**
     * What removing a mod entry by hand takes along in the editor: mod-linked creatures,
     * structures, biomes and plants with that prefix, the structure generation rules for them, and the
     * mod's exceptions. Entries the author added one by one are not mod-linked and stay unless
     * they are unknown themselves.
     */
    private static void dropModLinked(StageEntry entry, String prefix, Counter count) {
        EntityLocks locks = entry.getEntities();
        Set<String> linked = linkedWith(locks.getModLinked(), prefix);
        if (!linked.isEmpty()) {
            locks.setSpawnlock(keep(locks.getSpawnlock(), EntitySpawnLockEntry::getId, linked::contains, count, null));
            locks.setAttacklockEntries(keep(locks.getAttacklockEntries(), EntityAttackLockEntry::getId,
                    linked::contains, count, null));
            locks.setInteractionlock(keep(locks.getInteractionlock(), EntityInteractionLockEntry::getId,
                    linked::contains, count, null));
            locks.setModLinked(without(locks.getModLinked(), prefix));
            entry.setEntities(locks);
        }

        Set<String> structures = linkedWith(entry.getStructureModLinked(), prefix);
        if (!structures.isEmpty()) {
            entry.setStructures(keep(entry.getStructures(), Function.identity(), structures::contains, count, null));
            entry.setStructureModLinked(without(entry.getStructureModLinked(), prefix));
            entry.setStructureGenerationRules(entry.getStructureGenerationRules().stream()
                    .filter(r -> !r.id().startsWith(prefix)).collect(Collectors.toCollection(ArrayList::new)));
        }

        Set<String> biomes = linkedWith(entry.getBiomeModLinked(), prefix);
        if (!biomes.isEmpty()) {
            entry.setBiomes(keep(entry.getBiomes(), Function.identity(), biomes::contains, count, null));
            entry.setBiomeModLinked(without(entry.getBiomeModLinked(), prefix));
        }

        Set<String> crops = linkedWith(entry.getCropModLinked(), prefix);
        if (!crops.isEmpty()) {
            entry.setCrops(keep(entry.getCrops(), Function.identity(), crops::contains, count, null));
            entry.setCropModLinked(without(entry.getCropModLinked(), prefix));
        }

        replace(entry.getModExceptionEntries(), keep(entry.getModExceptionEntries(), ItemEntry::getId,
                id -> id.startsWith(prefix), count, null), entry::setModExceptionEntries);
    }

    /**
     * Writes a list back only when something was dropped. Setting an unchanged list would turn an
     * absent field into an empty one and add {@code "fluids": []} and the like to the file.
     */
    private static <T> void replace(List<T> before, List<T> after, java.util.function.Consumer<List<T>> setter) {
        if (after.size() != before.size()) setter.accept(after);
    }

    private static Predicate<String> unknown(Predicate<String> check) {
        return id -> UnknownIdCheck.isValidId(id) && check.test(id);
    }

    /** The elements that do not match {@code drop}; counts the dropped ones and collects their ids. */
    private static <T> List<T> keep(List<T> list, Function<T, String> id, Predicate<String> drop,
                                    Counter count, List<String> droppedIds) {
        List<T> kept = new ArrayList<>(list.size());
        for (T element : list) {
            String key = id.apply(element);
            if (key != null && drop.test(key)) {
                count.removed++;
                if (droppedIds != null) droppedIds.add(key);
            } else {
                kept.add(element);
            }
        }
        return kept;
    }

    private static Set<String> linkedWith(List<String> modLinked, String prefix) {
        return modLinked.stream().filter(id -> id.startsWith(prefix)).collect(Collectors.toSet());
    }

    private static List<String> without(List<String> ids, String prefix) {
        return ids.stream().filter(id -> !id.startsWith(prefix)).collect(Collectors.toCollection(ArrayList::new));
    }

    private static final class Counter {
        int removed;
    }
}
