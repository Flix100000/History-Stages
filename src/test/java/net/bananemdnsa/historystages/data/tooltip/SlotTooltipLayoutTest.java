package net.bananemdnsa.historystages.data.tooltip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotTooltipLayoutTest {

    private static List<String> ids(List<ScrollTooltipLine> lines) {
        return lines.stream().map(ScrollTooltipLine::id).toList();
    }

    @Test
    void defaultsHaveEveryLineInFactoryOrder() {
        assertEquals(List.of("title", "hint", "required", "stage_color"),
                ids(SlotTooltipLayout.defaults()));
    }

    /** A layout saved while the summary line still existed loads without it. */
    @Test
    void removedSummaryLineIsDroppedFromOldConfigs() {
        List<ScrollTooltipLine> parsed = SlotTooltipLayout.parse(List.of(
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("summary", true, false, "gold", ""))));
        assertEquals(List.of("title", "hint", "required", "stage_color"), ids(parsed));
    }

    @Test
    void savedOrderOfMovableLinesIsKept() {
        List<ScrollTooltipLine> parsed = SlotTooltipLayout.parse(List.of(
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("required", true, false, "dark_red", "")),
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("title", true, false, "red", ""))));
        assertEquals(List.of("required", "title", "hint", "stage_color"), ids(parsed));
        assertTrue(parsed.get(0).enabled());
    }

    @Test
    void unknownAndDuplicateLinesAreDropped() {
        List<ScrollTooltipLine> parsed = SlotTooltipLayout.parse(List.of(
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("nonsense", true, false, "", "")),
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("hint", false, false, "", "")),
                ScrollTooltipLayout.encodeLine(new ScrollTooltipLine("hint", true, false, "", ""))));
        assertEquals(SlotTooltipLayout.defaults().size(), parsed.size());
        assertFalse(parsed.stream().filter(l -> l.id().equals("hint")).findFirst().orElseThrow().enabled());
    }

    @Test
    void encodedDefaultsRoundTrip() {
        assertEquals(SlotTooltipLayout.defaults(), SlotTooltipLayout.parse(SlotTooltipLayout.defaultsEncoded()));
    }

    @Test
    void movableIdsExcludeTheColourOption() {
        assertEquals(List.of("title", "hint", "required"), SlotTooltipLayout.MOVABLE_IDS);
    }
}
