package net.bananemdnsa.historystages.data.lock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropMatchingTest {

    @Test
    void idMatches() {
        assertTrue(CropMatching.matches(List.of("minecraft:wheat"), "minecraft:wheat", Set.of()));
    }

    @Test
    void tagMatches() {
        assertTrue(CropMatching.matches(List.of("#minecraft:crops"), "modx:tomato", Set.of("minecraft:crops")));
    }

    @Test
    void unlistedDoesNot() {
        assertFalse(CropMatching.matches(List.of("minecraft:wheat"), "minecraft:carrots", Set.of("minecraft:crops")));
    }

    /** A tag written without "#" is an id that happens to look like a tag, not a tag. */
    @Test
    void bareTagNameIsNotAnId() {
        assertFalse(CropMatching.matches(List.of("minecraft:crops"), "minecraft:wheat", Set.of("minecraft:crops")));
    }

    @Test
    void blankEntriesAreIgnored() {
        assertFalse(CropMatching.matches(List.of("", "#"), "minecraft:wheat", Set.of("")));
    }
}
