package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.NbtFilter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * An attack-locked entity, optionally narrowed by an NBT filter. In JSON a plain string when there
 * is no filter, otherwise {@code { "id": "…", "nbt": {…} }}.
 */
public final class EntityAttackLockEntry {

    private final String id;
    private final NbtFilter nbt;

    public EntityAttackLockEntry(String id) {
        this(id, NbtFilter.NONE);
    }

    public EntityAttackLockEntry(String id, @Nullable NbtFilter nbt) {
        this.id = id;
        this.nbt = nbt != null ? nbt : NbtFilter.NONE;
    }

    public String getId() { return id; }

    public NbtFilter getNbt() { return nbt; }

    public boolean hasNbt() { return !nbt.isEmpty(); }

    public boolean matches(String entityId, @Nullable Predicate<com.google.gson.JsonObject> entityMatches) {
        return id.equals(entityId) && nbt.matches(entityMatches);
    }

    /** The filter is immutable, so a copy only needs a new holder. */
    public EntityAttackLockEntry copy() {
        return new EntityAttackLockEntry(id, nbt);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityAttackLockEntry other && id.equals(other.id) && nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + nbt.hashCode();
    }
}
