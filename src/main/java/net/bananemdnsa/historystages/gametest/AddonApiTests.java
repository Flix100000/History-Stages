package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.lock.CategoryLocks;
import net.bananemdnsa.historystages.api.lock.ItemLocks;
import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.lock.LockFeedbacks;
import net.bananemdnsa.historystages.api.lock.PlaceLocks;
import net.bananemdnsa.historystages.api.lock.Subjects;
import net.bananemdnsa.historystages.api.stage.StageDefinitionsChangedEvent;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.display.HiddenDisplayConfig;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.StageDefinitionsEventBridge;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.bananemdnsa.historystages.platform.bus.EventBus;

/**
 * The addon api as an addon meets it: built-in categories asked with the things an addon has in
 * hand, the veto, the definitions event, offline players, and the feedback helper.
 */
public class AddonApiTests {

    private static final String P = GameTestStages.PREFIX;

    public AddonApiTests() {}

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theItemCategoryAnswersAnIdAndAStack(GameTestHelper helper) {
        try {
            GameTestStages.global("api_items", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:diamond")))));
            UUID player = UUID.randomUUID();

            if (!CategoryLocks.isLockedForPlayer("historystages:items", "minecraft:diamond", player)) {
                helper.fail("an item id must reach the item category; this is the call the scripting docs show");
                return;
            }
            if (!CategoryLocks.isLockedForPlayer("historystages:items", new ItemStack(Items.DIAMOND), player)) {
                helper.fail("a stack must reach the item category");
                return;
            }
            // The script form of this question is neoforge-only; the global half it asks is this.
            if (!CategoryLocks.isLockedGlobally("historystages:items", "minecraft:diamond")) {
                helper.fail("an item id must reach the item category through the global question too");
                return;
            }
            if (!CategoryLocks.missingStagesGlobally("historystages:items", Items.DIAMOND).equals(List.of(P + "api_items"))) {
                helper.fail("an Item must reach the category too, and name the stage");
                return;
            }
            // The control: without it, a category answering "locked" to everything passes.
            if (CategoryLocks.isLockedForPlayer("historystages:items", "minecraft:dirt", player)) {
                helper.fail("an item no stage names must read as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theModCategoryAnswersAStack(GameTestHelper helper) {
        try {
            GameTestStages.global("api_mods", stage ->
                    stage.setModEntries(new ArrayList<>(List.of(new NamedLockEntry("minecraft")))));
            if (!CategoryLocks.isLockedGlobally("historystages:mods", new ItemStack(Items.DIAMOND))) {
                helper.fail("a vanilla stack must read as gated by a minecraft mod entry");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aSpawnSubjectReachesTheSpawnCategory(GameTestHelper helper) {
        String dimension = helper.getLevel().dimension().location().toString();
        try {
            GameTestStages.global("api_spawn", stage -> stage.getEntities().setSpawnlock(
                    new ArrayList<>(List.of(new EntitySpawnLockEntry("minecraft:zombie")))));
            if (!CategoryLocks.isLockedGlobally("historystages:spawnlock",
                    Subjects.spawn("minecraft:zombie", null, dimension))) {
                helper.fail("a spawn subject for the listed mob must read as locked");
                return;
            }
            if (CategoryLocks.isLockedGlobally("historystages:spawnlock",
                    Subjects.spawn("minecraft:skeleton", null, dimension))) {
                helper.fail("a mob nobody listed must read as unlocked");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void itemLocksTellGatedFromActionLocked(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        try {
            GameTestStages.global("api_actions", stage -> stage.setItemEntries(new ArrayList<>(List.of(
                    new ItemEntry("minecraft:diamond_sword", null, new ArrayList<>(List.of("pickup")))))));

            if (!ItemLocks.isGated(sword, player)) {
                helper.fail("a stage naming the item gates it, whatever the actions");
                return;
            }
            if (!ItemLocks.isActionLocked(sword, "pickup", player)) {
                helper.fail("the one action the entry names must be locked");
                return;
            }
            if (ItemLocks.isActionLocked(sword, "use", player)) {
                helper.fail("an action the entry leaves out must stay free; this is Issue #117");
                return;
            }
            if (!ItemLocks.missingStages(sword, player).equals(List.of(P + "api_actions"))) {
                helper.fail("missingStages must name the gating stage");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void definitionChangesArriveAsOneEvent(GameTestHelper helper) {
        int[] received = {0};
        Consumer<StageDefinitionsChangedEvent> listener = event -> received[0]++;
        // Whatever an earlier test left pending is flushed before listening.
        StageDefinitionsEventBridge.postIfChanged();
        EventBus.addListener(StageDefinitionsChangedEvent.class, listener);
        try {
            GameTestStages.global("api_def_a");
            GameTestStages.global("api_def_b");
            StageDefinitionsEventBridge.postIfChanged();
            StageDefinitionsEventBridge.postIfChanged();

            if (received[0] != 1) {
                helper.fail("two changes in one tick must arrive as exactly one event; got " + received[0]);
                return;
            }
            helper.succeed();
        } finally {
            EventBus.unregister(listener);
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anAddonCanRefuseANormalUnlockButNotAnAdminOne(GameTestHelper helper) {
        String id = P + "api_veto";
        StageData data = StageData.get(helper.getLevel());
        Consumer<StageEvent.BeforeUnlock> listener = event -> {
            if (event.getStageId().equals(id)) event.setCanceled(true);
        };
        EventBus.addListener(StageEvent.BeforeUnlock.class, listener);
        try {
            GameTestStages.global("api_veto");
            if (StageStates.unlockGlobal(id, helper.getLevel()) || data.hasStage(id)) {
                helper.fail("a cancelled BeforeUnlock must keep the stage locked");
                return;
            }
            if (!StageStates.forceUnlockGlobal(id, helper.getLevel()).unlocked()) {
                helper.fail("an admin unlock skips the veto, as it skips a logic block");
                return;
            }
            helper.succeed();
        } finally {
            EventBus.unregister(listener);
            GameTestStages.removeAll();
            data.removeStage(id);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anOfflinePlayerCanBeGivenAStage(GameTestHelper helper) {
        String id = P + "api_offline";
        UUID absent = UUID.randomUUID();
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        List<UUID> events = new ArrayList<>();
        Consumer<StageEvent.IndividualUnlocked> listener = event -> {
            if (event.getStageId().equals(id)) events.add(event.getPlayerUUID());
        };
        EventBus.addListener(StageEvent.IndividualUnlocked.class, listener);
        try {
            GameTestStages.individual("api_offline");
            if (!StageStates.unlockIndividual(id, absent, helper.getLevel().getServer())) {
                helper.fail("unlocking for an offline player must succeed");
                return;
            }
            if (!data.hasStage(absent, id) || !events.equals(List.of(absent))) {
                helper.fail("the stage must be written and announced once, for that player; events: " + events);
                return;
            }
            if (!StageStates.relockIndividual(id, absent, helper.getLevel().getServer()) || data.hasStage(absent, id)) {
                helper.fail("relocking for an offline player must take it away again");
                return;
            }
            helper.succeed();
        } finally {
            EventBus.unregister(listener);
            GameTestStages.removeAll();
            data.removeStage(absent, id);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void unlockTimesAndTimersAreReadable(GameTestHelper helper) {
        String id = P + "api_time";
        StageData data = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("api_time");
            if (StageStates.unlockedAtGlobal(id, helper.getLevel()).isPresent()) {
                helper.fail("a locked stage has no unlock time");
                return;
            }
            data.addStage(id);
            if (StageStates.unlockedAtGlobal(id, helper.getLevel()).isEmpty()) {
                helper.fail("an unlocked stage must report when it was unlocked");
                return;
            }
            if (StageStates.remainingTicksGlobal(id, helper.getLevel()).isPresent()) {
                helper.fail("a stage that is not temporary has no timer");
                return;
            }
            if (!StageStates.unlockedGlobal(helper.getLevel()).contains(id)) {
                helper.fail("the unlocked set must contain the stage");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(id);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void aRelockKeepsItemsWhenDropOnRevokeIsOff(GameTestHelper helper) {
        String id = P + "api_drop";
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        boolean original = Config.GAMEPLAY.individualDropOnRevoke.get();
        try {
            GameTestStages.individual("api_drop", stage ->
                    stage.setItemEntries(new ArrayList<>(List.of(new ItemEntry("minecraft:diamond_sword")))));

            Config.GAMEPLAY.individualDropOnRevoke.set(false);
            data.addStage(player.getUUID(), id);
            player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
            StageStates.relockIndividual(id, player);
            if (!player.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD))) {
                helper.fail("with dropOnRevoke off, the relock must leave the sword in the inventory");
                return;
            }

            // The control: with the switch on, the same relock does drop it.
            Config.GAMEPLAY.individualDropOnRevoke.set(true);
            data.addStage(player.getUUID(), id);
            StageStates.relockIndividual(id, player);
            if (player.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD))) {
                helper.fail("with dropOnRevoke on, the relock must drop the sword");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.individualDropOnRevoke.set(original);
            GameTestStages.removeAll();
            data.removeStage(player.getUUID(), id);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void theRequiredSuffixHonoursHints(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        try {
            GameTestStages.global("api_hint_on");
            GameTestStages.global("api_hint_off", stage -> {
                HiddenDisplayConfig hidden = new HiddenDisplayConfig();
                hidden.setShowLockHints(false);
                stage.setHiddenDisplay(hidden);
            });

            if (LockFeedbacks.requiredSuffix(player, List.of(P + "api_hint_on")) == null) {
                helper.fail("a stage that shows its hints must be named");
                return;
            }
            if (LockFeedbacks.requiredSuffix(player, List.of(P + "api_hint_on", P + "api_hint_off")) != null) {
                helper.fail("one stage hiding its hints must suppress the whole suffix");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void placeLocksSeeZonesBiomesAndDimensions(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        String dimension = level.dimension().location().toString();
        String biome = level.getBiome(pos).unwrapKey().orElseThrow().location().toString();
        try {
            if (PlaceLocks.isLocked(player, level, pos)) {
                helper.fail("nothing gates this place yet; the control must read free");
                return;
            }
            ZoneEntry zone = new ZoneEntry();
            zone.setName("api_zone");
            zone.setDimension(dimension);
            zone.setShapes(List.of(ZoneShape.cube(
                    -30_000_000, 0, -30_000_000, 30_000_000, 0, 30_000_000, true)));
            GameTestStages.global("api_place_zone", stage -> stage.setZones(new ArrayList<>(List.of(zone))));
            GameTestStages.individual("api_place_biome", stage -> stage.setBiomes(new ArrayList<>(List.of(biome))));
            GameTestStages.global("api_place_dim", stage -> stage.setDimensions(new ArrayList<>(List.of(dimension))));

            List<String> missing = PlaceLocks.missingStages(player, level, pos);
            List<String> expected = List.of(P + "api_place_dim", P + "api_place_biome", P + "api_place_zone");
            if (!missing.equals(expected)) {
                helper.fail("expected dimension, biome and zone stages in that order, got " + missing);
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void anUnknownOrWrongScopeIdIsRefused(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        StageData global = StageData.get(helper.getLevel());
        IndividualStageData individual = IndividualStageData.get(helper.getLevel());
        String typo = P + "api_no_such_stage";
        String personal = P + "api_personal";
        try {
            GameTestStages.individual("api_personal");
            if (StageStates.unlockGlobal(typo, helper.getLevel()) || global.hasStage(typo)) {
                helper.fail("an id no stage has must not be written into the save");
                return;
            }
            if (StageStates.forceUnlockGlobal(personal, helper.getLevel()).unlocked() || global.hasStage(personal)) {
                helper.fail("an individual stage must not land in the world's set through the global method");
                return;
            }
            if (!StageStates.unlockIndividual(personal, player) || !individual.hasStage(player.getUUID(), personal)) {
                helper.fail("the same id through the right method must work; the control");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            global.removeStage(typo);
            global.removeStage(personal);
            individual.removeStage(player.getUUID(), personal);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public static void blockedLostAndRequirementsAreReadable(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        String gate = P + "api_gate";
        String blocked = P + "api_blocked";
        StageData data = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("api_gate");
            GameTestStages.global("api_blocked", stage -> stage.setLogic(LogicCodec.write(List.of(
                    new LogicBlock(LogicBlockTypes.BLOCKED_WHILE,
                            new Condition.Not(new Condition.Unlocked(gate, null)), new JsonObject())))));
            DependencyGroup needsGate = new DependencyGroup();
            needsGate.setStages(new ArrayList<>(List.of(gate)));
            GameTestStages.global("api_needs", needsGate);

            if (!StageStates.isBlocked(blocked, player)) {
                helper.fail("the logic blocks the stage while the gate is shut");
                return;
            }
            if (StageStates.isLostForGood(blocked, player)) {
                helper.fail("nothing marked the stage lost");
                return;
            }
            RequirementResult before = StageStates.checkRequirements(P + "api_needs", player);
            if (before == null || before.isFulfilled()) {
                helper.fail("a stage needing the shut gate must report its requirement unmet");
                return;
            }
            data.addStage(gate);
            if (StageStates.isBlocked(blocked, player)) {
                helper.fail("with the gate open the logic no longer blocks");
                return;
            }
            if (!StageStates.checkRequirements(P + "api_needs", player).isFulfilled()) {
                helper.fail("with the gate open the requirement is met");
                return;
            }
            if (StageStates.checkRequirements(P + "api_nothing", player) != null) {
                helper.fail("an unknown id has no requirements to report");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(gate);
        }
    }
}
