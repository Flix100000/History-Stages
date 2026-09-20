package net.bananemdnsa.historystages.client;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.data.lock.ZoneGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gives a barrier zone something to actually stand in the way, on the machine where it matters.
 *
 * <p>The server can only ever correct: it sees where the player ended up and puts them back, and
 * a correction one tick late is what "teleported back" feels like, however small the step. A wall
 * has to be there while the movement is being worked out, and that happens on the client. So the
 * client stops the player at the zone and the server's push-back never comes up — it stays as the
 * authority for a client that ignores this, which is the only reason it is still there.
 *
 * <p>Block-sized cubes rather than the true shape. A zone is asked block by block whether a
 * position is inside it, so a sphere really is a blocky sphere; a smooth wall would let the player
 * lean into places the server counts as locked, or stop them short of ones it does not.
 */
public final class ZoneBarrierCollision {

    /**
     * How far into a barrier a player has to be before it lets go of them.
     *
     * <p>Deliberately as small as it can be. Big enough that standing flush against the wall never
     * counts — that puts the bounding box exactly on a block boundary and floating point makes
     * "exactly" a coin toss — and no bigger, because the alternative failure is far worse than a
     * player slipping through a wall they were somehow already stuck in. Someone wedged a
     * centimetre deep is held on every axis at once, and the server only hauls out players whose
     * feet are in a locked block, so it would not come to the rescue: they would simply be unable
     * to move, in a spot they cannot see anything wrong with.
     */
    private static final double LODGED = 1.0E-3;

    private ZoneBarrierCollision() {}

    /**
     * The collision shapes of every barrier zone reaching into this movement, on top of whatever
     * the game already found.
     *
     * <p>Only for the player and whatever they are riding. Every other entity on the client is
     * told where it is by the server rather than working it out, so walling them off here would
     * be a fight with the server rather than a barrier.
     */
    public static List<VoxelShape> add(Entity entity, AABB sweep, List<VoxelShape> found) {
        Level level = entity.level();
        if (!level.isClientSide()) return found;
        if (ClientZoneShapes.isEmpty()) return found;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return found;
        if (entity != player && entity != player.getVehicle()) return found;
        // The same exemption the push-back makes: a pack author flying through their own zone to
        // look at it must not be stopped by it.
        if (player.isCreative() || player.isSpectator()) return found;

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        List<VoxelShape> walls = null;
        for (ClientZoneShapes.Visible zone : ClientZoneShapes.get()) {
            if (!zone.barrier()) continue;
            // Somebody already inside — the zone was drawn around them, or they were put there —
            // would be frozen solid rather than walled out. Leave them to the server, which knows
            // where to put them down.
            if (lodgedIn(zone, entity.getBoundingBox(), minY, maxY)) continue;
            walls = wallsIn(zone, sweep, minY, maxY, walls);
        }

        if (walls == null) return found;
        walls.addAll(found);
        return walls;
    }

    private static List<VoxelShape> wallsIn(ClientZoneShapes.Visible zone, AABB sweep,
                                            int minY, int maxY, List<VoxelShape> into) {
        for (int x = Mth.floor(sweep.minX); x <= Mth.floor(sweep.maxX); x++) {
            for (int y = Mth.floor(sweep.minY); y <= Mth.floor(sweep.maxY); y++) {
                for (int z = Mth.floor(sweep.minZ); z <= Mth.floor(sweep.maxZ); z++) {
                    if (!locked(zone, x, y, z, minY, maxY)) continue;
                    if (into == null) into = new ArrayList<>();
                    into.add(Shapes.block().move(x, y, z));
                }
            }
        }
        return into;
    }

    private static boolean lodgedIn(ClientZoneShapes.Visible zone, AABB box, int minY, int maxY) {
        AABB clear = box.deflate(LODGED);
        for (int x = Mth.floor(clear.minX); x <= Mth.floor(clear.maxX); x++) {
            for (int y = Mth.floor(clear.minY); y <= Mth.floor(clear.maxY); y++) {
                for (int z = Mth.floor(clear.minZ); z <= Mth.floor(clear.maxZ); z++) {
                    if (locked(zone, x, y, z, minY, maxY)) return true;
                }
            }
        }
        return false;
    }

    /** An inverted zone holds the world outside its shapes, so the wall is on the other side. */
    private static boolean locked(ClientZoneShapes.Visible zone, int x, int y, int z,
                                  int minY, int maxY) {
        return ZoneGeometry.containsAny(zone.shapes(), x, y, z, minY, maxY) != zone.inverted();
    }
}
