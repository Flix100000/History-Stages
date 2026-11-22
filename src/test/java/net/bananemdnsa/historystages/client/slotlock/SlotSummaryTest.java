package net.bananemdnsa.historystages.client.slotlock;

import net.bananemdnsa.historystages.data.lock.SlotLockEntry;
import net.bananemdnsa.historystages.data.lock.SlotLockMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotSummaryTest {

    private static List<SlotSummary.Part> describe(String target, Integer... slots) {
        return SlotSummary.describe(new SlotLockEntry(target, List.of(slots), SlotLockMode.HIDE));
    }

    @Test
    void hotbarRangesCollapse() {
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.hotbar", "1–3, 7–9")),
                describe("player", 0, 1, 2, 6, 7, 8));
    }

    @Test
    void wholeHotbarIsNamedAsSuch() {
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.hotbar_all", null)),
                describe("player", 0, 1, 2, 3, 4, 5, 6, 7, 8));
    }

    @Test
    void fullRowNamedAsRow() {
        Integer[] row3 = {27, 28, 29, 30, 31, 32, 33, 34, 35};
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.row", "3")),
                describe("player", row3));
    }

    @Test
    void partialMainInventoryCountsFromOne() {
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.inventory", "1–2")),
                describe("player", 9, 10));
    }

    @Test
    void armorAndOffhandNamed() {
        assertEquals(List.of(
                        new SlotSummary.Part("editor.historystages.slots.summary.armor.feet", null),
                        new SlotSummary.Part("editor.historystages.slots.summary.armor.head", null),
                        new SlotSummary.Part("editor.historystages.slots.summary.offhand", null)),
                describe("player", 40, 39, 36));
    }

    @Test
    void containerRangesCompress() {
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.container", "0, 18–26")),
                describe("minecraft:barrel", 26, 18, 19, 20, 21, 22, 23, 24, 25, 0));
    }

    @Test
    void nothingSelectedSaysSo() {
        assertEquals(List.of(new SlotSummary.Part("editor.historystages.slots.summary.none", null)),
                describe("player"));
    }
}
