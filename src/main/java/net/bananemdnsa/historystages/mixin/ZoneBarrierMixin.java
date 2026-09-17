package net.bananemdnsa.historystages.mixin;

import java.util.List;

import net.bananemdnsa.historystages.client.ZoneBarrierCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hangs a barrier zone into the movement calculation, so walking into one stops the player the way
 * a block does.
 *
 * <p>There is no hook for this. Extra collision is not something Forge exposes — the world border
 * gets in the same way, hard-coded a few lines further down the same method — and the alternative
 * is correcting the player's position after the fact, which is the thing this exists to get rid
 * of.
 *
 * <p>What the wall is added to is the list of extra collisions {@code collide} has just put
 * together and is about to collide against, not the call it got that list from. The call is the
 * obvious place and the wrong one: Lithium and the performance mods built on it replace it to put
 * the entity lookup off until the movement is known to need one, and a call can only ever be
 * replaced by one mod — whoever is applied second finds nothing to replace and takes the game down
 * on startup. The list is not exclusive that way. Any number of mods can add to it, and it is the
 * same list the collision further down is handed, so the wall stands whether the call above it is
 * still vanilla's or not.
 *
 * <p>Nothing here is required. A mod that rewrites {@code collide} outright leaves this with
 * nowhere to go, and a barrier is not worth a crash over: without it the zone falls back to the
 * push-back it had before, which is the server's and works regardless.
 *
 * <p>Client only. On the server this method is not what decides where a player ends up; the
 * position they report is, and {@code ZoneLockHandler} still checks that.
 */
@Mixin(Entity.class)
public class ZoneBarrierMixin {

    @ModifyVariable(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("STORE"),
            ordinal = 0,
            require = 0)
    private List<VoxelShape> historystages$addZoneWalls(List<VoxelShape> found, Vec3 movement) {
        Entity entity = (Entity) (Object) this;
        return ZoneBarrierCollision.add(entity, entity.getBoundingBox().expandTowards(movement),
                found);
    }
}
