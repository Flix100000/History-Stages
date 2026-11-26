package net.bananemdnsa.historystages.data.lock;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.NbtFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * A named entry (tag or mod ID) with an optional list of locked actions.
 * When lockActions is null, all actions are locked (default behaviour).
 * When lockActions is an empty list, no actions are locked.
 *
 * In JSON the field is written as {@code unlock_actions} (the complement — actions that are
 * NOT locked). {@code null} / no field means all actions are locked; an empty list is therefore
 * written as every action being unlocked.
 *
 * <p>Empty and null are kept apart on purpose. Folding them together — which this did until
 * Issue #117 — makes an entry the maintainer cleared every tick from read back as one that locks
 * everything, the exact opposite of what the editor showed.
 */
public class NamedLockEntry implements net.bananemdnsa.historystages.data.display.TextOverrideHolder {

    /**
     * Canonical ordered list of the actions a named entry recognises.
     *
     * <p>Kept as a field rather than replaced at the call sites: the Gson adapters read it on
     * the save path, and addon code has always named it here.
     */
    public static final List<String> ALL_ACTIONS =
            net.bananemdnsa.historystages.api.lock.LockActions.ITEM;

    private final String id;
    private final List<String> lockActions; // null = all actions locked, empty = none locked
    private final NbtFilter nbt; // optional component/NBT variants; NONE = none (tags only)

    // Per-entry REPLACE text overrides for the stage's hidden-display config.
    // null = no override → fall back to the stage default text.
    private final String nameTextOverride;
    private final String tooltipTextOverride;

    // Lazily computed — only meaningful when this entry represents an item tag.
    private transient TagKey<Item> cachedTagKey;

    public NamedLockEntry(String id) {
        this(id, null, null, null);
    }

    public NamedLockEntry(String id, List<String> lockActions) {
        this(id, lockActions, null, null);
    }

    public NamedLockEntry(String id, List<String> lockActions, String nameTextOverride, String tooltipTextOverride) {
        this(id, lockActions, nameTextOverride, tooltipTextOverride, (JsonObject) null);
    }

    public NamedLockEntry(String id, List<String> lockActions, String nameTextOverride, String tooltipTextOverride, JsonObject nbt) {
        this(id, lockActions, nameTextOverride, tooltipTextOverride, NbtFilter.single(nbt));
    }

    // Private so a caller writing `null` for nbt still reaches the public JsonObject constructor unambiguously.
    private NamedLockEntry(String id, List<String> lockActions, String nameTextOverride, String tooltipTextOverride,
                           NbtFilter nbt) {
        this.id = id;
        this.lockActions = lockActions != null ? new ArrayList<>(lockActions) : null;
        this.nameTextOverride = (nameTextOverride != null && !nameTextOverride.isEmpty()) ? nameTextOverride : null;
        this.tooltipTextOverride = (tooltipTextOverride != null && !tooltipTextOverride.isEmpty()) ? tooltipTextOverride : null;
        this.nbt = nbt != null ? nbt : NbtFilter.NONE;
    }

    public String getId() { return id; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    /** Whether the entry names an action list at all — an empty one still counts as narrowed. */
    public boolean hasLockActions() { return lockActions != null; }

    /** An entry whose NBT may hold several variants, any one of which matches. */
    public static NamedLockEntry withFilter(String id, List<String> lockActions, String nameTextOverride,
                                            String tooltipTextOverride, NbtFilter nbt) {
        return new NamedLockEntry(id, lockActions, nameTextOverride, tooltipTextOverride, nbt);
    }

    public NbtFilter getNbtFilter() { return nbt; }

    /**
     * The first NBT variant only. Kept for addons written before variants; anything that matches
     * stacks must use {@link #getNbtFilter()} or it ignores every other variant.
     */
    @Deprecated
    public JsonObject getNbt() { return nbt.first(); }

    public boolean hasNbt() { return !nbt.isEmpty(); }

    @Override
    public String getNameTextOverride() { return nameTextOverride; }
    @Override
    public String getTooltipTextOverride() { return tooltipTextOverride; }

    public boolean hasNameTextOverride() { return nameTextOverride != null; }
    public boolean hasTooltipTextOverride() { return tooltipTextOverride != null; }

    /** Returns a cached TagKey for this entry's ID. Only call when this entry represents an item tag. */
    public TagKey<Item> getItemTagKey() {
        if (cachedTagKey == null) {
            cachedTagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
        }
        return cachedTagKey;
    }

    public NamedLockEntry copy() {
        return withFilter(id, lockActions != null ? new ArrayList<>(lockActions) : null,
                nameTextOverride, tooltipTextOverride, nbt);
    }
}
