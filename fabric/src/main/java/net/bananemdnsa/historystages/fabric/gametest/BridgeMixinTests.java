package net.bananemdnsa.historystages.fabric.gametest;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.AnvilUpdateEvent;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.bananemdnsa.historystages.platform.event.entity.EntityTeleportEvent;
import net.bananemdnsa.historystages.platform.event.entity.ProjectileImpactEvent;
import net.bananemdnsa.historystages.platform.event.entity.player.AdvancementEvent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The mixin-fired events whose firing point decides what a handler can do: advancement earned
 * (once, on completion), projectile impact (before any subclass work) and pearl landing (a cancel
 * removes the pearl). Each cancelling test has an uncancelled twin.
 *
 * <p>Vanilla calls {@code hitTargetOrDeflectSelf} and {@code onHit} from the projectile's tick;
 * both are protected, so the tests call them by reflection with a hit at a known spot.
 */
public class BridgeMixinTests {

    private static final BlockPos POS = new BlockPos(1, 2, 1);

    @GameTest(skyAccess = true)
    public void advancementEarnedFiresOnceOnCompletion(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        AdvancementHolder advancement = Advancement.Builder.advancement()
                .display(Items.STONE, Component.literal("test"), Component.literal("test"), null,
                        AdvancementType.TASK, false, false, false)
                .addCriterion("first", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .addCriterion("second", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .build(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "gametest/two_criteria"));
        AtomicInteger earned = new AtomicInteger();
        Consumer<AdvancementEvent.AdvancementEarnEvent> counter = event -> {
            if (event.getEntity() == player && event.getAdvancement() == advancement) {
                earned.incrementAndGet();
            }
        };
        EventBus.addListener(AdvancementEvent.AdvancementEarnEvent.class, counter);
        try {
            player.getAdvancements().award(advancement, "first");
            helper.assertTrue(earned.get() == 0, "earned after one of two criteria: " + earned);
            player.getAdvancements().award(advancement, "second");
            helper.assertTrue(earned.get() == 1, "expected one earned event on completion, got " + earned);
        } finally {
            EventBus.unregister(counter);
        }
        helper.succeed();
    }

    /** A large fireball explodes and discards itself in its own onHit, after calling super. */
    @GameTest(skyAccess = true)
    public void cancelledImpactStopsTheFireballsOwnHit(GameTestHelper helper) {
        LargeFireball fireball = helper.spawn(EntityTypes.FIREBALL, POS);
        Consumer<ProjectileImpactEvent> canceller = event -> {
            if (event.getProjectile() == fireball) {
                event.setCanceled(true);
            }
        };
        EventBus.addListener(ProjectileImpactEvent.class, canceller);
        try {
            hitBlockBelow(helper, fireball);
        } finally {
            EventBus.unregister(canceller);
        }
        helper.assertTrue(!fireball.isRemoved(), "a cancelled impact still let the fireball explode");
        fireball.discard();
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void impactLetsTheFireballExplode(GameTestHelper helper) {
        LargeFireball fireball = helper.spawn(EntityTypes.FIREBALL, POS);
        hitBlockBelow(helper, fireball);
        helper.assertTrue(fireball.isRemoved(), "the fireball did not go off at all, so the cancel test proves nothing");
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void cancelledPearlIsRemovedAndDoesNotTeleport(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Vec3 start = player.position();
        ThrownEnderpearl pearl = thrownBy(helper, player);
        AtomicInteger posted = new AtomicInteger();
        Consumer<EntityTeleportEvent.EnderPearl> canceller = event -> {
            if (event.getEntity() == player) {
                posted.incrementAndGet();
                event.setCanceled(true);
            }
        };
        EventBus.addListener(EntityTeleportEvent.EnderPearl.class, canceller);
        try {
            land(helper, pearl);
        } finally {
            EventBus.unregister(canceller);
        }
        helper.assertTrue(posted.get() == 1, "pearl event posted " + posted + " times");
        helper.assertTrue(pearl.isRemoved(), "a cancelled pearl stayed in the world");
        helper.assertTrue(player.position().equals(start), "a cancelled pearl still moved its thrower");
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void pearlTeleportsItsThrower(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Vec3 start = player.position();
        ThrownEnderpearl pearl = thrownBy(helper, player);
        land(helper, pearl);
        helper.assertTrue(pearl.isRemoved(), "the pearl was not used up");
        helper.assertTrue(!player.position().equals(start), "an uncancelled pearl did not move its thrower, "
                + "so the cancel test proves nothing");
        helper.succeed();
    }

    /** NeoForge clears result and cost on a cancel; vanilla computed both before the event. */
    @GameTest(skyAccess = true)
    public void cancelledAnvilUpdateClearsResultAndCost(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.createConnected(helper);
        Consumer<AnvilUpdateEvent> canceller = event -> {
            if (event.getPlayer() == player) {
                event.setCanceled(true);
            }
        };
        EventBus.addListener(AnvilUpdateEvent.class, canceller);
        AnvilMenu menu;
        try {
            menu = repairInAnvil(player);
        } finally {
            EventBus.unregister(canceller);
        }
        helper.assertTrue(!menu.getSlot(2).hasItem(), "a cancelled anvil update still offered a result");
        helper.assertTrue(menu.getCost() == 0, "a cancelled anvil update kept a cost of " + menu.getCost());
        helper.succeed();
    }

    @GameTest(skyAccess = true)
    public void anvilRepairOffersAResult(GameTestHelper helper) {
        AnvilMenu menu = repairInAnvil(TestPlayers.createConnected(helper));
        helper.assertTrue(menu.getSlot(2).hasItem() && menu.getCost() > 0,
                "an anvil offered nothing for a damaged sword and its material, so the cancel test proves nothing");
        helper.succeed();
    }

    private static AnvilMenu repairInAnvil(ServerPlayer player) {
        AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(sword.getMaxDamage() / 2);
        menu.getSlot(0).set(sword);
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        return menu;
    }

    private static ThrownEnderpearl thrownBy(GameTestHelper helper, ServerPlayer player) {
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), player, new ItemStack(Items.ENDER_PEARL));
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(POS));
        pearl.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(pearl);
        return pearl;
    }

    private static void land(GameTestHelper helper, ThrownEnderpearl pearl) {
        invoke(pearl, "onHit", blockHitBelow(helper));
    }

    private static void hitBlockBelow(GameTestHelper helper, Projectile projectile) {
        invoke(projectile, "hitTargetOrDeflectSelf", blockHitBelow(helper));
    }

    private static BlockHitResult blockHitBelow(GameTestHelper helper) {
        BlockPos below = helper.absolutePos(POS.below());
        return new BlockHitResult(Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false);
    }

    private static void invoke(Object target, String name, HitResult hit) {
        try {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == HitResult.class) {
                        method.setAccessible(true);
                        method.invoke(target, hit);
                        return;
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("no " + name + "(HitResult) on " + target.getClass());
    }
}
