package net.bananemdnsa.historystages.data.graph;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphStylePresetsTest {

    private static final String FILE = """
            {
              "presets": {
                "boss": {
                  "name": "Boss",
                  "style": { "shape": "HEXAGON", "size": 1.6, "fill": "#AA3232" },
                  "styles": { "locked": { "fill": "#552222", "border": "#FF0000" } }
                }
              },
              "global": {
                "iron_age": { "preset": "boss", "style": { "border": "#4A7DFF" } },
                "stone_age": { "preset": "boss" },
                "plain": { "style": { "shape": "CIRCLE" } }
              },
              "individual": {
                "my_path": { "preset": "boss" }
              }
            }
            """;

    private static GraphStageData.Preset preset(String name, String shape) {
        GraphStageData.Preset p = new GraphStageData.Preset();
        p.name = name;
        p.style = new StageStyle();
        p.style.shape = shape;
        return p;
    }

    @Test
    void readsPresetsAndReferences() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        assertEquals("Boss", snap.presets().get("boss").name);
        assertEquals("boss", snap.tree(false).get("iron_age").preset);
    }

    @Test
    void presetsAndReferencesSurviveARoundTrip() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(GraphStageData.toJson(GraphStageData.fromJson(FILE)));

        assertEquals("HEXAGON", snap.presets().get("boss").style.shape);
        assertEquals("#552222", snap.presets().get("boss").styles.locked.fill);
        assertEquals("boss", snap.tree(true).get("my_path").preset);
    }

    @Test
    void aFileWithoutPresetsStillLoads() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "style": { "shape": "CIRCLE" } } }, "individual": {} }
                """);

        assertTrue(snap.presets().isEmpty());
        assertEquals("CIRCLE", snap.style("a", false, NodeState.UNLOCKED).shape);
    }

    @Test
    void theStageInheritsWhatThePresetSets() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        StageStyle style = snap.style("stone_age", false, NodeState.UNLOCKED);

        assertEquals("HEXAGON", style.shape);
        assertEquals(1.6, style.size);
        assertEquals("#AA3232", style.fill);
    }

    @Test
    void thePresetsPerStateValuesApplyInThatState() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        assertEquals("#552222", snap.style("stone_age", false, NodeState.LOCKED).fill);
        assertEquals("#AA3232", snap.style("stone_age", false, NodeState.UNLOCKED).fill);
    }

    @Test
    void theStagesOwnValueWinsEvenOverThePresetsPerStateValue() {
        // The preset sets a red border for locked; the stage says blue for every state.
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        assertEquals("#4A7DFF", snap.style("iron_age", false, NodeState.LOCKED).border);
        assertEquals("HEXAGON", snap.style("iron_age", false, NodeState.LOCKED).shape);
    }

    @Test
    void aMissingPresetIsIgnored() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson("""
                { "global": { "a": { "preset": "gone", "style": { "border": "#123456" } } } }
                """);

        StageStyle style = snap.style("a", false, NodeState.UNLOCKED);

        assertNull(style.shape);
        assertEquals("#123456", style.border);
    }

    @Test
    void anEntryWithOnlyAPresetIsKept() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        GraphStageData.Entry entry = snap.tree(false).get("stone_age");

        assertFalse(entry.isEmpty());
        assertTrue(entry.hasStyles());
        assertTrue(GraphStageData.toJson(snap).contains("stone_age"));
    }

    @Test
    void copyingStylesCarriesThePreset() {
        GraphStageData.Entry copy = GraphStageData.fromJson(FILE).tree(false).get("iron_age").copyStyles();

        assertEquals("boss", copy.preset);
        assertEquals("boss", GraphStageData.entryFromJson(GraphStageData.entryToJson(copy)).preset);
    }

    @Test
    void withStyleTakesThePresetFromTheSource() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);
        GraphStageData.Entry source = new GraphStageData.Entry();
        source.preset = null;

        GraphStageData.Snapshot after = snap.withStyle("stone_age", false, source);

        assertNull(after.tree(false).get("stone_age"), "nothing left, so the entry goes");
    }

    @Test
    void changingTheDescriptionKeepsThePreset() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE).withDescription("stone_age", false, "Text");

        assertEquals("boss", snap.tree(false).get("stone_age").preset);
    }

    @Test
    void deletingAPresetRemovesEveryReferenceAndEmptyEntries() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE).withoutPreset("boss");

        assertFalse(snap.presets().containsKey("boss"));
        assertNull(snap.tree(false).get("iron_age").preset);
        assertEquals("#4A7DFF", snap.tree(false).get("iron_age").style.border, "own values stay");
        assertNull(snap.tree(false).get("stone_age"), "an entry that only held the preset goes");
        assertNull(snap.tree(true).get("my_path"));
        assertNotNull(snap.tree(false).get("plain"));
    }

    @Test
    void assigningSetsAndClearsThePreset() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE)
                .withPreset("side", preset("Side path", "CIRCLE"));

        GraphStageData.Snapshot assigned = snap.withAssignedPreset("plain", false, "side");
        assertEquals("side", assigned.tree(false).get("plain").preset);
        assertEquals("CIRCLE", assigned.tree(false).get("plain").style.shape, "own values stay");

        GraphStageData.Snapshot cleared = assigned.withAssignedPreset("stone_age", false, null);
        assertNull(cleared.tree(false).get("stone_age"));
    }

    @Test
    void assigningToAStageWithoutAnEntryCreatesOne() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE).withAssignedPreset("new_one", true, "boss");

        assertEquals("boss", snap.tree(true).get("new_one").preset);
    }

    @Test
    void countsUsersAcrossBothTrees() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        assertEquals(3, snap.usageCount("boss"));
        assertEquals(0, snap.usageCount("nobody"));
    }

    @Test
    void replacingAPresetKeepsItsId() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE).withPreset("boss", preset("Big boss", "DIAMOND"));

        assertEquals("Big boss", snap.presets().get("boss").name);
        assertEquals("DIAMOND", snap.style("stone_age", false, NodeState.UNLOCKED).shape);
    }

    @Test
    void idsComeFromTheNameAndNeverCollide() {
        assertEquals("boss_stage", GraphStageData.newPresetId("Boss Stage", Set.of()));
        assertEquals("boss_2", GraphStageData.newPresetId("Boss", Set.of("boss")));
        assertEquals("boss_3", GraphStageData.newPresetId("BOSS", Set.of("boss", "boss_2")));
        assertEquals("nebenpfad", GraphStageData.newPresetId("Nebenpfad!", Set.of()));
        assertEquals("preset", GraphStageData.newPresetId("ÄÖÜ", Set.of()));
        assertEquals("preset_2", GraphStageData.newPresetId("", Set.of("preset")));
    }

    @Test
    void flatteningKeepsTheLookInEveryState() {
        // "Save as preset" turns preset + own values into one new preset. Every state must look
        // exactly as before — including the preset's per-state value the stage's all-states
        // value used to beat.
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);
        GraphStageData.Entry stage = snap.tree(false).get("iron_age");

        GraphStageData.Preset flat = GraphStageData.Preset.flatten("Blue boss",
                snap.presets().get(stage.preset), stage.style, stage.styles);

        for (NodeState state : NodeState.values()) {
            StageStyle before = snap.style("iron_age", false, state);
            StageStyle after = flat.style(state);
            for (String leaf : StageStyleFields.LEAVES) {
                assertEquals(StageStyleFields.get(before, leaf), StageStyleFields.get(after, leaf),
                        state + "." + leaf);
            }
        }
        assertEquals("Blue boss", flat.name);
        assertEquals("#4A7DFF", flat.style.border, "the stage's value lands in the all-states block");
    }

    @Test
    void flatteningWithoutAPresetCopiesTheOwnValues() {
        StageStyle own = new StageStyle();
        own.shape = "CIRCLE";

        GraphStageData.Preset flat = GraphStageData.Preset.flatten("Round", null, own, null);

        assertEquals("CIRCLE", flat.style(NodeState.LOCKED).shape);
    }

    @Test
    void namesAreUniqueIgnoringCase() {
        GraphStageData.Snapshot snap = GraphStageData.fromJson(FILE);

        assertTrue(snap.presetNameTaken(" boss ", null));
        assertFalse(snap.presetNameTaken("Boss", "boss"), "renaming to its own name is fine");
        assertFalse(snap.presetNameTaken("Side", null));
    }
}
