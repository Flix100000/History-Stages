package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The entity keys worth offering by name, the way {@link NbtPresets} does it for item components.
 *
 * <p>A fresh entity cannot list these by itself: flags like {@code Silent} or {@code NoGravity}
 * are only written once they are switched on, so reading a new entity's tag never shows them.
 * Free of {@code net.minecraft} like the rest of this package; which choices a value has is
 * filled in by the screen, which can reach the registries.
 */
public final class EntityNbtPresets {

    /** How the value is entered. Only steers suggestions and the default; the key is free text either way. */
    public enum Kind { BOOL, NUMBER, CHOICE, LIST, JSON }

    /** When the preset is offered for an entity type. */
    public enum Scope {
        /** Every entity. */
        ANY,
        /** Living entities only. */
        LIVING,
        /** Mobs only. */
        MOB,
        /** Only when a fresh entity of the type saves this key, so it is known to apply. */
        KEYED,
        /** Only for the listed entity ids: the key exists but is not saved until it is set. */
        TYPES
    }

    public record Preset(String key, Kind kind, Scope scope, String defaultValue, List<String> types) {
        public String nameKey() {
            return "editor.historystages.entity_nbt.preset." + key + ".name";
        }

        public String descriptionKey() {
            return "editor.historystages.entity_nbt.preset." + key + ".desc";
        }
    }

    private static final Map<String, Preset> BY_KEY = new LinkedHashMap<>();

    static {
        add("Silent", Kind.BOOL, Scope.ANY, "true");
        add("NoGravity", Kind.BOOL, Scope.ANY, "true");
        add("Glowing", Kind.BOOL, Scope.ANY, "true");
        add("Invulnerable", Kind.BOOL, Scope.ANY, "true");
        add("Tags", Kind.LIST, Scope.ANY, "[\"my_tag\"]");

        add("Health", Kind.NUMBER, Scope.LIVING, "1-10");

        add("NoAI", Kind.BOOL, Scope.MOB, "true");
        add("PersistenceRequired", Kind.BOOL, Scope.MOB, "true");
        add("LeftHanded", Kind.BOOL, Scope.MOB, "true");
        add("CanPickUpLoot", Kind.BOOL, Scope.MOB, "true");

        add("Type", Kind.CHOICE, Scope.KEYED, "");
        add("variant", Kind.CHOICE, Scope.KEYED, "");
        add("Variant", Kind.CHOICE, Scope.KEYED, "0");
        add("Color", Kind.CHOICE, Scope.KEYED, "0");
        add("RabbitType", Kind.CHOICE, Scope.KEYED, "0");
        add("MainGene", Kind.CHOICE, Scope.KEYED, "normal");
        add("VillagerData", Kind.JSON, Scope.KEYED, "{\"profession\": \"minecraft:farmer\"}");
        add("Age", Kind.NUMBER, Scope.KEYED, "0");
        add("IsBaby", Kind.BOOL, Scope.KEYED, "true");
        add("Size", Kind.NUMBER, Scope.KEYED, "0");
        add("Sheared", Kind.BOOL, Scope.KEYED, "true");
        add("Saddle", Kind.BOOL, Scope.KEYED, "true");
        BY_KEY.put("powered", new Preset("powered", Kind.BOOL, Scope.TYPES, "true", List.of("minecraft:creeper")));
        add("Tame", Kind.BOOL, Scope.KEYED, "true");
        add("FromBucket", Kind.BOOL, Scope.KEYED, "true");
        add("Invisible", Kind.BOOL, Scope.KEYED, "true");
        add("Small", Kind.BOOL, Scope.KEYED, "true");
        add("ShowArms", Kind.BOOL, Scope.KEYED, "true");
        add("NoBasePlate", Kind.BOOL, Scope.KEYED, "true");
    }

    private EntityNbtPresets() {}

    private static void add(String key, Kind kind, Scope scope, String defaultValue) {
        BY_KEY.put(key, new Preset(key, kind, scope, defaultValue, List.of()));
    }

    public static List<Preset> all() {
        return List.copyOf(BY_KEY.values());
    }

    public static Preset byKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }
}
