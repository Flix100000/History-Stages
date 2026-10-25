package net.bananemdnsa.historystages.client.editor.tab;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelChoicesTest {

    /** 1 stands for "every level", then one choice per level that exists. */
    @Test
    void oneChoicePerExistingLevel() {
        assertEquals(List.of(1, 2, 3, 4, 5), LevelChoices.of(5, 1));
    }

    /** An enchantment with a single level has nothing to pick but "every level". */
    @Test
    void aSingleLevelOffersOnlyEveryLevel() {
        assertEquals(List.of(1), LevelChoices.of(1, 1));
    }

    /** Unknown maximum (an effect no potion uses): offer a sensible handful. */
    @Test
    void anUnknownMaximumOffersFive() {
        assertEquals(List.of(1, 2, 3, 4, 5), LevelChoices.of(0, 1));
    }

    /** A level stored by hand above the known maximum stays selectable instead of vanishing. */
    @Test
    void theCurrentLevelIsAlwaysOffered() {
        assertEquals(List.of(1, 2, 3, 7), LevelChoices.of(3, 7));
    }
}
