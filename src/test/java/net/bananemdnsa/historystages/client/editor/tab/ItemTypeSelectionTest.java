package net.bananemdnsa.historystages.client.editor.tab;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemTypeSelectionTest {

    private static final List<String> ALL = List.of("minecraft:enchanted_book", "minecraft:iron_sword",
            "minecraft:netherite_sword");

    /** Ticked means locked, so the list opens with everything not spared ticked. */
    @Test
    void lockedIsEverythingNotSpared() {
        assertEquals(List.of("minecraft:enchanted_book", "minecraft:iron_sword"),
                ItemTypeSelection.locked(ALL, List.of("minecraft:netherite_sword")));
    }

    @Test
    void sparedIsEverythingNotTicked() {
        assertEquals(List.of("minecraft:netherite_sword"),
                ItemTypeSelection.spared(ALL, Set.of("minecraft:enchanted_book", "minecraft:iron_sword")));
    }

    /** A type spared earlier but no longer offered (mod removed) stays spared rather than vanishing. */
    @Test
    void aSparedTypeNoLongerOfferedIsKept() {
        assertEquals(List.of("minecraft:netherite_sword", "oldmod:blade"),
                ItemTypeSelection.spared(ALL, Set.of("minecraft:enchanted_book", "minecraft:iron_sword"),
                        List.of("oldmod:blade")));
    }

    @Test
    void nothingTickedSparesEverything() {
        assertEquals(ALL, ItemTypeSelection.spared(ALL, Set.of()));
    }
}
