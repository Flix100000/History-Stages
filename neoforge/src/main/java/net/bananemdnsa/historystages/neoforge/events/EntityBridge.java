package net.bananemdnsa.historystages.neoforge.events;

import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Entities and mobs: joining, travelling, spawning, dying, taking damage. */
public final class EntityBridge {

    private EntityBridge() {}

    public static void register(IEventBus bus) {
        Bridges.listenCancellable(bus, EntityJoinLevelEvent.class, EntityBridge::joinLevel);
        Bridges.listenCancellable(bus, EntityTeleportEvent.class, EntityBridge::teleport);
        Bridges.listenCancellable(bus, EntityTravelToDimensionEvent.class, EntityBridge::travelToDimension);
        Bridges.listenCancellable(bus, ProjectileImpactEvent.class, EntityBridge::projectileImpact);
        Bridges.listenCancellable(bus, BabyEntitySpawnEvent.class, EntityBridge::babySpawn);
        Bridges.listenCancellable(bus, FinalizeSpawnEvent.class, EntityBridge::finalizeSpawn);
        Bridges.listenCancellable(bus, LivingDeathEvent.class, EntityBridge::death);
        Bridges.listenCancellable(bus, LivingDropsEvent.class, EntityBridge::drops);
        Bridges.listenCancellable(bus, LivingIncomingDamageEvent.class, EntityBridge::incomingDamage);
        Bridges.listen(bus, LivingEquipmentChangeEvent.class, EntityBridge::equipmentChange);
        Bridges.listen(bus, LivingGetProjectileEvent.class, EntityBridge::getProjectile);
        Bridges.listen(bus, MobEffectEvent.Added.class, EntityBridge::effectAdded);
        Bridges.listen(bus, MobSpawnEvent.PositionCheck.class, EntityBridge::positionCheck);
        Bridges.listen(bus, MobSpawnEvent.SpawnPlacementCheck.class, EntityBridge::spawnPlacementCheck);
    }

    private static void joinLevel(EntityJoinLevelEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent(
                    event.getEntity(), event.getLevel()), priority);
        }
    }

    /**
     * One listener for every kind of teleport. An ender pearl becomes the platform's
     * {@code EnderPearl}, everything else the plain event, so a handler on the base class still
     * hears all of them, as it did on NeoForge.
     */
    private static void teleport(EntityTeleportEvent event, EventPriority priority) {
        boolean pearl = event instanceof EntityTeleportEvent.EnderPearl;
        var type = pearl
                ? net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.EnderPearl.class
                : net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.class;
        if (!Bridges.wanted(type, priority)) {
            return;
        }
        var platform = pearl
                ? new net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.EnderPearl(
                        event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ())
                : new net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent(
                        event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ());
        Bridges.post(event, platform, priority);
        event.setTargetX(platform.getTargetX());
        event.setTargetY(platform.getTargetY());
        event.setTargetZ(platform.getTargetZ());
    }

    private static void travelToDimension(EntityTravelToDimensionEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent(
                    event.getEntity(), event.getDimension()), priority);
        }
    }

    private static void projectileImpact(ProjectileImpactEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent(
                    event.getProjectile(), event.getRayTraceResult()), priority);
        }
    }

    private static void babySpawn(BabyEntitySpawnEvent event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent(
                event.getParentA(), event.getParentB(), event.getChild());
        Bridges.post(event, platform, priority);
        if (platform.getChild() != event.getChild()) {
            event.setChild(platform.getChild());
        }
    }

    /**
     * Two separate outcomes, as on NeoForge: cancelling skips the mob's own finalizeSpawn, while
     * {@code spawnCancelled} keeps the mob out of the world.
     */
    private static void finalizeSpawn(FinalizeSpawnEvent event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent(
                event.getEntity(), event.getLevel(), event.getX(), event.getY(), event.getZ(), event.getSpawnType());
        platform.setSpawnCancelled(event.isSpawnCancelled());
        Bridges.post(event, platform, priority);
        if (platform.isSpawnCancelled() != event.isSpawnCancelled()) {
            event.setSpawnCancelled(platform.isSpawnCancelled());
        }
    }

    private static void death(LivingDeathEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent(
                    event.getEntity(), event.getSource()), priority);
        }
    }

    /** The drop collection is the live one, so a handler that removes an item removes the real drop. */
    private static void drops(LivingDropsEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent(
                    event.getEntity(), event.getSource(), event.getDrops()), priority);
        }
    }

    private static void incomingDamage(LivingIncomingDamageEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent.class, priority)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent(
                    event.getEntity(), event.getSource()), priority);
        }
    }

    private static void equipmentChange(LivingEquipmentChangeEvent event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent(
                    event.getEntity(), event.getSlot(), event.getFrom(), event.getTo()), priority);
        }
    }

    private static void getProjectile(LivingGetProjectileEvent event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingGetProjectileEvent.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.LivingGetProjectileEvent(
                event.getEntity(), event.getProjectileWeaponItemStack(), event.getProjectileItemStack());
        EventBus.post(platform, priority);
        if (platform.getProjectileItemStack() != event.getProjectileItemStack()) {
            event.setProjectileItemStack(platform.getProjectileItemStack());
        }
    }

    private static void effectAdded(MobEffectEvent.Added event, EventPriority priority) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobEffectEvent.Added.class, priority)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.living.MobEffectEvent.Added(
                    event.getEntity(), event.getEffectInstance()), priority);
        }
    }

    private static void positionCheck(MobSpawnEvent.PositionCheck event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck(
                event.getEntity(), event.getLevel(), event.getSpawnType(), event.getX(), event.getY(), event.getZ());
        platform.setResult(switch (event.getResult()) {
            case SUCCEED -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.Result.SUCCEED;
            case DEFAULT -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.Result.DEFAULT;
            case FAIL -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL;
        });
        EventBus.post(platform, priority);
        event.setResult(switch (platform.getResult()) {
            case SUCCEED -> MobSpawnEvent.PositionCheck.Result.SUCCEED;
            case DEFAULT -> MobSpawnEvent.PositionCheck.Result.DEFAULT;
            case FAIL -> MobSpawnEvent.PositionCheck.Result.FAIL;
        });
    }

    private static void spawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event, EventPriority priority) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.class, priority)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                event.getEntityType(), event.getLevel(), event.getSpawnType(), event.getPos());
        platform.setResult(switch (event.getResult()) {
            case SUCCEED -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
            case DEFAULT -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
            case FAIL -> net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
        });
        EventBus.post(platform, priority);
        event.setResult(switch (platform.getResult()) {
            case SUCCEED -> MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED;
            case DEFAULT -> MobSpawnEvent.SpawnPlacementCheck.Result.DEFAULT;
            case FAIL -> MobSpawnEvent.SpawnPlacementCheck.Result.FAIL;
        });
    }
}
