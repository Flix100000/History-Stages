package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedCanvasBackgroundTest {

    @Test
    void noOverrideKeepsTheConfig() {
        ResolvedCanvasBackground r = ResolvedCanvasBackground.resolve(
                "GRID", "minecraft:a.png", "#17171A", null);

        assertEquals("GRID", r.mode());
        assertEquals("minecraft:a.png", r.texture());
        assertEquals(0x17171A, r.rgb());
    }

    @Test
    void eachOverrideFieldReplacesOnlyItself() {
        CanvasBackgroundStyle override = new CanvasBackgroundStyle();
        override.texture = "minecraft:b.png";

        ResolvedCanvasBackground r = ResolvedCanvasBackground.resolve(
                "TEXTURE", "minecraft:a.png", "#17171A", override);

        assertEquals("TEXTURE", r.mode());
        assertEquals("minecraft:b.png", r.texture());
        assertEquals(0x17171A, r.rgb());
    }

    @Test
    void anOverrideColourAndModeWin() {
        CanvasBackgroundStyle override = new CanvasBackgroundStyle();
        override.mode = "solid";
        override.color = "#102030";

        ResolvedCanvasBackground r = ResolvedCanvasBackground.resolve(
                "GRID", "minecraft:a.png", "#17171A", override);

        assertEquals("SOLID", r.mode());
        assertEquals(0x102030, r.rgb());
    }

    @Test
    void unreadableValuesFallBackToTheBuiltInDefaults() {
        ResolvedCanvasBackground r = ResolvedCanvasBackground.resolve("NOPE", null, "rgb(1,2,3)", null);

        assertEquals("GRID", r.mode());
        assertEquals("", r.texture());
        assertEquals(ResolvedCanvasBackground.DEFAULT_RGB, r.rgb());
    }
}
