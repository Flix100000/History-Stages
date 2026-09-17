package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphStageDataTest {

    @Test
    void parsesDescriptionAndStyle() {
        String json = """
                {
                  "global": {
                    "endzeit": {
                      "description": "Der letzte Abschnitt.",
                      "style": { "shape": "hexagon", "size": 1.4, "border": "#c04040" }
                    }
                  },
                  "individual": {}
                }
                """;

        GraphStageData.Snapshot snap = GraphStageData.fromJson(json);

        assertEquals("Der letzte Abschnitt.", snap.description("endzeit", false));
        assertEquals("hexagon", snap.style("endzeit", false).shape);
        assertEquals(1.4, snap.style("endzeit", false).size);
        assertEquals("#c04040", snap.style("endzeit", false).border);
    }

    @Test
    void unknownStageYieldsNullDescriptionAndEmptyStyle() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("{}");

        assertNull(snap.description("nope", false));
        assertTrue(snap.style("nope", false).isEmpty());
    }

    @Test
    void treesAreSeparateNamespaces() {
        String json = """
                {
                  "global":     { "test": { "description": "global one" } },
                  "individual": { "test": { "description": "individual one" } }
                }
                """;

        GraphStageData.Snapshot snap = GraphStageData.fromJson(json);

        assertEquals("global one", snap.description("test", false));
        assertEquals("individual one", snap.description("test", true));
    }

    @Test
    void descriptionSurvivesARoundTrip() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "description": "line one\\nline two" } } }
                """);

        GraphStageData.Snapshot again = GraphStageData.fromJson(GraphStageData.toJson(snap));

        assertEquals("line one\nline two", again.description("a", false));
    }

    @Test
    void styleSurvivesARoundTrip() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "style": { "shape": "diamond", "checkmark": false } } } }
                """);

        GraphStageData.Snapshot again = GraphStageData.fromJson(GraphStageData.toJson(snap));

        assertEquals("diamond", again.style("a", false).shape);
        assertEquals(Boolean.FALSE, again.style("a", false).checkmark);
    }

    @Test
    void malformedJsonYieldsEmptySnapshot() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("{ broken");

        assertNull(snap.description("a", false));
    }

    @Test
    void settingADescriptionToBlankRemovesTheEntry() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "description": "text" } } }
                """);

        GraphStageData.Snapshot updated = snap.withDescription("a", false, "   ");

        assertNull(updated.description("a", false));
    }

    @Test
    void aFileWithoutStylesStillLoads() {
        String json = """
                { "global": { "endzeit": {
                    "description": "text",
                    "style": { "shape": "hexagon" } } }, "individual": {} }
                """;

        GraphStageData.Snapshot snapshot = GraphStageData.fromJson(json);

        assertEquals("text", snapshot.description("endzeit", false));
        assertEquals("hexagon", snapshot.style("endzeit", false).shape);
        assertNull(snapshot.style("endzeit", false, NodeState.LOCKED).border,
                "no styles block means no per-state override");
    }

    @Test
    void perStateOverrideLayersOnTopOfTheAllStatesOne() {
        String json = """
                { "global": { "endzeit": {
                    "style":  { "shape": "hexagon", "size": 1.4 },
                    "styles": { "locked": { "border": "#C04040", "size": 2.0 } } } },
                  "individual": {} }
                """;

        GraphStageData.Snapshot snapshot = GraphStageData.fromJson(json);

        StageStyle locked = snapshot.style("endzeit", false, NodeState.LOCKED);
        assertEquals("hexagon", locked.shape, "the all-states field must carry through");
        assertEquals("#C04040", locked.border);
        assertEquals(2.0, locked.size, "the per-state field must win");

        StageStyle unlocked = snapshot.style("endzeit", false, NodeState.UNLOCKED);
        assertNull(unlocked.border, "the locked override must not leak into another state");
        assertEquals(1.4, unlocked.size);
    }

    @Test
    void stylesSurviveARoundTrip() {
        String json = """
                { "global": { "endzeit": {
                    "styles": { "reachable": { "checkmark": true } } } },
                  "individual": {} }
                """;

        GraphStageData.Snapshot reparsed =
                GraphStageData.fromJson(GraphStageData.toJson(GraphStageData.fromJson(json)));

        assertEquals(Boolean.TRUE,
                reparsed.style("endzeit", false, NodeState.REACHABLE).checkmark);
    }

    @Test
    void withStyleKeepsTheDescription() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"description\": \"keep me\" } }, \"individual\": {} }");

        GraphStageData.Entry override = new GraphStageData.Entry();
        override.style = new StageStyle();
        override.style.shape = "CIRCLE";

        GraphStageData.Snapshot after = start.withStyle("a", false, override);

        assertEquals("keep me", after.description("a", false));
        assertEquals("CIRCLE", after.style("a", false).shape);
    }

    @Test
    void clearingTheLastFieldDropsTheEntry() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"style\": { \"shape\": \"CIRCLE\" } } }, \"individual\": {} }");

        GraphStageData.Snapshot after = start.withStyle("a", false, new GraphStageData.Entry());

        assertFalse(after.global().containsKey("a"),
                "an entry with neither description nor style must not stay in the file");
    }

    @Test
    void anEntryWithOnlyAPerStateOverrideIsNotEmpty() {
        GraphStageData.Entry entry = new GraphStageData.Entry();
        entry.styles = new StateStyles();
        entry.styles.locked = new StageStyle();
        entry.styles.locked.checkmark = Boolean.TRUE;

        assertFalse(entry.isEmpty());
    }

    @Test
    void aBackgroundBlockSurvivesTheFileAndTheWire() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "eisen": { "background": {
                    "mode": "TEXTURE", "texture": "minecraft:textures/block/iron_block.png", "color": "#1A1410"
                } } }, "individual": {} }
                """);

        GraphStageData.Entry reread = GraphStageData.fromJson(GraphStageData.toJson(snap)).global().get("eisen");
        assertEquals("TEXTURE", reread.background.mode);
        assertEquals("minecraft:textures/block/iron_block.png", reread.background.texture);
        assertEquals("#1A1410", reread.background.color);

        GraphStageData.Entry wired = GraphStageData.entryFromJson(GraphStageData.entryToJson(reread));
        assertEquals("#1A1410", wired.background.color);
    }

    @Test
    void anEntryWithOnlyABackgroundIsKept() {
        GraphStageData.Entry source = new GraphStageData.Entry();
        source.background = new CanvasBackgroundStyle();
        source.background.mode = "SOLID";

        GraphStageData.Snapshot after = GraphStageData.Snapshot.empty().withStyle("a", true, source);

        assertTrue(after.individual().containsKey("a"));
        assertEquals("SOLID", after.individual().get("a").background.mode);
        assertTrue(after.individual().get("a").hasStyles());
    }

    @Test
    void anEmptyBackgroundIsNotWrittenBack() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"description\": \"x\", \"background\": { \"mode\": \"GRID\" } } } }");
        GraphStageData.Entry cleared = new GraphStageData.Entry();
        cleared.background = new CanvasBackgroundStyle();

        GraphStageData.Snapshot after = start.withStyle("a", false, cleared);

        assertNull(after.global().get("a").background);
        assertEquals("x", after.description("a", false));
    }

    @Test
    void changingTheDescriptionKeepsTheBackground() {
        GraphStageData.Snapshot start = GraphStageData.fromJson(
                "{ \"global\": { \"a\": { \"background\": { \"mode\": \"GRID\" } } } }");

        GraphStageData.Snapshot after = start.withDescription("a", false, "text");

        assertEquals("GRID", after.global().get("a").background.mode);
    }
}
