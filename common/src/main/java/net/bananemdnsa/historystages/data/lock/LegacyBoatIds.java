package net.bananemdnsa.historystages.data.lock;

import net.bananemdnsa.historystages.data.NbtFilter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Turns the boat entries of 1.21.1 stage files into the boat types of 26.2.
 *
 * <p>1.21.1 has one {@code minecraft:boat} and one {@code minecraft:chest_boat}; the wood is the
 * entity's {@code Type} tag, so "oak boats only" was written as an NBT filter. Since 1.21.2 every
 * wood is an entity type of its own ({@code minecraft:oak_boat}, {@code minecraft:bamboo_raft}…)
 * and boats carry no {@code Type} any more, so such a filter would never match again. An entry for
 * the old id becomes one entry per wood it named, or per wood there is when it named none; what
 * else its filter asked for stays on each of them.
 *
 * <p>The woods are listed here rather than read from the registry: stage files are read in unit
 * tests too, where there is none. A wood added by a later version is not covered by an old entry,
 * which is no loss — the old file could not have meant it.
 */
public final class LegacyBoatIds {

    static final String BOAT = "minecraft:boat";
    static final String CHEST_BOAT = "minecraft:chest_boat";

    private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia",
            "cherry", "dark_oak", "pale_oak", "mangrove", "bamboo");

    private LegacyBoatIds() {}

    public static boolean isLegacy(String id) {
        return BOAT.equals(id) || CHEST_BOAT.equals(id);
    }

    /** The 26.2 id of one wood's boat or chest boat (bamboo has rafts). */
    static String idFor(String wood, boolean chest) {
        if ("bamboo".equals(wood)) return chest ? "minecraft:bamboo_chest_raft" : "minecraft:bamboo_raft";
        return "minecraft:" + wood + (chest ? "_chest_boat" : "_boat");
    }

    /**
     * The entries an old boat entry stands for, built by {@code make} from an id and the filter
     * left once the wood is taken out. Any other entry comes back as it is, through {@code make}.
     */
    public static <T> List<T> expand(String id, NbtFilter filter, BiFunction<String, NbtFilter, T> make) {
        if (!isLegacy(id)) return List.of(make.apply(id, filter));
        boolean chest = CHEST_BOAT.equals(id);

        // wood -> what else the alternatives naming it ask for; null = nothing else (match all).
        Map<String, List<JsonObject>> byWood = new LinkedHashMap<>();
        if (filter.isEmpty()) {
            for (String wood : WOODS) byWood.put(wood, null);
        } else {
            for (JsonObject alternative : filter.alternatives()) {
                JsonObject rest = alternative.deepCopy();
                JsonElement type = rest.remove("Type");
                List<String> woods = type != null && type.isJsonPrimitive()
                        && WOODS.contains(type.getAsString()) ? List.of(type.getAsString()) : WOODS;
                for (String wood : woods) {
                    if (byWood.containsKey(wood) && byWood.get(wood) == null) continue;
                    if (rest.size() == 0) {
                        byWood.put(wood, null);
                    } else {
                        byWood.computeIfAbsent(wood, w -> new ArrayList<>()).add(rest);
                    }
                }
            }
        }

        List<T> out = new ArrayList<>();
        for (String wood : WOODS) {
            if (!byWood.containsKey(wood)) continue;
            List<JsonObject> rest = byWood.get(wood);
            out.add(make.apply(idFor(wood, chest), rest == null ? NbtFilter.NONE : NbtFilter.of(rest)));
        }
        return out;
    }
}
