package net.bananemdnsa.historystages.data.disguise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The parsed content of {@code settings/disguises.json}. Immutable; edits return a copy.
 *
 * <p>Kept free of Minecraft classes so the rules — validation, cycle detection, the file format —
 * can be unit-tested. Matching against real items lives in {@link Disguises}.
 *
 * <p>Cycles are only detectable between item keys here. A tag key never becomes a chain step
 * (a chain steps from an item id to the rule for that id), so it cannot close a loop on its own;
 * a loop that runs through tag membership is caught at runtime by {@link DisguiseChain}.
 */
public final class DisguiseRuleSet {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DisguiseRuleSet EMPTY = new DisguiseRuleSet(new TreeMap<>());

    /** Sorted by key, so tag ties resolve the same way on every machine and saves diff cleanly. */
    private final TreeMap<String, DisguiseRule> rules;
    private final List<DisguiseRule> tagRules;

    private DisguiseRuleSet(TreeMap<String, DisguiseRule> rules) {
        this.rules = rules;
        List<DisguiseRule> tags = new ArrayList<>();
        for (DisguiseRule r : rules.values()) {
            if (r.isTag()) tags.add(r);
        }
        this.tagRules = Collections.unmodifiableList(tags);
    }

    public static DisguiseRuleSet empty() {
        return EMPTY;
    }

    /** The result of reading a file: the rules that survived, and why the others did not. */
    public record Parsed(DisguiseRuleSet set, List<String> problems) {}

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public Collection<DisguiseRule> all() {
        return Collections.unmodifiableCollection(rules.values());
    }

    public DisguiseRule get(String key) {
        return rules.get(key);
    }

    public DisguiseRule itemRule(String itemId) {
        return itemId.startsWith("#") ? null : rules.get(itemId);
    }

    public DisguiseRule tagRule(String tagId) {
        return rules.get("#" + tagId);
    }

    /** Every tag rule, sorted by key. The first one an item belongs to wins. */
    public List<DisguiseRule> tagRules() {
        return tagRules;
    }

    public DisguiseRuleSet withRule(DisguiseRule rule) {
        TreeMap<String, DisguiseRule> copy = new TreeMap<>(rules);
        copy.put(rule.key(), rule);
        return new DisguiseRuleSet(copy);
    }

    public DisguiseRuleSet without(String key) {
        if (!rules.containsKey(key)) return this;
        TreeMap<String, DisguiseRule> copy = new TreeMap<>(rules);
        copy.remove(key);
        return new DisguiseRuleSet(copy);
    }

    /**
     * Whether setting {@code key → target} would close a loop over item keys. Asked by the editor
     * and the save packet before an edit is accepted.
     */
    public boolean wouldCycle(String key, String target) {
        if (key.startsWith("#")) return false;
        Set<String> seen = new HashSet<>();
        String current = target;
        while (current != null && seen.add(current)) {
            if (current.equals(key)) return true;
            DisguiseRule next = rules.get(current);
            current = next != null && !next.isTag() ? next.as() : null;
        }
        return false;
    }

    // --- file format ---

    public static Parsed fromJson(String json) {
        List<String> problems = new ArrayList<>();
        if (json == null || json.isBlank()) return new Parsed(EMPTY, problems);

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                problems.add("disguises.json is not a JSON object");
                return new Parsed(EMPTY, problems);
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            problems.add("disguises.json could not be parsed: " + e.getMessage());
            return new Parsed(EMPTY, problems);
        }

        TreeMap<String, DisguiseRule> rules = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            DisguiseRule rule = readRule(e.getKey(), e.getValue(), problems);
            if (rule != null) rules.put(rule.key(), rule);
        }
        removeCycles(rules, problems);
        return new Parsed(rules.isEmpty() ? EMPTY : new DisguiseRuleSet(rules), problems);
    }

    private static DisguiseRule readRule(String key, JsonElement value, List<String> problems) {
        if (!isValidKey(key)) {
            problems.add("'" + key + "': key must be an item id or #tag (mod ids are not allowed)");
            return null;
        }
        if (value == null || !value.isJsonObject()) {
            problems.add("'" + key + "': value must be an object");
            return null;
        }
        JsonObject obj = value.getAsJsonObject();
        String as = readString(obj, "as");
        if (as == null || as.isBlank()) {
            problems.add("'" + key + "': missing \"as\"");
            return null;
        }
        if (!isValidId(as)) {
            problems.add("'" + key + "': \"as\" must be an item id, got '" + as + "'");
            return null;
        }
        if (as.equals(key)) {
            problems.add("'" + key + "': an item cannot be disguised as itself");
            return null;
        }
        DropsMode drops = DropsMode.parse(readString(obj, "drops"));
        boolean hints = !obj.has("hints") || !obj.get("hints").isJsonPrimitive()
                || obj.get("hints").getAsBoolean();
        JsonObject nbt = obj.has("nbt") && obj.get("nbt").isJsonObject() ? obj.getAsJsonObject("nbt") : null;
        if (nbt != null && key.startsWith("#")) {
            problems.add("'" + key + "': \"nbt\" only works on item keys and was ignored");
            nbt = null;
        }
        return new DisguiseRule(key, as, drops, hints, nbt);
    }

    private static String readString(JsonObject obj, String name) {
        JsonElement el = obj.get(name);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static boolean isValidKey(String key) {
        if (key.startsWith("#")) return isValidId(key.substring(1));
        return isValidId(key);
    }

    private static boolean isValidId(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && colon < id.length() - 1 && id.indexOf(':', colon + 1) < 0
                && id.chars().noneMatch(Character::isWhitespace);
    }

    /** Drops every item rule that lies on a loop, so no chain can start one. */
    private static void removeCycles(TreeMap<String, DisguiseRule> rules, List<String> problems) {
        Set<String> onCycle = new HashSet<>();
        for (String start : rules.keySet()) {
            if (start.startsWith("#")) continue;
            List<String> path = new ArrayList<>();
            String current = start;
            while (current != null && !path.contains(current)) {
                path.add(current);
                DisguiseRule r = rules.get(current);
                current = r != null && !r.isTag() ? r.as() : null;
            }
            if (current != null && current.equals(start)) onCycle.addAll(path);
        }
        if (onCycle.isEmpty()) return;
        List<String> sorted = new ArrayList<>(onCycle);
        Collections.sort(sorted);
        problems.add("disguise loop between " + String.join(", ", sorted) + " — these rules were ignored");
        sorted.forEach(rules::remove);
    }

    public static String toJson(DisguiseRuleSet set) {
        JsonObject root = new JsonObject();
        for (DisguiseRule r : set.rules.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("as", r.as());
            if (r.drops() != DropsMode.REAL) obj.addProperty("drops", r.drops().serialize());
            if (!r.hints()) obj.addProperty("hints", false);
            if (r.hasNbt()) obj.add("nbt", r.nbt().deepCopy());
            root.add(r.key(), obj);
        }
        return GSON.toJson(root);
    }
}
