package net.bananemdnsa.historystages.platform;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a dying entity's drops back so the drop event can be asked about them first.
 *
 * <p>NeoForge builds the list before anything reaches the world and hands it over; vanilla drops
 * straight into the level, item by item, from several methods deep. So the items are caught on
 * their way in, shown to the handlers, and only then added — which is what lets a lock remove one
 * rather than delete it again a tick later in front of the player.
 *
 * <p>One capture at a time, on the server thread, which is where dying happens. Anything dropped
 * while no capture is running goes straight through.
 */
public final class DeathDropCapture {

    private static LivingEntity capturing;
    private static List<ItemEntity> captured;

    private DeathDropCapture() {}

    public static void begin(LivingEntity entity) {
        capturing = entity;
        captured = new ArrayList<>();
    }

    /**
     * True while drops should be collected instead of added.
     *
     * <p>It does not ask which entity: the window is exactly one call of dropAllDeathLoot on the
     * server thread, so every item entity appearing inside it belongs to the one that is dying.
     * Asking the item would not work anyway — its owner field is about who may pick it up, not
     * about where it came from.
     */
    public static boolean isActive() {
        return capturing != null;
    }

    public static void capture(ItemEntity drop) {
        captured.add(drop);
    }

    /** Ends the capture and hands back what was held. Never null, so a caller cannot forget. */
    public static List<ItemEntity> end() {
        List<ItemEntity> drops = captured == null ? List.of() : captured;
        capturing = null;
        captured = null;
        return drops;
    }
}
