package net.bananemdnsa.historystages.data.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bananemdnsa.historystages.util.DebugLogger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads and writes {@code settings/graph_stages.json} — the per-stage descriptions and style
 * overrides the pack author hand-writes.
 *
 * <p>This is deliberately a different file from {@code graph_layout.json}
 * ({@link GraphLayoutData}) even though both are keyed by stage id: the two have different
 * owners. The layout file is written by the machine (the auto-layout algorithm, and dragging in
 * the editor), so "re-arrange" is a plain delete of that file. If the hand-written descriptions
 * lived alongside it, resetting the layout would become a selective key removal — and a bug
 * there would cost a pack author their texts. Keeping them apart makes "re-arrange" trivially
 * safe.
 */
public final class GraphStageData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Snapshot current = Snapshot.empty();

    private GraphStageData() {}

    /**
     * One stage's hand-written data: a description, a style override, or both.
     *
     * <p>{@code style} applies in every state; {@code styles} layers on top of it per state.
     * Keeping the older key as the base is what lets a file written before per-state overrides
     * existed load unchanged — and it keeps "always a hexagon, red while locked" one line each
     * instead of three copies of the shape.
     *
     * <p>Also the wire form: {@code SaveStageGraphStylePacket} sends an {@code Entry} whose
     * {@code description} is null, so the fragment on the wire is a fragment of the file.
     */
    public static class Entry {
        /** Literal text or a translation key; null = none. */
        public String description;
        /** Applies in every state. May be null. */
        public StageStyle style;
        /** Per-state, layered over {@link #style}. May be null. */
        public StateStyles styles;
        /** Map background while this is the player's latest unlock with one. May be null. */
        public CanvasBackgroundStyle background;
        /** Id of the {@link Preset} under this stage's own style. May be null. */
        public String preset;

        public boolean isEmpty() {
            return (description == null || description.isBlank())
                    && preset == null
                    && (style == null || style.isEmpty())
                    && (styles == null || styles.isEmpty())
                    && (background == null || background.isEmpty());
        }

        /**
         * True when either style block carries anything. Reads the fields rather than going
         * through {@link #copyStyles()}, because the sidebar asks this once per row per frame
         * and three objects per stage per frame is a lot of garbage for a 3×3 dot.
         */
        public boolean hasStyles() {
            return preset != null
                    || (style != null && !style.isEmpty()) || (styles != null && !styles.isEmpty())
                    || (background != null && !background.isEmpty());
        }

        /** Everything but the description, deep-copied — for an edit buffer or the clipboard. */
        public Entry copyStyles() {
            Entry out = new Entry();
            out.style = style == null ? null : style.copy();
            out.styles = styles == null ? null : styles.copy();
            out.background = background == null ? null : background.copy();
            out.preset = preset;
            return out;
        }
    }

    /**
     * A named node look that stages point at. Change it once and every stage using it follows; a
     * stage's own style still lies on top. Holds the node look only — the map background is a
     * per-stage event, not part of how a kind of stage looks.
     */
    public static class Preset {
        public String name;
        /** Applies in every state. May be null. */
        public StageStyle style;
        /** Per-state, layered over {@link #style}. May be null. */
        public StateStyles styles;

        public Preset copy() {
            Preset out = new Preset();
            out.name = name;
            out.style = style == null ? null : style.copy();
            out.styles = styles == null ? null : styles.copy();
            return out;
        }

        /** The look for one state: the all-states block with that state's block folded on top. */
        public StageStyle style(NodeState state) {
            return StageStyle.overlay(style, styles == null ? null : styles.get(state));
        }

        /**
         * One preset that looks exactly like {@code base} with a stage's own blocks on top, in
         * every state — what "save as preset" stores.
         *
         * <p>Not a plain block-by-block overlay: a stage's all-states value beats the preset's
         * per-state one, but in a single preset a per-state value beats the all-states one. So
         * the base's per-state blocks lose every field the stage's all-states block sets.
         */
        public static Preset flatten(String name, Preset base, StageStyle ownStyle, StateStyles ownStyles) {
            Preset out = new Preset();
            out.name = name;
            StageStyle merged = StageStyle.overlay(base == null ? null : base.style, ownStyle);
            out.style = merged.isEmpty() ? null : merged;
            StateStyles states = new StateStyles();
            for (NodeState state : NodeState.values()) {
                StageStyle basePart = without(base == null || base.styles == null ? null : base.styles.get(state), ownStyle);
                StageStyle block = StageStyle.overlay(basePart, ownStyles == null ? null : ownStyles.get(state));
                states.set(state, block.isEmpty() ? null : block);
            }
            out.styles = states.isEmpty() ? null : states;
            return out;
        }

        /** {@code style} minus every field {@code mask} sets. */
        private static StageStyle without(StageStyle style, StageStyle mask) {
            StageStyle out = style == null ? new StageStyle() : style.copy();
            if (mask == null) return out;
            for (String leaf : StageStyleFields.LEAVES) {
                if (StageStyleFields.get(mask, leaf) != null) StageStyleFields.set(out, leaf, null);
            }
            return out;
        }
    }

    /** Immutable entry maps, one per stage tree, plus the presets both trees share. */
    public record Snapshot(Map<String, Entry> global, Map<String, Entry> individual,
                           Map<String, Preset> presets) {

        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of());
        }

        private Snapshot withTree(boolean individual, Map<String, Entry> tree) {
            return individual ? new Snapshot(global, tree, presets) : new Snapshot(tree, this.individual, presets);
        }

        public Map<String, Entry> tree(boolean individual) {
            return individual ? individual() : global();
        }

        /** Null when the stage has no entry, or its description is absent or blank. */
        public String description(String stageId, boolean individual) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null || entry.description == null || entry.description.isBlank()) {
                return null;
            }
            return entry.description;
        }

        /**
         * Never null; an empty {@link StageStyle} when the stage has no override. A copy, so a
         * caller cannot write back into the loaded snapshot through the style it was handed.
         */
        public StageStyle style(String stageId, boolean individual) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null || entry.style == null) return new StageStyle();
            return entry.style.copy();
        }

        /**
         * The override that applies to one node: the all-states block with the per-state block
         * folded on top. Never null; an empty {@link StageStyle} when the stage has neither.
         */
        public StageStyle style(String stageId, boolean individual, NodeState state) {
            Entry entry = tree(individual).get(stageId);
            if (entry == null) return new StageStyle();
            StageStyle perState = entry.styles == null ? null : entry.styles.get(state);
            StageStyle own = StageStyle.overlay(entry.style, perState);
            Preset preset = entry.preset == null ? null : presets.get(entry.preset);
            // The stage wins field by field — its all-states value beats the preset's per-state
            // one too, so "Boss, but blue" stays blue in every state.
            return preset == null ? own : StageStyle.overlay(preset.style(state), own);
        }

        /** The preset's look in one state, or an empty style for no or an unknown preset. */
        public StageStyle presetStyle(String presetId, NodeState state) {
            Preset preset = presetId == null ? null : presets.get(presetId);
            return preset == null ? new StageStyle() : preset.style(state);
        }

        /** Presets ordered by name, case-insensitively — the order every list shows them in. */
        public List<Map.Entry<String, Preset>> presetsByName() {
            List<Map.Entry<String, Preset>> out = new ArrayList<>(presets.entrySet());
            out.sort(Comparator.comparing(e -> e.getValue().name == null
                    ? "" : e.getValue().name.toLowerCase(Locale.ROOT)));
            return out;
        }

        /** True when another preset already has this name, ignoring case and outer spaces. */
        public boolean presetNameTaken(String name, String exceptId) {
            String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Preset> e : presets.entrySet()) {
                if (e.getKey().equals(exceptId)) continue;
                String other = e.getValue().name == null ? "" : e.getValue().name.trim().toLowerCase(Locale.ROOT);
                if (other.equals(wanted)) return true;
            }
            return false;
        }

        /** How many stages, in both trees, point at this preset. */
        public int usageCount(String presetId) {
            int count = 0;
            for (Entry entry : global.values()) if (presetId.equals(entry.preset)) count++;
            for (Entry entry : individual.values()) if (presetId.equals(entry.preset)) count++;
            return count;
        }

        /** Adds or replaces one preset; the stages pointing at it are untouched. */
        public Snapshot withPreset(String presetId, Preset preset) {
            Map<String, Preset> copy = new LinkedHashMap<>(presets);
            copy.put(presetId, preset.copy());
            return new Snapshot(global, individual, copy);
        }

        /**
         * Drops a preset and every reference to it. The stages keep their own values; one that
         * held nothing but the reference goes, so the file does not fill up with empty objects.
         */
        public Snapshot withoutPreset(String presetId) {
            Map<String, Preset> copy = new LinkedHashMap<>(presets);
            copy.remove(presetId);
            return new Snapshot(unlink(global, presetId), unlink(individual, presetId), copy);
        }

        private static Map<String, Entry> unlink(Map<String, Entry> tree, String presetId) {
            Map<String, Entry> out = new LinkedHashMap<>();
            for (Map.Entry<String, Entry> e : tree.entrySet()) {
                Entry entry = e.getValue();
                if (presetId.equals(entry.preset)) {
                    Entry updated = withPresetId(entry, null);
                    if (!updated.isEmpty()) out.put(e.getKey(), updated);
                } else {
                    out.put(e.getKey(), entry);
                }
            }
            return out;
        }

        /** Points one stage at a preset, or at none with {@code presetId == null}. */
        public Snapshot withAssignedPreset(String stageId, boolean individual, String presetId) {
            Map<String, Entry> copy = new LinkedHashMap<>(tree(individual));
            Entry existing = copy.get(stageId);
            Entry updated = withPresetId(existing == null ? new Entry() : existing, presetId);
            if (updated.isEmpty()) copy.remove(stageId);
            else copy.put(stageId, updated);
            return withTree(individual, copy);
        }

        private static Entry withPresetId(Entry entry, String presetId) {
            Entry out = entry.copyStyles();
            out.description = entry.description;
            out.preset = presetId;
            return out;
        }

        /**
         * Returns a new snapshot with the given stage's description replaced. A null or blank
         * {@code text} removes the description; if the entry is then left with neither a
         * description nor a style, it is dropped entirely so the file does not accumulate empty
         * objects.
         */
        public Snapshot withDescription(String stageId, boolean individual, String text) {
            Map<String, Entry> copy = new LinkedHashMap<>(tree(individual));

            Entry existing = copy.get(stageId);
            Entry updated = new Entry();
            updated.description = (text == null || text.isBlank()) ? null : text;
            updated.style = existing == null ? null : existing.style;
            updated.styles = existing == null ? null : existing.styles;
            updated.background = existing == null ? null : existing.background;
            updated.preset = existing == null ? null : existing.preset;

            if (updated.isEmpty()) {
                copy.remove(stageId);
            } else {
                copy.put(stageId, updated);
            }

            return withTree(individual, copy);
        }

        /**
         * Returns a new snapshot with the given stage's style halves replaced by the ones in
         * {@code source}, keeping any description untouched. An entry left with nothing at all is
         * dropped, so the file does not accumulate empty objects.
         */
        public Snapshot withStyle(String stageId, boolean individual, Entry source) {
            Map<String, Entry> copy = new LinkedHashMap<>(tree(individual));

            Entry existing = copy.get(stageId);
            Entry updated = new Entry();
            updated.description = existing == null ? null : existing.description;
            updated.style = source == null || source.style == null || source.style.isEmpty()
                    ? null : source.style.copy();
            updated.styles = source == null || source.styles == null || source.styles.isEmpty()
                    ? null : source.styles.copy();
            updated.background = source == null || source.background == null
                    || source.background.isEmpty() ? null : source.background.copy();
            updated.preset = source == null ? null : source.preset;

            if (updated.isEmpty()) {
                copy.remove(stageId);
            } else {
                copy.put(stageId, updated);
            }

            return withTree(individual, copy);
        }
    }

    // --- pure conversion, unit-tested ---

    public static Snapshot fromJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return Snapshot.empty();
            JsonObject obj = root.getAsJsonObject();
            return new Snapshot(section(obj, "global"), section(obj, "individual"), presets(obj));
        } catch (Exception e) {
            // A hand-edited file with a typo must not take the graph down; the author sees no
            // descriptions/styles for now and can fix the file.
            DebugLogger.error("Stage Graph", "Could not parse graph_stages.json: " + e.getMessage());
            return Snapshot.empty();
        }
    }

    private static Map<String, Entry> section(JsonObject root, String name) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (!root.has(name) || !root.get(name).isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(name).entrySet()) {
            try {
                Entry entry = GSON.fromJson(e.getValue(), Entry.class);
                if (entry != null) out.put(e.getKey(), entry);
            } catch (Exception ex) {
                // Skip one malformed entry; the rest of the file must still load.
            }
        }
        return out;
    }

    private static Map<String, Preset> presets(JsonObject root) {
        Map<String, Preset> out = new LinkedHashMap<>();
        if (!root.has("presets") || !root.get("presets").isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("presets").entrySet()) {
            try {
                Preset preset = GSON.fromJson(e.getValue(), Preset.class);
                if (preset == null) continue;
                // A hand-written preset without a name still needs something to show in a list.
                if (preset.name == null || preset.name.isBlank()) preset.name = e.getKey();
                out.put(e.getKey(), preset);
            } catch (Exception ex) {
                // Skip one malformed preset; the rest of the file must still load.
            }
        }
        return out;
    }

    /**
     * A new preset's id, made from its name: lower case, letters, digits and underscores only,
     * numbered on a collision. Never changes afterwards, so renaming touches no stage.
     */
    public static String newPresetId(String name, Collection<String> taken) {
        String base = (name == null ? "" : name).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "preset";
        if (!taken.contains(base)) return base;
        int n = 2;
        while (taken.contains(base + "_" + n)) n++;
        return base + "_" + n;
    }

    public static String toJson(Snapshot snapshot) {
        JsonObject root = new JsonObject();
        JsonObject presets = new JsonObject();
        // Sorted for the same stable diff as the stage sections.
        for (Map.Entry<String, Preset> e : new TreeMap<>(snapshot.presets()).entrySet()) {
            presets.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        }
        root.add("presets", presets);
        root.add("global", writeSection(snapshot.global()));
        root.add("individual", writeSection(snapshot.individual()));
        return GSON.toJson(root);
    }

    /** One entry's style halves as JSON — the wire form of an override set. */
    public static String entryToJson(Entry entry) {
        Entry styleOnly = entry == null ? new Entry() : entry.copyStyles();
        return GSON.toJson(styleOnly);
    }

    /** Inverse of {@link #entryToJson}. Never null; an empty entry on anything unparseable. */
    public static Entry entryFromJson(String json) {
        try {
            Entry entry = GSON.fromJson(json, Entry.class);
            if (entry == null) return new Entry();
            // The wire form carries styles only. A description arriving here would be a client
            // writing a field this packet has no permission story for.
            entry.description = null;
            return entry;
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not parse a style override: " + e.getMessage());
            return new Entry();
        }
    }

    private static JsonObject writeSection(Map<String, Entry> entries) {
        JsonObject obj = new JsonObject();
        // Sorted so a re-save produces a stable diff for pack authors keeping this in git.
        for (Map.Entry<String, Entry> e : new TreeMap<>(entries).entrySet()) {
            obj.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        }
        return obj;
    }

    // --- file access + in-memory state ---

    public static Snapshot get() {
        return current;
    }

    public static void set(Snapshot snapshot) {
        current = snapshot;
    }

    public static void load() {
        File file = GraphSettingsPaths.file(GraphSettingsPaths.STAGES_FILE);
        if (!file.exists()) {
            current = Snapshot.empty();
            return;
        }
        try {
            current = fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not read graph_stages.json: " + e.getMessage());
            current = Snapshot.empty();
        }
    }

    public static void save() {
        File file = GraphSettingsPaths.file(GraphSettingsPaths.STAGES_FILE);
        try {
            Files.write(file.toPath(), toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            DebugLogger.error("Stage Graph", "Could not write graph_stages.json: " + e.getMessage());
        }
    }
}
