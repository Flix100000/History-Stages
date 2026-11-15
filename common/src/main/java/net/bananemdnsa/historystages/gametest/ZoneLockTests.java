package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneIndex;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneVerdict;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.lock.ZoneLockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.tick.PlayerTickEvent;

/**
 * The zone seam, answered against a live server.
 *
 * <p>The geometry and the merge rules are pinned by {@code ZoneGeometryTest} and
 * {@code ZoneVerdictTest}, which need no game at all. What can only be shown here is the wiring:
 * that the index picks up a zone the moment its stage is published, that unlocking the stage
 * makes it stop applying without a restart, that an individual stage is judged per player, and
 * that a zone stops at its dimension.
 *
 * <p><strong>Nothing here runs the real tick.</strong> The test player has no connection, so
 * applying an effect or a message would try to send it a packet and the failure would say nothing
 * about zones. {@link ZoneLockHandler#verdictFor} is asked instead — the same computation the tick
 * performs before it applies anything.
 */
public final class ZoneLockTests {

    private ZoneLockTests() {}

    // ---------------------------------------------------------------------------------------
    // Helpers. Deliberately above the tests: GameTestCleanupGuardTest slices the file from one
    // test to the next and gives the last test everything to the end, so a helper sitting after
    // it gets blamed on that test.
    // ---------------------------------------------------------------------------------------

    /**
     * A zone covering the whole world horizontally, in the dimension the tests run in.
     *
     * <p>Absolute coordinates would have to know where the test structure was placed; a box this
     * size contains the player wherever the harness put them, which keeps these tests about the
     * wiring rather than about arithmetic that is already covered without a game.
     */
    private static ZoneEntry everywhere(String name, String dimension) {
        ZoneEntry zone = new ZoneEntry();
        zone.setName(name);
        zone.setDimension(dimension);
        zone.setShapes(List.of(ZoneShape.cube(
                -30_000_000, 0, -30_000_000, 30_000_000, 0, 30_000_000, true)));
        return zone;
    }

    /** A zone that contains nothing near the player, for the "leaves them alone" half. */
    private static ZoneEntry farAway(String name, String dimension) {
        ZoneEntry zone = new ZoneEntry();
        zone.setName(name);
        zone.setDimension(dimension);
        zone.setShapes(List.of(ZoneShape.cube(
                29_000_000, 0, 29_000_000, 29_000_100, 0, 29_000_100, true)));
        return zone;
    }

    private static String dimensionOf(GameTestHelper helper) {
        return helper.getLevel().dimension().identifier().toString();
    }

    private static ZoneEntry damaging(ZoneEntry zone, double amount) {
        zone.getRules().getDamage().setEnabled(true);
        zone.getRules().getDamage().setAmount(amount);
        return zone;
    }

    private static ZoneEntry inverted(ZoneEntry zone) {
        zone.getRules().setInverted(true);
        return zone;
    }

    private static ZoneEntry barrier(ZoneEntry zone) {
        zone.getRules().setBarrier(true);
        return zone;
    }

    private static ZoneEntry spawnBlocking(ZoneEntry zone) {
        zone.getRules().setBlockSpawns(true);
        return zone;
    }

