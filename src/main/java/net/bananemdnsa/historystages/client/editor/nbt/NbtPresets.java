package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.List;

/**
 * The lookup tables the editor needs on top of the item's raw NBT.
 *
 * <p>On 1.20.1 an item's tag is the data itself, so a property is named by its path inside that tag
 * ({@code RepairCost}, {@code display.Name}) rather than by a component id. {@link #common()} is the
 * picker's first tab — the handful of properties people reach for by name — and
 * {@link #knownProperties()} the longer list behind it.
 */
public final class NbtPresets {

    /**
     * @param nameKey        lang key for the friendly label
     * @param descriptionKey lang key for the line under it
     * @param componentId    the NBT path this writes to
     * @param valueKind      how its value is edited, and therefore what JSON gets written
     * @param defaultValue   pre-filled JSON, "" for none
     */
    public record Preset(String nameKey, String descriptionKey, String componentId,
                         ValueKind valueKind, String defaultValue) {}

    public static final String LORE_COMPONENT = "display.Lore";

    private static final List<Preset> COMMON = List.of(
            new Preset("editor.historystages.nbt.preset.custom_name",
                    "editor.historystages.nbt.preset.custom_name.desc",
                    "display.Name", ValueKind.TEXT, ""),
            new Preset("editor.historystages.nbt.preset.lore",
                    "editor.historystages.nbt.preset.lore.desc",
                    LORE_COMPONENT, ValueKind.TEXT_LIST, ""),
            // Stored as a byte; NbtMatcher reads a JSON boolean against a ByteTag.
            new Preset("editor.historystages.nbt.preset.unbreakable",
                    "editor.historystages.nbt.preset.unbreakable.desc",
                    "Unbreakable", ValueKind.PRESENCE, "true"),
            new Preset("editor.historystages.nbt.preset.potion",
                    "editor.historystages.nbt.preset.potion.desc",
                    "Potion", ValueKind.TEXT, ""),
            new Preset("editor.historystages.nbt.preset.custom_model_data",
                    "editor.historystages.nbt.preset.custom_model_data.desc",
                    "CustomModelData", ValueKind.NUMBER, ""),
            new Preset("editor.historystages.nbt.preset.repair_cost",
                    "editor.historystages.nbt.preset.repair_cost.desc",
                    "RepairCost", ValueKind.NUMBER, ""));

    /**
     * Vanilla properties worth offering by name. Not exhaustive and not meant to be: a mod's own
     * keys come in through "take from item" or as a custom key.
     */
    private static final List<String> KNOWN = List.of(
            "display.Name", "display.Lore", "display.color", "Unbreakable", "Damage", "RepairCost",
            "CustomModelData", "HideFlags", "Potion", "CustomPotionColor", "CustomPotionEffects",
            "Trim", "BlockEntityTag", "EntityTag", "SkullOwner", "Fireworks", "Explosion", "title",
            "author", "generation", "Charged", "ChargedProjectiles", "map", "LodestoneDimension",
            "BucketVariantTag");

    private NbtPresets() {}

    public static List<Preset> common() {
        return COMMON;
    }

    public static List<String> knownProperties() {
        return KNOWN;
    }

    /** True for a top-level key that names one of the {@link #knownProperties()}. */
    public static boolean isKnownTopLevel(String key) {
        return !key.contains(".") && KNOWN.contains(key);
    }

    /** The preset that writes to this path, or null when it is not a preset. */
    public static Preset byComponentId(String componentId) {
        for (Preset preset : COMMON) {
            if (preset.componentId().equals(componentId)) return preset;
        }
        return null;
    }

    /**
     * How this property's value should be edited. Anything the preset table does not cover stays
     * raw JSON — a guessed shape produces a criterion that silently never matches.
     */
    public static ValueKind valueKindFor(String componentId) {
        Preset preset = byComponentId(componentId);
        return preset == null ? ValueKind.JSON : preset.valueKind();
    }

    public static final String ENCHANTMENTS = "Enchantments";
    public static final String STORED_ENCHANTMENTS = "StoredEnchantments";

    /**
     * Friendly name for one of the two enchantment lists.
     *
     * <p>They are told apart here rather than at each call site because the two look nearly
     * identical everywhere they appear — picker row, card heading, card description — and a single
     * spot that forgets the difference makes them indistinguishable, which is exactly what
     * happened when both cards described themselves as "enchantments on the item".
     */
    public static String enchantmentNameKey(String topLevelKey) {
        return STORED_ENCHANTMENTS.equals(topLevelKey)
                ? "editor.historystages.nbt.enchantments.stored"
                : "editor.historystages.nbt.enchantments.active";
    }

    public static String enchantmentDescriptionKey(String topLevelKey) {
        return STORED_ENCHANTMENTS.equals(topLevelKey)
                ? "editor.historystages.nbt.desc.stored_enchantments"
                : "editor.historystages.nbt.desc.enchantments";
    }

    /**
     * Always false here. On 1.21 the pre-1.20.5 key names only match inside {@code custom_data}
     * and the editor offers to convert them; on 1.20.1 those keys are the item's real data.
     */
    public static boolean isLegacyKey(String key) {
        return false;
    }

    /** Null here, see {@link #isLegacyKey}. */
    public static String componentForLegacyKey(String key) {
        return null;
    }
}
