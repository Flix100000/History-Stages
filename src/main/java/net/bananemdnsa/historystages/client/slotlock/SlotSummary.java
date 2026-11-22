package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.data.lock.SlotLockEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A slot lock in words: "Hotbar 1–3, 7–9 · Row 3 · Helmet". Returns lang keys with one optional
 * argument rather than components, so it can be tested without Minecraft.
 */
public final class SlotSummary {

    public record Part(String langKey, String arg) {}

    private static final String PREFIX = "editor.historystages.slots.summary.";
    private static final String[] ARMOR = {"feet", "legs", "chest", "head"};

    private SlotSummary() {}

    public static List<Part> describe(SlotLockEntry entry) {
        TreeSet<Integer> slots = new TreeSet<>(entry.slots());
        List<Part> parts = new ArrayList<>();
        if (slots.isEmpty()) {
            parts.add(new Part(PREFIX + "none", null));
            return parts;
        }
        if (!entry.isPlayerTarget()) {
            parts.add(new Part(PREFIX + "container", ranges(slots, 0)));
            return parts;
        }

        TreeSet<Integer> hotbar = new TreeSet<>(slots.subSet(0, true, 8, true));
        if (hotbar.size() == 9) {
            parts.add(new Part(PREFIX + "hotbar_all", null));
        } else if (!hotbar.isEmpty()) {
            parts.add(new Part(PREFIX + "hotbar", ranges(hotbar, 1)));
        }

        TreeSet<Integer> partialMain = new TreeSet<>();
        for (int row = 0; row < 3; row++) {
            int first = 9 + row * 9;
            TreeSet<Integer> inRow = new TreeSet<>(slots.subSet(first, true, first + 8, true));
            if (inRow.size() == 9) {
                parts.add(new Part(PREFIX + "row", Integer.toString(row + 1)));
            } else {
                partialMain.addAll(inRow);
            }
        }
        if (!partialMain.isEmpty()) {
            // Counted from 1 across the 27 main slots, the way a player would count them.
            parts.add(new Part(PREFIX + "inventory", ranges(partialMain, 1 - 9)));
        }

        for (int armor = 36; armor <= 39; armor++) {
            if (slots.contains(armor)) parts.add(new Part(PREFIX + "armor." + ARMOR[armor - 36], null));
        }
        if (slots.contains(40)) parts.add(new Part(PREFIX + "offhand", null));
        return parts;
    }

    /** "1–3, 7–9" from {0,1,2,6,7,8} with offset 1. En dash, comma-separated. */
    private static String ranges(TreeSet<Integer> slots, int offset) {
        StringBuilder out = new StringBuilder();
        Integer start = null;
        Integer previous = null;
        for (int slot : slots) {
            if (start == null) {
                start = slot;
            } else if (slot != previous + 1) {
                append(out, start + offset, previous + offset);
                start = slot;
            }
            previous = slot;
        }
        append(out, start + offset, previous + offset);
        return out.toString();
    }

    private static void append(StringBuilder out, int from, int to) {
        if (!out.isEmpty()) out.append(", ");
        out.append(from);
        if (to != from) out.append('–').append(to);
    }
}
