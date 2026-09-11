package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stage file is a contract. These tests pin what a written zone looks like and, more
 * importantly, that reading and re-writing one loses nothing.
 */
class ZoneEntryListAdapterTest {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<List<ZoneEntry>>() {}.getType(),
                    new ZoneEntryListAdapter())
            .create();

    private static List<ZoneEntry> read(String json) {
        return GSON.fromJson(json, new TypeToken<List<ZoneEntry>>() {}.getType());
    }

    private static String write(List<ZoneEntry> zones) {
        return GSON.toJson(zones, new TypeToken<List<ZoneEntry>>() {}.getType());
    }

    @Test
    void readsACubeZone() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Krater", "dimension": "minecraft:the_nether",
               "shapes": [{ "type": "cube", "from": [1,2,3], "to": [4,5,6], "full_height": true }],
               "rules": { "damage": { "enabled": true, "amount": 2.0, "interval": 40 } } }]
            """);

        assertEquals(1, zones.size());
        ZoneEntry zone = zones.get(0);
        assertEquals("Krater", zone.getName());
        assertEquals("minecraft:the_nether", zone.getDimension());
        assertEquals(1, zone.getShapes().size());

        ZoneShape shape = zone.getShapes().get(0);
        assertEquals(ZoneShapeType.CUBE, shape.type());
        assertEquals(1, shape.fromX());
        assertEquals(6, shape.toZ());
        assertTrue(shape.fullHeight());

        assertTrue(zone.getRules().getDamage().isEnabled());
        assertEquals(2.0, zone.getRules().getDamage().getAmount());
        assertEquals(40, zone.getRules().getDamage().getInterval());
    }

    @Test
    void readsSphereAndCylinder() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld", "shapes": [
                 { "type": "sphere", "center": [10,20,30], "radius": 7 },
                 { "type": "cylinder", "center": [1,2,3], "radius": 4, "height": 9 }] }]
            """);

        List<ZoneShape> shapes = zones.get(0).getShapes();
        assertEquals(ZoneShapeType.SPHERE, shapes.get(0).type());
        assertEquals(7, shapes.get(0).radius());
        assertEquals(20, shapes.get(0).fromY());

        assertEquals(ZoneShapeType.CYLINDER, shapes.get(1).type());
        assertEquals(4, shapes.get(1).radius());
        assertEquals(9, shapes.get(1).height());
    }

    /** A hand-written file with no rules block must not blow up. */
    @Test
    void missingRulesBecomeDefaults() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld", "shapes": [] }]
            """);
        ZoneRules rules = zones.get(0).getRules();
        assertTrue(rules.isBlockRightClick(), "right click defaults to blocked");
        assertFalse(rules.getDamage().isEnabled(), "damage defaults to off");
        assertFalse(rules.isBarrier(), "the round 2 fields default to off");
    }

    /** The five round 2 fields survive round 1 untouched — that is why they exist already. */
    @Test
    void roundTwoFieldsSurviveARoundTrip() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld", "shapes": [],
               "rules": { "barrier": true, "block_spawns": true, "inverted": true,
                          "show_border": true, "show_overlay": true } }]
            """);
        assertTrue(zones.get(0).getRules().isBarrier());

        ZoneRules rules = read(write(zones)).get(0).getRules();
        assertTrue(rules.isBarrier());
        assertTrue(rules.isBlockSpawns());
        assertTrue(rules.isInverted());
        assertTrue(rules.isShowBorder());
        assertTrue(rules.isShowOverlay());
    }

    /** Shapes must come back as the same shapes, not as a pile of defaults. */
    @Test
    void shapesSurviveARoundTrip() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld", "shapes": [
                 { "type": "cube", "from": [1,2,3], "to": [4,5,6], "full_height": true },
                 { "type": "sphere", "center": [10,20,30], "radius": 7 },
                 { "type": "cylinder", "center": [1,2,3], "radius": 4, "height": 9 }] }]
            """);

        List<ZoneShape> shapes = read(write(zones)).get(0).getShapes();
        assertEquals(3, shapes.size());
        assertEquals(ZoneShape.cube(1, 2, 3, 4, 5, 6, true), shapes.get(0));
        assertEquals(ZoneShape.sphere(10, 20, 30, 7), shapes.get(1));
        assertEquals(ZoneShape.cylinder(1, 2, 3, 4, 9, false), shapes.get(2));
    }

    /**
     * Anything the adapter does not know must survive being written back.
     *
     * <p>Rebuilding an object instead of amending the one that was read is how unknown keys get
     * dropped silently — addon data among them. That has happened twice in this repo, in the same
     * chain both times.
     */
    @Test
    void unknownFieldsSurviveARoundTrip() {
        String written = write(read("""
            [{ "name": "Z", "dimension": "minecraft:overworld", "shapes": [],
               "some_addon_key": { "kept": 42 } }]
            """));
        assertTrue(written.contains("some_addon_key"), "unknown key was dropped: " + written);
        assertTrue(written.contains("42"), "unknown value was dropped: " + written);
    }

    /** A typo in the shape type must not make the whole file unreadable. */
    @Test
    void anUnknownShapeTypeFallsBackToCube() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld",
               "shapes": [{ "type": "pyramid", "from": [0,0,0], "to": [1,1,1] }] }]
            """);
        assertEquals(ZoneShapeType.CUBE, zones.get(0).getShapes().get(0).type());
    }

    /** An entry that is not an object at all is skipped, not fatal. */
    @Test
    void junkEntriesAreSkipped() {
        List<ZoneEntry> zones = read("""
            [ "nonsense", 5, { "name": "Z", "dimension": "minecraft:overworld", "shapes": [] } ]
            """);
        assertEquals(1, zones.size());
        assertEquals("Z", zones.get(0).getName());
    }

    /** A cube missing one of its two corners describes nothing and is dropped, not guessed at. */
    @Test
    void aCubeWithoutBothCornersIsDropped() {
        List<ZoneEntry> zones = read("""
            [{ "name": "Z", "dimension": "minecraft:overworld",
               "shapes": [{ "type": "cube", "from": [0,0,0] }] }]
            """);
        assertTrue(zones.get(0).getShapes().isEmpty());
    }
}
