package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * How a zone and its shapes read in a list.
 *
 * <p>Its own class, and free of Minecraft, because it is the one part of the zone editor that can
 * be proven without starting the game — and because the tab and the zone screen have to say the
 * same thing about the same zone.
 *
 * <p>Coordinates rather than lang keys: a shape line is numbers, and numbers do not translate. The
 * words around them are added by the caller, which has the font and the language.
 */
public final class ZoneRowText {

    private static final String SUMMARY = "editor.historystages.zone.summary.";

    private ZoneRowText() {}

    /** {@code "100/64/100 → 150/90/150"} and the like, for one shape. */
    public static String describeShape(ZoneShape shape) {
        if (shape == null) return "";
        return switch (shape.type()) {
            case CUBE -> point(shape.fromX(), shape.fromY(), shape.fromZ())
                    + " → " + point(shape.toX(), shape.toY(), shape.toZ())
                    + (shape.fullHeight() ? " ↕" : "");
            case SPHERE -> point(shape.fromX(), shape.fromY(), shape.fromZ())
                    + " r" + shape.radius();
            case CYLINDER -> point(shape.fromX(), shape.fromY(), shape.fromZ())
                    + " r" + shape.radius()
                    + (shape.fullHeight() ? " ↕" : " h" + shape.height());
        };
    }

    /**
     * The summary shown for a whole zone: how many shapes, or that it has none yet.
     *
     * <p>A zone without shapes is legal — it is what a freshly created one looks like — so it gets
     * said plainly rather than shown as an empty row somebody reads as a bug.
     */
    public static String describeZone(ZoneEntry zone) {
        if (zone == null) return "";
        List<ZoneShape> shapes = zone.getShapes();
        if (shapes.isEmpty()) return zone.getDimension();
        return zone.getDimension() + " · " + shapes.size();
    }

    /**
     * What a zone does, as lang keys, loudest first.
     *
     * <p>Keys rather than words, for the reason the class doc gives: the caller has the font and
     * the language, this has neither.
     *
     * <p>A rule standing at its default is skipped, and the deviation is what gets said. Five
     * switches are on for a new zone — the four protections and the message — so listing them
     * would fill the line with the one thing that never tells two zones apart; turning one
     * <em>off</em> is the surprise. Both sight switches collapse into one word, because to a
     * reader they mean the same thing: the zone is not a secret.
     */
    public static List<String> summariseRules(ZoneEntry zone) {
        if (zone == null) return List.of();
        ZoneRules rules = zone.getRules();
        List<String> keys = new ArrayList<>();

        if (rules.getDamage().isEnabled()) keys.add(SUMMARY + "damage");
        if (rules.isBarrier()) keys.add(SUMMARY + "barrier");
        if (rules.getEffects().isEnabled()) keys.add(SUMMARY + "effects");
        if (rules.isBlockSpawns()) keys.add(SUMMARY + "block_spawns");
        if (rules.isInverted()) keys.add(SUMMARY + "inverted");
        if (rules.isShowBorder() || rules.isShowOverlay()) keys.add(SUMMARY + "visible");

        if (!rules.getMessage().isEnabled()) keys.add(SUMMARY + "silent");
        if (!rules.isBlockRightClick()) keys.add(SUMMARY + "allow_right_click");
        if (!rules.isBlockLeftClick()) keys.add(SUMMARY + "allow_left_click");
        if (!rules.isBlockProjectiles()) keys.add(SUMMARY + "allow_projectiles");
        if (!rules.isBlockExplosions()) keys.add(SUMMARY + "allow_explosions");

        return keys;
    }

    /**
     * How many of the three switches that act on a player standing inside are on.
     *
     * <p>For the section rail, which counts switches where {@link #summariseRules} describes
     * effect. The two are allowed to disagree, and over the sight switches they do.
     */
    public static int countEffects(ZoneEntry zone) {
        if (zone == null) return 0;
        ZoneRules rules = zone.getRules();
        return count(rules.getMessage().isEnabled(), rules.getDamage().isEnabled(),
                rules.getEffects().isEnabled());
    }

    /** How many of the six switches that keep a player out, or intact, are on. */
    public static int countProtection(ZoneEntry zone) {
        if (zone == null) return 0;
        ZoneRules rules = zone.getRules();
        return count(rules.isBlockRightClick(), rules.isBlockLeftClick(),
                rules.isBlockProjectiles(), rules.isBlockExplosions(),
                rules.isBarrier(), rules.isBlockSpawns());
    }

    /** How many of the two sight switches are on. Counted apart, unlike in the summary. */
    public static int countDisplay(ZoneEntry zone) {
        if (zone == null) return 0;
        ZoneRules rules = zone.getRules();
        return count(rules.isShowBorder(), rules.isShowOverlay());
    }

    private static int count(boolean... values) {
        int on = 0;
        for (boolean value : values) {
            if (value) on++;
        }
        return on;
    }

    private static String point(int x, int y, int z) {
        return x + "/" + y + "/" + z;
    }
}
