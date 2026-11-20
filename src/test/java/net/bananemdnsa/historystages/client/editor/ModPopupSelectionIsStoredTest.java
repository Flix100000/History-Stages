package net.bananemdnsa.historystages.client.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod-lock chain (Mods tab → structures → biomes → plants) hands each popup's ticks to a
 * callback, and the popup itself writes nothing. A callback that only stores them in edit mode
 * drops every pick made while adding a mod for the first time — which is how the structure and
 * biome steps behaved after the tabs moved onto their lock categories.
 *
 * <p>The screen needs a running client, so this reads its source, like
 * {@code AddonBlockSurvivesEditingTest}: each callback has to store the selection under the mod
 * being added or edited, in both flows.
 */
class ModPopupSelectionIsStoredTest {

    @Test
    void everyModEntryPopupStoresItsSelectionWhenAModIsFirstAdded() throws Exception {
        Path screen = Path.of("src", "main", "java", "net", "bananemdnsa", "historystages",
                "client", "editor", "StageDetailScreen.java");
        assertTrue(Files.exists(screen), "expected to run from the project root");
        String source = Files.readString(screen);

        for (String popup : List.of("modStructurePopup", "modBiomePopup", "modCropPopup")) {
            int start = source.indexOf(popup + " = new ModEntrySelectionPopup(");
            assertTrue(start >= 0, popup + " is no longer built where expected");
            int end = source.indexOf("});", start);
            String callback = source.substring(start, end);
            assertTrue(callback.contains("replaceModSelection(pendingModId, selectedIds)"),
                    popup + " must store its selection for the mod being added, not only in edit mode");
        }
    }

    /**
     * "Skip" means "nothing from this step", not "stop": skipping the structures must still offer
     * the biomes, and skipping the biomes the plants. Only the entity popup did that; the others
     * just closed and ended the chain.
     */
    @Test
    void skippingAStepContinuesWithTheNextOne() throws Exception {
        String screen = Files.readString(Path.of("src", "main", "java", "net", "bananemdnsa",
                "historystages", "client", "editor", "StageDetailScreen.java"));
        assertTrue(skipOf(screen, "modStructurePopup").contains("showModBiomePopup()"),
                "skipping the structures must go on to the biomes");
        assertTrue(skipOf(screen, "modBiomePopup").contains("showModCropPopup()"),
                "skipping the biomes must go on to the plants");
        assertTrue(skipOf(screen, "modCropPopup").contains("editingModId = null"),
                "skipping the last step must end the edit");

        String popup = Files.readString(Path.of("src", "main", "java", "net", "bananemdnsa",
                "historystages", "client", "editor", "widget", "popup", "ModEntrySelectionPopup.java"));
        int skipButton = popup.indexOf("if (mouseX >= skipX && mouseX < skipX + btnW) {");
        assertTrue(skipButton >= 0, "skip button branch not found");
        String branch = popup.substring(skipButton, popup.indexOf("return true;", skipButton));
        assertTrue(branch.contains("onSkip.run()"), "the skip button must run the skip continuation");
    }

    /** The skip continuation is the constructor's last argument, after the confirm callback. */
    private static String skipOf(String source, String popup) {
        int start = source.indexOf(popup + " = new ModEntrySelectionPopup(");
        assertTrue(start >= 0, popup + " is no longer built where expected");
        int confirmEnd = source.indexOf("},", start);
        assertTrue(confirmEnd >= 0, popup + " has no skip continuation after its confirm callback");
        int end = source.indexOf(");", confirmEnd);
        return source.substring(confirmEnd, end);
    }
}
