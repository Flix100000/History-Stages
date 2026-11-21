package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.NbtFilter;

public class DependencyItem {
    private String id;
    private int count;
    // Reflective Gson leaves this null when the field is absent; read it through getNbtFilter().
    private NbtFilter nbt;

    public DependencyItem() {
        this.count = 1;
    }

    public DependencyItem(String id, int count) {
        this.id = id;
        this.count = Math.max(1, count);
    }

    public DependencyItem(String id, int count, JsonObject nbt) {
        this(id, count, NbtFilter.single(nbt));
    }

    // Private so a caller writing `null` for nbt still reaches the public JsonObject constructor unambiguously.
    private DependencyItem(String id, int count, NbtFilter nbt) {
        this.id = id;
        this.count = Math.max(1, count);
        setNbtFilter(nbt);
    }

    /** A requirement whose NBT may hold several variants, any one of which is accepted. */
    public static DependencyItem withFilter(String id, int count, NbtFilter nbt) {
        return new DependencyItem(id, count, nbt);
    }

    public String getId() { return id; }
    public int getCount() { return count; }

    public NbtFilter getNbtFilter() { return nbt != null ? nbt : NbtFilter.NONE; }

    /**
     * The first NBT variant only. Kept for addons written before variants; anything that matches
     * stacks must use {@link #getNbtFilter()} or it ignores every other variant.
     */
    @Deprecated
    public JsonObject getNbt() { return getNbtFilter().first(); }

    public boolean hasNbt() { return !getNbtFilter().isEmpty(); }

    public void setId(String id) { this.id = id; }
    public void setCount(int count) { this.count = Math.max(1, count); }

    // NONE is stored as null so the field stays out of the written JSON.
    public void setNbtFilter(NbtFilter nbt) { this.nbt = nbt == null || nbt.isEmpty() ? null : nbt; }

    @Deprecated
    public void setNbt(JsonObject nbt) { setNbtFilter(NbtFilter.single(nbt)); }

    public DependencyItem copy() {
        // NbtFilter is immutable, so sharing it is a copy.
        return new DependencyItem(id, count, getNbtFilter());
    }
}