    /**
     * Throws a pearl straight down onto a stone block in the test structure and lets it fly the
     * way the server would, tick by tick, until it is gone. The thrower stands ten blocks away.
     * Returns the thrower's position afterwards, or null when the pearl never came down.
     */
    private static Vec3 throwPearlOntoTheFloor(GameTestHelper helper, ServerPlayer thrower) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), thrower, new ItemStack(Items.ENDER_PEARL));
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        pearl.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        pearl.setDeltaMovement(0.0, -0.5, 0.0);
        helper.getLevel().addFreshEntity(pearl);
        for (int i = 0; i < 40 && !pearl.isRemoved(); i++) {
            pearl.tick();
        }
        return pearl.isRemoved() ? thrower.position() : null;
    }

    private static ServerPlayer pearlThrower(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        BlockPos stand = helper.absolutePos(new BlockPos(11, 1, 1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
        return player;
    }

    /** A barrier zone around the test structure only, so the thrower stands outside it. */
    private static ZoneEntry barrierAroundTheStructure(GameTestHelper helper) {
        BlockPos low = helper.absolutePos(new BlockPos(-1, -1, -1));
        BlockPos high = helper.absolutePos(new BlockPos(3, 4, 3));
        ZoneEntry zone = new ZoneEntry();
        zone.setName("walled");
        zone.setDimension(dimensionOf(helper));
        zone.setShapes(List.of(ZoneShape.cube(low.getX(), low.getY(), low.getZ(),
                high.getX(), high.getY(), high.getZ(), false)));
        return barrier(zone);
    }

    // ---------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------

    /** The index has to notice a zone as soon as its stage exists. */
    @HsGameTest
    public static void aLockedZoneAppliesToAPlayerInsideIt(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_inside", stage -> stage.setZones(
                    new ArrayList<>(List.of(damaging(
                            everywhere("inside", dimensionOf(helper)), 3.0)))));

            ZoneVerdict verdict = ZoneLockHandler.verdictFor(player);
            if (!verdict.isLocked()) {
                helper.fail("the player stands in a zone of a locked stage, but the verdict says "
                        + "they are not in one");
                return;
            }
            if (verdict.damageAmount() != 3.0) {
                helper.fail("the zone asks for 3.0 damage, the verdict says "
                        + verdict.damageAmount());
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** A zone somewhere else must leave the player alone. */
    @HsGameTest
    public static void aZoneElsewhereDoesNotApply(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_far", stage -> stage.setZones(
                    new ArrayList<>(List.of(damaging(
                            farAway("far", dimensionOf(helper)), 3.0)))));

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("a zone 29 million blocks away is being applied to the player");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * The dimension is part of the zone, not decoration.
     *
     * <p>Without it a Nether fortress zone would also fire in the middle of an Overworld village,
     * which is the reason the field is mandatory.
     */
    @HsGameTest
    public static void aZoneInAnotherDimensionDoesNotApply(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            String elsewhere = dimensionOf(helper).equals("minecraft:overworld")
                    ? "minecraft:the_nether" : "minecraft:overworld";
            GameTestStages.global("zone_other_dim", stage -> stage.setZones(
                    new ArrayList<>(List.of(damaging(everywhere("other", elsewhere), 3.0)))));

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("a zone declared for " + elsewhere + " is being applied in "
                        + dimensionOf(helper));
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** Unlocking the stage stops the zone at once — no restart, no interval to wait out. */
    @HsGameTest
    public static void unlockingTheStageEndsTheLockImmediately(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_unlock", stage -> stage.setZones(
                    new ArrayList<>(List.of(damaging(
                            everywhere("unlock", dimensionOf(helper)), 3.0)))));

            if (!ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("the zone is not applying before the stage is unlocked");
                return;
            }

            StageData.get(helper.getLevel()).addStage("gametest:zone_unlock");

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("the stage is unlocked but the zone is still applying");
                return;
            }
            helper.succeed();
        } finally {
            StageData.get(helper.getLevel()).removeStage("gametest:zone_unlock");
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * An individual stage is judged per player.
     *
     * <p>This is the gap that has opened repeatedly in this mod: a feature reads the global stage
     * map, forgets the individual one, and the whole per-player half quietly does nothing.
     */
    @HsGameTest
    public static void anIndividualStageIsJudgedPerPlayer(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.individual("zone_individual", stage -> stage.setZones(
                    new ArrayList<>(List.of(damaging(
                            everywhere("individual", dimensionOf(helper)), 3.0)))));

            if (!ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("an individual stage the player does not have is not gating its zone");
                return;
            }

            IndividualStageData.get(helper.getLevel())
                    .addStage(player.getUUID(), "gametest:zone_individual");

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("the player has the individual stage but the zone still applies");
                return;
            }
            helper.succeed();
        } finally {
            IndividualStageData.get(helper.getLevel())
                    .removeStage(player.getUUID(), "gametest:zone_individual");
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** Two overlapping zones: the higher damage wins rather than the two adding up. */
    @HsGameTest
    public static void overlappingZonesTakeTheHighestDamage(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            String dimension = dimensionOf(helper);
            GameTestStages.global("zone_overlap", stage -> stage.setZones(
                    new ArrayList<>(List.of(
                            damaging(everywhere("weak", dimension), 1.0),
                            damaging(everywhere("strong", dimension), 4.0)))));

            ZoneVerdict verdict = ZoneLockHandler.verdictFor(player);
            if (verdict.damageAmount() != 4.0) {
                helper.fail("two overlapping zones asking for 1.0 and 4.0 should apply 4.0, "
                        + "the verdict says " + verdict.damageAmount());
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * Reaching into a zone from outside it is refused.
     *
     * <p>Without this a player standing at the border could mine their way in, which would make
     * every zone a wall with a door in it.
     */
    @HsGameTest
    public static void breakingIntoAZoneFromOutsideIsRefused(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_reach", stage -> stage.setZones(
                    new ArrayList<>(List.of(everywhere("reach", dimensionOf(helper))))));

            // Warm the player's cached zone list; the break seam reads what the tick left behind.
            ZoneLockHandler.verdictFor(player);

            BlockPos inside = player.blockPosition().offset(3, 0, 0);
            if (!ZoneLockHandler.breakBlocked(player, inside)) {
                helper.fail("breaking a block inside a locked zone was allowed");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** An inverted zone holds everywhere it is not — so a player far from it is the one it gates. */
    @HsGameTest
    public static void anInvertedZoneAppliesOutsideItself(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_inverted", stage -> stage.setZones(
                    new ArrayList<>(List.of(inverted(damaging(
                            farAway("cage", dimensionOf(helper)), 3.0))))));

            if (!ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("an inverted zone should gate everywhere it is not, and the player "
                        + "is 29 million blocks from it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** Standing in an inverted zone is the allowed place, so nothing applies. */
    @HsGameTest
    public static void beingInsideAnInvertedZoneIsAllowed(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_inverted_in", stage -> stage.setZones(
                    new ArrayList<>(List.of(inverted(damaging(
                            everywhere("cage", dimensionOf(helper)), 3.0))))));

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("the player is inside the inverted zone, which is the allowed place");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * Inversion must not leak across worlds.
     *
     * <p>Otherwise every inverted zone would read as "everywhere except here, in every world", and
     * stepping through a portal would put a player in the locked half at once.
     */
    @HsGameTest
    public static void anInvertedZoneStopsAtItsDimension(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            String elsewhere = dimensionOf(helper).equals("minecraft:overworld")
                    ? "minecraft:the_nether" : "minecraft:overworld";
            GameTestStages.global("zone_inverted_dim", stage -> stage.setZones(
                    new ArrayList<>(List.of(inverted(damaging(
                            farAway("cage", elsewhere), 3.0))))));

            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("an inverted zone declared for " + elsewhere + " reached into "
                        + dimensionOf(helper));
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * The barrier reports somewhere to put the player.
     *
     * <p>Asks for the target rather than letting the tick apply it: moving the test player sends
     * a position packet down a connection they do not have.
     */
    @HsGameTest
    public static void aBarrierZoneHasSomewhereToPushThePlayer(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_barrier", stage -> stage.setZones(
                    new ArrayList<>(List.of(barrier(everywhere("wall", dimensionOf(helper)))))));

            ZoneLockHandler.verdictFor(player);
            if (ZoneLockHandler.barrierTargetFor(player) == null) {
                helper.fail("a barrier zone containing the player produced no way out");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** A zone without the switch pushes nobody, however thoroughly it contains them. */
    @HsGameTest
    public static void aZoneWithoutTheBarrierSwitchPushesNobody(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("zone_no_barrier", stage -> stage.setZones(
                    new ArrayList<>(List.of(everywhere("open", dimensionOf(helper))))));

            ZoneLockHandler.verdictFor(player);
            if (ZoneLockHandler.barrierTargetFor(player) != null) {
                helper.fail("a zone with no barrier switch tried to move the player");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * A mob belongs to the world, not to a player, so an individual stage cannot suppress spawns.
     *
     * <p>Checked through the index flag rather than by waiting for a spawn: the flag is what the
     * spawn hook reads before it does anything, so a false there is the whole behaviour.
     */
    @HsGameTest
    public static void onlyGlobalStagesCanSuppressSpawns(GameTestHelper helper) {
        try {
            GameTestStages.individual("zone_spawn_individual", stage -> stage.setZones(
                    new ArrayList<>(List.of(spawnBlocking(
                            everywhere("quiet", dimensionOf(helper)))))));

            ZoneIndex.rebuildIfDirty();
            if (ZoneIndex.anySpawnBlocking()) {
                helper.fail("an individual stage switched on spawn suppression, which cannot work "
                        + "per player and has to be ignored");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The same switch on a global stage does arm it. */
    @HsGameTest
    public static void aGlobalStageArmsSpawnSuppression(GameTestHelper helper) {
        try {
            GameTestStages.global("zone_spawn_global", stage -> stage.setZones(
                    new ArrayList<>(List.of(spawnBlocking(
                            everywhere("quiet", dimensionOf(helper)))))));

            ZoneIndex.rebuildIfDirty();
            if (!ZoneIndex.anySpawnBlocking()) {
                helper.fail("a global stage asked for spawn suppression and it stayed off");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * A sphere protects exactly the blocks within its radius. Checked because a biome lock that
     * outlived its stage once looked like a sphere zone reaching past its own surface.
     */
    @HsGameTest
    public static void aSphereZoneStopsAtItsRadius(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            BlockPos centre = helper.absolutePos(new BlockPos(1, 1, 1));
            ZoneEntry zone = new ZoneEntry();
            zone.setName("ball");
            zone.setDimension(dimensionOf(helper));
            zone.setShapes(List.of(ZoneShape.sphere(centre.getX(), centre.getY(), centre.getZ(), 6)));
            GameTestStages.global("zone_sphere", stage -> stage.setZones(new ArrayList<>(List.of(zone))));

            BlockPos[] inside = {centre, centre.east(6), centre.above(6), centre.offset(3, 3, 3)};
            BlockPos[] outside = {centre.east(7), centre.below(7), centre.offset(4, 4, 3), centre.offset(5, 0, 4)};
            for (BlockPos pos : inside) {
                if (ZoneLockHandler.lockingStagesAt(helper.getLevel(), pos, player.getUUID()).isEmpty()) {
                    helper.fail(pos.subtract(centre) + " from the centre is inside a radius-6 sphere but not protected");
                    return;
                }
            }
            for (BlockPos pos : outside) {
                if (!ZoneLockHandler.lockingStagesAt(helper.getLevel(), pos, player.getUUID()).isEmpty()) {
                    helper.fail(pos.subtract(centre) + " from the centre is outside a radius-6 sphere but protected");
                    return;
                }
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /**
     * A pearl thrown into a barrier zone from outside must not carry its thrower in, and must not
     * stay in the world either.
     */
    @HsGameTest
    public static void aPearlIntoABarrierZoneDoesNotTeleport(GameTestHelper helper) {
        ServerPlayer player = pearlThrower(helper);
        try {
            GameTestStages.global("zone_pearl", stage -> stage.setZones(
                    new ArrayList<>(List.of(barrierAroundTheStructure(helper)))));
            EventBus.post(new PlayerTickEvent.Post(player));
            if (ZoneLockHandler.verdictFor(player).isLocked()) {
                helper.fail("the thrower should stand outside the zone");
                return;
            }

            Vec3 before = player.position();
            Vec3 after = throwPearlOntoTheFloor(helper, player);
            if (after == null) {
                helper.fail("the pearl never landed and is still in the world");
                return;
            }
            if (!after.equals(before)) {
                helper.fail("the pearl carried its thrower into a barrier zone, to " + after);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            ZoneLockHandler.clearPlayer(player.getUUID());
        }
    }

    /** Without the zone the same throw does move the thrower, so the test above can fail. */
    @HsGameTest
    public static void aPearlWithoutAZoneTeleports(GameTestHelper helper) {
        ServerPlayer player = pearlThrower(helper);
        Vec3 before = player.position();
        Vec3 after = throwPearlOntoTheFloor(helper, player);
        if (after == null || after.equals(before)) {
            helper.fail("an ordinary pearl did not teleport its thrower (landed: " + (after != null) + ")");
            return;
        }
        helper.succeed();
    }
}
