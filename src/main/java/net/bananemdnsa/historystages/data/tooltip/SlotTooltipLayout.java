package net.bananemdnsa.historystages.data.tooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The layout of the tooltip on a locked slot: which lines it shows, in which order, with which
 * text and colour. Stored in the same line format as the scroll tooltip, so the two config
 * entries read alike, but with its own small set of ids.
 */
public final class SlotTooltipLayout {

    private SlotTooltipLayout() {}

    /** Reorderable lines, in factory order. */
    public static final List<String> MOVABLE_IDS = List.of("title", "hint", "required");

    /** Colour of the stage names under the "required" header; the value lives in {@code text}. */
    public static final String STAGE_COLOR_ID = "stage_color";

    private static final List<ScrollTooltipLine> DEFAULTS = List.of(
            new ScrollTooltipLine("title", true, false, "red", ""),
            new ScrollTooltipLine("hint", true, false, "gray", ""),
            new ScrollTooltipLine("required", true, false, "dark_red", ""),
            new ScrollTooltipLine(STAGE_COLOR_ID, true, false, "", "gold"));

    private static List<ScrollTooltipLine> active = DEFAULTS;

    public static List<ScrollTooltipLine> defaults() {
        return DEFAULTS;
    }

    public static List<String> defaultsEncoded() {
        return DEFAULTS.stream().map(ScrollTooltipLayout::encodeLine).toList();
    }

    /** Fills in missing lines, drops unknown and repeated ones, and keeps the saved order of the movable ones. */
    public static List<ScrollTooltipLine> parse(List<? extends String> entries) {
        Map<String, ScrollTooltipLine> byId = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                ScrollTooltipLine line = ScrollTooltipLayout.decodeLine(raw);
                if (line == null || byId.containsKey(line.id())) continue;
                if (!MOVABLE_IDS.contains(line.id()) && !STAGE_COLOR_ID.equals(line.id())) continue;
                byId.put(line.id(), line);
                if (MOVABLE_IDS.contains(line.id())) order.add(line.id());
            }
        }
        for (String id : MOVABLE_IDS) if (!order.contains(id)) order.add(id);

        List<ScrollTooltipLine> result = new ArrayList<>(DEFAULTS.size());
        for (String id : order) result.add(byId.getOrDefault(id, defaultLine(id)));
        result.add(byId.getOrDefault(STAGE_COLOR_ID, defaultLine(STAGE_COLOR_ID)));
        return List.copyOf(result);
    }

    public static void rebuildFromConfig(List<? extends String> entries) {
        active = parse(entries);
    }

    public static List<ScrollTooltipLine> active() {
        return active;
    }

    public static ScrollTooltipLine defaultLine(String id) {
        return DEFAULTS.stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }
}
