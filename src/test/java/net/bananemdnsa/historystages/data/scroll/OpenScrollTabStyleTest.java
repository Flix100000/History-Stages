package net.bananemdnsa.historystages.data.scroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenScrollTabStyleTest {

    @Test
    void everyStyleRoundTripsThroughItsSerializedName() {
        for (OpenScrollTabStyle style : OpenScrollTabStyle.values()) {
            assertEquals(style, OpenScrollTabStyle.parse(style.serialize()));
        }
    }

    @Test
    void anUnknownStyleFallsBackToIcons() {
        assertEquals(OpenScrollTabStyle.ICONS, OpenScrollTabStyle.parse("banners"));
        assertEquals(OpenScrollTabStyle.ICONS, OpenScrollTabStyle.parse(null));
    }

    @Test
    void parsingIgnoresCaseAndSurroundingSpace() {
        assertEquals(OpenScrollTabStyle.WORDS, OpenScrollTabStyle.parse(" WORDS  "));
    }
}
