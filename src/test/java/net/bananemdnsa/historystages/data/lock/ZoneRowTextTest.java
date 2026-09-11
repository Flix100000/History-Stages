package net.bananemdnsa.historystages.data.lock;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a zone and its shapes read like in a list.
 *
 * <p>Worth pinning because the tab and the zone screen both show it: if the two ever disagree
 * about the same zone, the one you clicked and the one you are editing look like different things.
 */
class ZoneRowTextTest {

    @Test
    void aCubeShowsBothCorners() {
        assertEquals("1/2/3 → 4/5/6",
                ZoneRowText.describeShape(ZoneShape.cube(1, 2, 3, 4, 5, 6, false)));
    }

    /** Full height replaces the numbers, so the row has to say so rather than show stale ones. */
    @Test
    void fullHeightIsMarked() {
        String text = ZoneRowText.describeShape(ZoneShape.cube(1, 2, 3, 4, 5, 6, true));
        assertTrue(text.contains("↕"), "full height is not marked: " + text);
    }

    @Test
    void aSphereShowsItsCentreAndRadius() {
        assertEquals("10/20/30 r7", ZoneRowText.describeShape(ZoneShape.sphere(10, 20, 30, 7)));
    }

    @Test
    void aCylinderShowsItsHeightUnlessItSpansTheWorld() {
        assertEquals("1/2/3 r4 h9",
                ZoneRowText.describeShape(ZoneShape.cylinder(1, 2, 3, 4, 9, false)));
        assertEquals("1/2/3 r4 ↕",
                ZoneRowText.describeShape(ZoneShape.cylinder(1, 2, 3, 4, 9, true)));
    }

    @Test
    void aZoneShowsItsWorldAndShapeCount() {
        ZoneEntry zone = new ZoneEntry();
        zone.setDimension("minecraft:overworld");
        zone.setShapes(List.of(
                ZoneShape.cube(0, 0, 0, 1, 1, 1, false),
                ZoneShape.sphere(0, 0, 0, 5)));

        assertEquals("minecraft:overworld · 2", ZoneRowText.describeZone(zone));
    }

    /**
     * A zone nobody has marked out yet is a normal state, not an error — it is what a freshly
     * created one looks like. The row must not claim it covers anything.
     */
    @Test
    void aZoneWithoutShapesDoesNotClaimACount() {
        ZoneEntry zone = new ZoneEntry();
        zone.setDimension("minecraft:overworld");
        assertEquals("minecraft:overworld", ZoneRowText.describeZone(zone));
    }

    @Test
    void nullsAreEmptyRatherThanACrash() {
        assertEquals("", ZoneRowText.describeShape(null));
        assertEquals("", ZoneRowText.describeZone(null));
        assertTrue(ZoneRowText.summariseRules(null).isEmpty());
    }

    /**
     * Five switches are on for every new zone — the four protections and the message. Listing
     * them would fill the line with the one thing that never tells two zones apart.
     */
    @Test
    void aFreshZoneSummarisesToNothing() {
        assertTrue(ZoneRowText.summariseRules(new ZoneEntry()).isEmpty());
    }

    /** A zone that says nothing when you walk into it is the surprising one, so that is said. */
    @Test
    void aSilencedMessageIsMentioned() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().getMessage().setEnabled(false);
        assertTrue(ZoneRowText.summariseRules(zone)
                .contains("editor.historystages.zone.summary.silent"));
    }

    @Test
    void theLoudRulesComeFirst() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().setBarrier(true);
        zone.getRules().getDamage().setEnabled(true);
        zone.getRules().setShowBorder(true);

        List<String> keys = ZoneRowText.summariseRules(zone);

        assertEquals("editor.historystages.zone.summary.damage", keys.get(0));
        assertEquals("editor.historystages.zone.summary.barrier", keys.get(1));
    }

    /** Inverting changes where the zone applies, not what it does — it has to be said. */
    @Test
    void invertedIsMentioned() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().setInverted(true);
        assertTrue(ZoneRowText.summariseRules(zone)
                .contains("editor.historystages.zone.summary.inverted"));
    }

    /** Turning protection off is the surprising state, so that is the one worth reporting. */
    @Test
    void protectionIsMentionedOnlyWhenItIsMissing() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().setBlockExplosions(false);

        List<String> keys = ZoneRowText.summariseRules(zone);

        assertTrue(keys.contains("editor.historystages.zone.summary.allow_explosions"), keys.toString());
        assertFalse(keys.contains("editor.historystages.zone.summary.allow_right_click"), keys.toString());
    }

    /**
     * The rail counts switches; the summary describes effect. The two deliberately disagree about
     * the sight switches, so both are pinned here side by side.
     */
    @Test
    void aFreshZoneCountsOneEffectAndFourProtections() {
        ZoneEntry zone = new ZoneEntry();
        assertEquals(1, ZoneRowText.countEffects(zone), "message is on for a new zone");
        assertEquals(4, ZoneRowText.countProtection(zone));
        assertEquals(0, ZoneRowText.countDisplay(zone));
    }

    @Test
    void barrierAndSpawnsCountAsProtection() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().setBarrier(true);
        zone.getRules().setBlockSpawns(true);
        assertEquals(6, ZoneRowText.countProtection(zone));
    }

    @Test
    void theSightSwitchesCountSeparatelyEvenThoughTheySummariseAsOne() {
        ZoneEntry zone = new ZoneEntry();
        zone.getRules().setShowBorder(true);
        zone.getRules().setShowOverlay(true);

        assertEquals(2, ZoneRowText.countDisplay(zone));
        assertEquals(List.of("editor.historystages.zone.summary.visible"),
                ZoneRowText.summariseRules(zone));
    }

    @Test
    void nullCountsAsNothing() {
        assertEquals(0, ZoneRowText.countEffects(null));
        assertEquals(0, ZoneRowText.countProtection(null));
        assertEquals(0, ZoneRowText.countDisplay(null));
    }

    /** Both sight switches mean the same thing to a reader: the zone is not a secret. */
    @Test
    void theTwoSightSwitchesCollapseIntoOneWord() {
        ZoneEntry border = new ZoneEntry();
        border.getRules().setShowBorder(true);
        ZoneEntry both = new ZoneEntry();
        both.getRules().setShowBorder(true);
        both.getRules().setShowOverlay(true);

        assertEquals(List.of("editor.historystages.zone.summary.visible"),
                ZoneRowText.summariseRules(border));
        assertEquals(ZoneRowText.summariseRules(border), ZoneRowText.summariseRules(both));
    }
}
