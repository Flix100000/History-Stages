package net.bananemdnsa.historystages.data.lock;

import com.google.gson.annotations.JsonAdapter;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityLocks {
    @JsonAdapter(EntitySpawnLockEntryListAdapter.class)
    private List<EntitySpawnLockEntry> spawnlock;
    @JsonAdapter(EntityAttackLockEntryListAdapter.class)
    private List<EntityAttackLockEntry> attacklock;
    @JsonAdapter(EntityInteractionLockEntryListAdapter.class)
    private List<EntityInteractionLockEntry> interactionlock;
    private List<String> modLinked;

    public EntityLocks() {
        this.spawnlock = new ArrayList<>();
        this.attacklock = new ArrayList<>();
        this.interactionlock = new ArrayList<>();
        this.modLinked = new ArrayList<>();
    }

    public List<EntitySpawnLockEntry> getSpawnlock() {
        return spawnlock != null ? spawnlock : new ArrayList<>();
    }

    /**
     * The attack-locked entity ids, as a live view: removing or replacing an id here changes the
     * entries underneath. Validation edits the list this way, which is why it is a view and not
     * a copy. Adding an id adds an entry without a filter.
     */
    public List<String> getAttacklock() {
        if (attacklock == null) attacklock = new ArrayList<>();
        List<EntityAttackLockEntry> backing = attacklock;
        return new AbstractList<>() {
            @Override public String get(int index) { return backing.get(index).getId(); }
            @Override public int size() { return backing.size(); }
            @Override public String set(int index, String id) {
                String old = backing.get(index).getId();
                backing.set(index, new EntityAttackLockEntry(id, backing.get(index).getNbt()));
                return old;
            }
            @Override public void add(int index, String id) {
                backing.add(index, new EntityAttackLockEntry(id));
            }
            @Override public String remove(int index) { return backing.remove(index).getId(); }
        };
    }

    /** The attack entries with their filters. */
    public List<EntityAttackLockEntry> getAttacklockEntries() {
        if (attacklock == null) attacklock = new ArrayList<>();
        return attacklock;
    }

    public List<EntityInteractionLockEntry> getInteractionlock() {
        return interactionlock != null ? interactionlock : new ArrayList<>();
    }

    /** Convenience: returns just the entity IDs in interactionlock (without action detail). */
    public List<String> getInteractionlockIds() {
        List<String> ids = new ArrayList<>(getInteractionlock().size());
        for (EntityInteractionLockEntry e : getInteractionlock()) ids.add(e.getId());
        return ids;
    }

    public List<String> getModLinked() {
        return modLinked != null ? modLinked : new ArrayList<>();
    }

    public void setSpawnlock(List<EntitySpawnLockEntry> spawnlock) {
        if (spawnlock == null) {
            this.spawnlock = new ArrayList<>();
            return;
        }
        List<EntitySpawnLockEntry> copy = new ArrayList<>(spawnlock.size());
        for (EntitySpawnLockEntry e : spawnlock) copy.add(e.copy());
        this.spawnlock = copy;
    }

    /**
     * Replaces the attack list by ids. An id that was already listed keeps its NBT filter, so
     * code that only knows ids (an addon writing the category, say) cannot wipe filters by
     * accident. To change filters, use {@link #setAttacklockEntries}.
     */
    public void setAttacklock(List<String> ids) {
        Map<String, EntityNbtFilter> filters = new HashMap<>();
        for (EntityAttackLockEntry e : getAttacklockEntries()) filters.putIfAbsent(e.getId(), e.getNbt());
        List<EntityAttackLockEntry> entries = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) entries.add(new EntityAttackLockEntry(id, filters.get(id)));
        }
        this.attacklock = entries;
    }

    public void setAttacklockEntries(List<EntityAttackLockEntry> entries) {
        List<EntityAttackLockEntry> copy = new ArrayList<>();
        if (entries != null) {
            for (EntityAttackLockEntry e : entries) copy.add(e.copy());
        }
        this.attacklock = copy;
    }

    public void setInteractionlock(List<EntityInteractionLockEntry> interactionlock) {
        if (interactionlock == null) {
            this.interactionlock = new ArrayList<>();
            return;
        }
        List<EntityInteractionLockEntry> copy = new ArrayList<>(interactionlock.size());
        for (EntityInteractionLockEntry e : interactionlock) copy.add(e.copy());
        this.interactionlock = copy;
    }

    public void setModLinked(List<String> modLinked) {
        this.modLinked = modLinked != null ? new ArrayList<>(modLinked) : new ArrayList<>();
    }

    /** Convenience: returns just the entity IDs in spawnlock (without source detail). */
    public List<String> getSpawnlockIds() {
        List<String> ids = new ArrayList<>(getSpawnlock().size());
        for (EntitySpawnLockEntry e : getSpawnlock()) ids.add(e.getId());
        return ids;
    }
}
