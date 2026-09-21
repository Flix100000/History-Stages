package net.bananemdnsa.historystages.client.cache;

import java.util.List;

import net.bananemdnsa.historystages.data.lock.ZoneShape;

/**
 * The locked zones near this player that the client is allowed to know about.
 *
 * <p>Deliberately not "the locked zones". A zone gets here for one of two reasons only: it asks to
 * be drawn, or it is a barrier and the client has to stop the player at it. A zone that is neither
 * never crosses the wire, so an area meant as a surprise stays one even against somebody reading
 * their own traffic.
 */
public final class ClientZoneShapes {

    /**
     * One zone the server is willing to tell this client about, and why.
     *
     * <p>{@code inverted} travels with it because the client cannot work it out: for an inverted
     * zone the shapes mark the <em>allowed</em> area, so a renderer without this flag would tint
     * the screen in exactly the one place the player is meant to be.
     */
    public record Visible(boolean border, boolean overlay, boolean barrier, boolean inverted,
                          List<ZoneShape> shapes) {}

    private static volatile List<Visible> zones = List.of();

    private ClientZoneShapes() {}

    public static void update(List<Visible> next) {
        zones = next == null ? List.of() : List.copyOf(next);
    }

    public static List<Visible> get() {
        return zones;
    }

    public static boolean isEmpty() {
        return zones.isEmpty();
    }

    public static void clear() {
        zones = List.of();
    }
}
