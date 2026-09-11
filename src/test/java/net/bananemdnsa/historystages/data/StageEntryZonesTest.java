package net.bananemdnsa.historystages.data;

import java.util.List;

import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageEntryZonesTest {

    @Test
    void zonesDefaultToAnEmptyListRatherThanNull() {
        assertNotNull(new StageEntry().getZones());
        assertTrue(new StageEntry().getZones().isEmpty());
    }

    @Test
    void zonesSurviveBeingSet() {
        ZoneEntry zone = new ZoneEntry();
        zone.setName("Krater");
        zone.setDimension("minecraft:overworld");
        zone.setShapes(List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false)));

        StageEntry stage = new StageEntry();
        stage.setZones(List.of(zone));

        assertEquals(1, stage.getZones().size());
        assertEquals("Krater", stage.getZones().get(0).getName());
        assertEquals(1, stage.getZones().get(0).getShapes().size());
    }

    /** A null list clears rather than blowing up — every neighbouring setter behaves this way. */
    @Test
    void settingNullClearsTheList() {
        StageEntry stage = new StageEntry();
        stage.setZones(List.of(new ZoneEntry()));
        stage.setZones(null);
        assertTrue(stage.getZones().isEmpty());
    }

    /**
     * {@code copy()} names every field by hand, so a new one is dropped unless it is added — and
     * the editor's save path starts from a copy. Without this, saving any stage from the editor
     * would silently erase zones that were typed into the file by hand.
     */
    @Test
    void copyCarriesZones() {
        ZoneEntry zone = new ZoneEntry();
        zone.setName("Krater");
        zone.setShapes(List.of(ZoneShape.cube(0, 0, 0, 10, 10, 10, false)));

        StageEntry original = new StageEntry();
        original.setZones(List.of(zone));

        StageEntry copy = original.copy();
        assertEquals(1, copy.getZones().size());
        assertEquals("Krater", copy.getZones().get(0).getName());
        assertEquals(1, copy.getZones().get(0).getShapes().size());
    }

    /**
     * And the copy must be its own. A shared {@link ZoneEntry} would mean editing a zone in the
     * editor also rewrites the stage the editor is supposed to be able to discard.
     */
    @Test
    void copyDoesNotShareZoneObjects() {
        ZoneEntry zone = new ZoneEntry();
        zone.setName("vorher");

        StageEntry original = new StageEntry();
        original.setZones(List.of(zone));

        StageEntry copy = original.copy();
        copy.getZones().get(0).setName("nachher");
        copy.getZones().get(0).getRules().setBlockLeftClick(false);

        assertEquals("vorher", original.getZones().get(0).getName());
        assertTrue(original.getZones().get(0).getRules().isBlockLeftClick(),
                "the rules block must be its own too, not shared");
    }
}
