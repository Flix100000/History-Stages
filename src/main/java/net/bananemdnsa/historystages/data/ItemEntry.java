package net.bananemdnsa.historystages.data;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.display.TextOverrideHolder;

import java.util.ArrayList;
import java.util.List;

public class ItemEntry implements TextOverrideHolder {

    private final String id;
    private final NbtFilter nbt;
    private final List<String> lockActions; // null = all actions locked, empty = none locked

    // Per-item text overrides for the stage's hidden-display REPLACE mode.
    // null = no override → fall back to the stage default text.
    private final String nameTextOverride;
    private final String tooltipTextOverride;

    public ItemEntry(String id) {
        this(id, (JsonObject) null, null, null, null);
    }

    public ItemEntry(String id, JsonObject nbt) {
        this(id, nbt, null, null, null);
    }

    public ItemEntry(String id, JsonObject nbt, List<String> lockActions) {
        this(id, nbt, lockActions, null, null);
    }

    public ItemEntry(String id, JsonObject nbt, List<String> lockActions,
                     String nameTextOverride, String tooltipTextOverride) {
        this(id, NbtFilter.single(nbt), lockActions, nameTextOverride, tooltipTextOverride);
    }

    // Private so a caller writing `null` for nbt still reaches the public JsonObject constructor unambiguously.
    private ItemEntry(String id, NbtFilter nbt, List<String> lockActions,
                      String nameTextOverride, String tooltipTextOverride) {
        this.id = id;
        this.nbt = nbt != null ? nbt : NbtFilter.NONE;
        this.lockActions = lockActions != null ? new ArrayList<>(lockActions) : null;
        this.nameTextOverride = emptyToNull(nameTextOverride);
        this.tooltipTextOverride = emptyToNull(tooltipTextOverride);
    }

    private static String emptyToNull(String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }

    public String getId() { return id; }
    /** An entry whose NBT may hold several variants, any one of which matches. */
    public static ItemEntry withFilter(String id, NbtFilter nbt, List<String> lockActions,
                                       String nameTextOverride, String tooltipTextOverride) {
        return new ItemEntry(id, nbt, lockActions, nameTextOverride, tooltipTextOverride);
    }

    public NbtFilter getNbtFilter() { return nbt; }

    /**
     * The first NBT variant only. Kept for addons written before variants; anything that matches
     * stacks must use {@link #getNbtFilter()} or it ignores every other variant.
     */
    @Deprecated
    public JsonObject getNbt() { return nbt.first(); }

    public boolean hasNbt() { return !nbt.isEmpty(); }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    /** Whether the entry names an action list at all — an empty one still counts as narrowed. */
    public boolean hasLockActions() { return lockActions != null; }

    /** Per-item REPLACE name override, or null to use the stage default. */
    public String getNameTextOverride() { return nameTextOverride; }

    /** Per-item REPLACE tooltip override, or null to use the stage default. */
    public String getTooltipTextOverride() { return tooltipTextOverride; }

    public boolean hasNameTextOverride() { return nameTextOverride != null; }
    public boolean hasTooltipTextOverride() { return tooltipTextOverride != null; }

    public ItemEntry copy() {
        // NbtFilter is immutable, so sharing it is a copy.
        return withFilter(
                id,
                nbt,
                lockActions != null ? new ArrayList<>(lockActions) : null,
                nameTextOverride,
                tooltipTextOverride
        );
    }
}
