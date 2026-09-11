package net.bananemdnsa.historystages.mixin;

import java.util.List;

import net.bananemdnsa.historystages.client.ZoneBarrierCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hangs a barrier zone into the movement calculation, so walking into one stops the player the way
 * a block does.
 *
 * <p>There is no hook for this. Extra collision is not something NeoForge exposes — the world
 * border gets in the same way, hard-coded a few lines further down the same method — and the
 * alternative is correcting the player's position after the fact, which is the thing this exists
 * to get rid of.
 *
 * <p>Client only. On the server this method is not what decides where a player ends up; the
 * position they report is, and {@code ZoneLockHandler} still checks that.
 */
@Mixin(Entity.class)
public class ZoneBarrierMixin {

    @Redirect(
            method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions"
                            + "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)"
                            + "Ljava/util/List;"))
    private List<VoxelShape> historystages$addZoneWalls(Level level, Entity entity, AABB sweep) {
        return ZoneBarrierCollision.add(entity, sweep, level.getEntityCollisions(entity, sweep));
    }
}
