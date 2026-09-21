package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;

/**
 * The editor state behind the three entity tabs, which cannot be split cleanly because the data
 * cannot: attack, spawn and interaction locks all live in one {@code EntityLocks} object, they
 * share the list of entries a mod contributed, and writing any of them means writing all three.
 *
 * <p>Unlike the item-style tabs, the extras here hang off the <em>entity id</em> rather than the
 * row position, so removing a row is a plain map removal with no renumbering.
 */
public final class EntityTabsState {

    private final List<String> attacklock = new ArrayList<>();

    private final List<String> spawnlock = new ArrayList<>();
    /** Only entries that differ from a plain lock; a missing id means "block every spawn". */
    private final Map<String, EntitySpawnLockEntry> spawnRules = new HashMap<>();

    private final List<String> interactionlock = new ArrayList<>();
    private final Map<String, List<String>> interactionActions = new HashMap<>();
    private final Map<String, List<ItemEntry>> interactionItems = new HashMap<>();

    private final List<String> modLinked = new ArrayList<>();

    public List<String> attacklock() {
        return attacklock;
    }

    public List<String> spawnlock() {
        return spawnlock;
    }

    public Map<String, EntitySpawnLockEntry> spawnRules() {
        return spawnRules;
    }

    public List<String> interactionlock() {
        return interactionlock;
    }

    public Map<String, List<String>> interactionActions() {
        return interactionActions;
    }

    public Map<String, List<ItemEntry>> interactionItems() {
        return interactionItems;
    }

    /** Entries contributed by a mod selection, across all three lists — the data keeps one list. */
    public List<String> modLinked() {
        return modLinked;
    }

    public void load(StageEntry stage) {
        EntityLocks locks = stage.getEntities();

        attacklock.clear();
        attacklock.addAll(locks.getAttacklock());

        spawnlock.clear();
        spawnRules.clear();
        for (EntitySpawnLockEntry entry : locks.getSpawnlock()) {
            spawnlock.add(entry.getId());
            if (!entry.equals(new EntitySpawnLockEntry(entry.getId()))) spawnRules.put(entry.getId(), entry);
        }

        interactionlock.clear();
        interactionActions.clear();
        interactionItems.clear();
        for (EntityInteractionLockEntry entry : locks.getInteractionlock()) {
            interactionlock.add(entry.getId());
            if (entry.getLockActions() != null) {
                interactionActions.put(entry.getId(), new ArrayList<>(entry.getLockActions()));
            }
            if (entry.getLockItems() != null && !entry.getLockItems().isEmpty()) {
                // Deep copy: these are edited in place by the item-filter popup, and the stage we
                // loaded from must not change underneath us.
                List<ItemEntry> filters = new ArrayList<>(entry.getLockItems().size());
                for (ItemEntry filter : entry.getLockItems()) filters.add(filter.copy());
                interactionItems.put(entry.getId(), filters);
            }
        }

        modLinked.clear();
        modLinked.addAll(locks.getModLinked());
    }

    /**
     * Writes all three lists at once. Each entity tab calls this, which is deliberate: they share
     * one storage object, so a partial write would drop the other two.
     */
    public void store(StageEntry stage) {
        EntityLocks locks = new EntityLocks();
        locks.setAttacklock(new ArrayList<>(attacklock));

        List<EntityInteractionLockEntry> interactionEntries = new ArrayList<>();
        for (String entityId : interactionlock) {
            interactionEntries.add(new EntityInteractionLockEntry(
                    entityId, interactionActions.get(entityId), interactionItems.get(entityId)));
        }
        locks.setInteractionlock(interactionEntries);

        List<EntitySpawnLockEntry> spawnEntries = new ArrayList<>();
        for (String entityId : spawnlock) {
            spawnEntries.add(spawnRules.getOrDefault(entityId, new EntitySpawnLockEntry(entityId)));
        }
        locks.setSpawnlock(spawnEntries);

        locks.setModLinked(new ArrayList<>(modLinked));
        stage.setEntities(locks);
    }

    /** Drops one entity from a list together with whatever extras hang off its id. */
    public void removeFrom(List<String> rows, int index) {
        if (index < 0 || index >= rows.size()) return;
        String entityId = rows.remove(index);
        if (rows == spawnlock) {
            spawnRules.remove(entityId);
        } else if (rows == interactionlock) {
            interactionActions.remove(entityId);
            interactionItems.remove(entityId);
        }
    }
}
