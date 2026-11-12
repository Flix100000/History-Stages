package net.bananemdnsa.historystages.neoforge.events;

import net.bananemdnsa.historystages.platform.bus.EventBus;
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

    private static void joinLevel(EntityJoinLevelEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.EntityJoinLevelEvent(
                    event.getEntity(), event.getLevel()));
        }
    }

    /**
     * One listener for every kind of teleport. An ender pearl becomes the platform's
     * {@code EnderPearl}, everything else the plain event, so a handler on the base class still
     * hears all of them, as it did on NeoForge.
     */
    private static void teleport(EntityTeleportEvent event) {
        boolean pearl = event instanceof EntityTeleportEvent.EnderPearl;
        var type = pearl
                ? net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.EnderPearl.class
                : net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.class;
        if (!Bridges.wanted(type)) {
            return;
        }
        var platform = pearl
                ? new net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent.EnderPearl(
                        event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ())
                : new net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent(
                        event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ());
        Bridges.post(event, platform);
        event.setTargetX(platform.getTargetX());
        event.setTargetY(platform.getTargetY());
        event.setTargetZ(platform.getTargetZ());
    }

    private static void travelToDimension(EntityTravelToDimensionEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.EntityTravelToDimensionEvent(
                    event.getEntity(), event.getDimension()));
        }
    }

    private static void projectileImpact(ProjectileImpactEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent(
                    event.getProjectile(), event.getRayTraceResult()));
        }
    }

    private static void babySpawn(BabyEntitySpawnEvent event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.BabyEntitySpawnEvent(
                event.getParentA(), event.getParentB(), event.getChild());
        Bridges.post(event, platform);
        if (platform.getChild() != event.getChild()) {
            event.setChild(platform.getChild());
        }
    }

    /**
     * Two separate outcomes, as on NeoForge: cancelling skips the mob's own finalizeSpawn, while
     * {@code spawnCancelled} keeps the mob out of the world.
     */
    private static void finalizeSpawn(FinalizeSpawnEvent event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.FinalizeSpawnEvent(
                event.getEntity(), event.getLevel(), event.getX(), event.getY(), event.getZ(), event.getSpawnType());
        platform.setSpawnCancelled(event.isSpawnCancelled());
        Bridges.post(event, platform);
        if (platform.isSpawnCancelled() != event.isSpawnCancelled()) {
            event.setSpawnCancelled(platform.isSpawnCancelled());
        }
    }

    private static void death(LivingDeathEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingDeathEvent(
                    event.getEntity(), event.getSource()));
        }
    }

    /** The drop collection is the live one, so a handler that removes an item removes the real drop. */
    private static void drops(LivingDropsEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingDropsEvent(
                    event.getEntity(), event.getSource(), event.getDrops()));
        }
    }

    private static void incomingDamage(LivingIncomingDamageEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent.class)) {
            Bridges.post(event, new net.bananemdnsa.historystages.platform.event.entity.living.LivingIncomingDamageEvent(
                    event.getEntity(), event.getSource()));
        }
    }

    private static void equipmentChange(LivingEquipmentChangeEvent event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.living.LivingEquipmentChangeEvent(
                    event.getEntity(), event.getSlot(), event.getFrom(), event.getTo()));
        }
    }

    private static void getProjectile(LivingGetProjectileEvent event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.LivingGetProjectileEvent.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.LivingGetProjectileEvent(
                event.getEntity(), event.getProjectileWeaponItemStack(), event.getProjectileItemStack());
        EventBus.post(platform);
        if (platform.getProjectileItemStack() != event.getProjectileItemStack()) {
            event.setProjectileItemStack(platform.getProjectileItemStack());
        }
    }

    private static void effectAdded(MobEffectEvent.Added event) {
        if (Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobEffectEvent.Added.class)) {
            EventBus.post(new net.bananemdnsa.historystages.platform.event.entity.living.MobEffectEvent.Added(
                    event.getEntity(), event.getEffectInstance()));
        }
    }

    private static void positionCheck(MobSpawnEvent.PositionCheck event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck(
                event.getEntity(), event.getLevel(), event.getSpawnType(), event.getX(), event.getY(), event.getZ());
        platform.setResult(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.PositionCheck.Result
                .valueOf(event.getResult().name()));
        EventBus.post(platform);
        event.setResult(MobSpawnEvent.PositionCheck.Result.valueOf(platform.getResult().name()));
    }

    private static void spawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!Bridges.wanted(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.class)) {
            return;
        }
        var platform = new net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                event.getEntityType(), event.getLevel(), event.getSpawnType(), event.getPos());
        platform.setResult(net.bananemdnsa.historystages.platform.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result
                .valueOf(event.getResult().name()));
        EventBus.post(platform);
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.valueOf(platform.getResult().name()));
    }
}
