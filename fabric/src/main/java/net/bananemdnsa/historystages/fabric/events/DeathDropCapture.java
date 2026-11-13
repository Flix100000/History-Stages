package net.bananemdnsa.historystages.fabric.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Holds a dying entity's drops back so the drop event can be asked about them first.
 *
 * <p>NeoForge builds the list before anything reaches the world and hands it over; vanilla drops
 * straight into the level, item by item, from several methods deep. So the items are caught on
 * their way in ({@code EntityJoinLevelMixin}), shown to the handlers, and only then added
 * ({@code LivingDropsMixin}), which lets a lock remove one rather than delete it again a tick later
 * in front of the player.
 *
 * <p>On the server thread, which is where dying happens. Anything dropped while no capture is
 * running goes straight through. Captures nest: a death inside a death (a drop that kills, a loot
 * function that hurts) keeps its own list, and the outer one carries on with its own afterwards.
 */
public final class DeathDropCapture {

    private record Capture(LivingEntity entity, List<ItemEntity> drops) {}

    private static final Deque<Capture> OPEN = new ArrayDeque<>();

    private DeathDropCapture() {}

    public static void begin(LivingEntity entity) {
        OPEN.push(new Capture(entity, new ArrayList<>()));
    }

    /**
     * True while drops should be collected instead of added. It does not ask which entity: the
     * window is exactly one dropAllDeathLoot call on the server thread, and an item's owner field
     * says who may pick it up, not where it came from.
     */
    public static boolean isActive() {
        return !OPEN.isEmpty();
    }

    public static void capture(ItemEntity drop) {
        OPEN.peek().drops().add(drop);
    }

    /** Ends the innermost capture and hands back what it held. Never null. */
    public static List<ItemEntity> end() {
        Capture capture = OPEN.poll();
        return capture == null ? List.of() : capture.drops();
    }
}
