package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.NegatedTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.WeatherTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import net.bananemdnsa.historystages.data.logic.Condition;
import net.bananemdnsa.historystages.data.logic.LogicBlock;
import net.bananemdnsa.historystages.data.logic.LogicBlockTypes;
import net.bananemdnsa.historystages.data.logic.LogicCodec;
import net.bananemdnsa.historystages.data.relock.LockTrigger;
import net.bananemdnsa.historystages.data.relock.RelockPolls;
import net.bananemdnsa.historystages.data.saveddata.AutoTriggerProgressData;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LockTriggerProgressData;
import net.bananemdnsa.historystages.data.saveddata.LostStagesData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.events.AutoTriggerEventBridge;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Re-locking at runtime: conditional stages following their states, and lock triggers closing
 * DEFAULT/AUTO stages again, with and without permanent loss.
 *
 * <p>Players are always the connected variant: every unlock and relock here sends the player a
 * sync packet, and a lost mark sends another.
 *
 * <p>Unlock messages ({@code notify: false}) are not tested here: the silent listener swallows
 * them, so there is nothing to observe.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelockTests {

    private static final String P = GameTestStages.PREFIX;
    // Unreachable in a test, so an AUTO stage only opens when a test opens it.
    private static final XpLevelTrigger NEVER = new XpLevelTrigger(100_000);

    private RelockTests() {}

    // --- helpers ------------------------------------------------------------

    private static String effectId(Holder<MobEffect> effect) {
        return String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
    }

    private static void postEffect(ServerPlayer player, Holder<MobEffect> effect) {
        // Posted rather than applied: the listener is what is under test, and a real effect would
        // stay on the player and change what later polls in the same test see.
        NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(player, null, new MobEffectInstance(effect, 100), null));
    }

    private static void conditional(String name, boolean individual, String mode, List<TriggerCondition> triggers,
                                    DependencyGroup... groups) {
        if (individual) {
            GameTestStages.individual(name, e -> {
                e.setMode(StageMode.CONDITIONAL);
                e.setAutoTrigger(new AutoTrigger(mode, triggers));
            }, groups);
        } else {
            GameTestStages.global(name, e -> {
                e.setMode(StageMode.CONDITIONAL);
                e.setAutoTrigger(new AutoTrigger(mode, triggers));
            }, groups);
        }
    }

    private static void autoWithLock(String name, List<TriggerCondition> unlock, String lockMode,
                                     List<TriggerCondition> lock, Boolean reUnlockable) {
        GameTestStages.individual(name, e -> {
            e.setMode(StageMode.AUTO);
            e.setAutoTrigger(new AutoTrigger("any", unlock));
            e.setLockTrigger(new LockTrigger(lockMode, lock, reUnlockable));
        });
    }

    private static boolean open(GameTestHelper helper, ServerPlayer player, String id) {
        return IndividualStageData.get(helper.getLevel()).hasStage(player.getUUID(), id);
    }

    private static Set<Long> lockProgress(GameTestHelper helper, ServerPlayer player, String id) {
        return LockTriggerProgressData.get(helper.getLevel()).peekIndividual(player.getUUID(), id);
    }

    /**
     * Everything an individual test may have left behind for this player. Call it right after
     * {@code GameTestStages.removeAll()}, which stays in each finally so GameTestCleanupGuardTest
     * can see it.
     */
    private static void cleanUp(GameTestHelper helper, ServerPlayer player, String... names) {
        AutoTriggerManager.rebuildIndex();
        if (player == null) return;
        ServerLevel level = helper.getLevel();
        UUID uuid = player.getUUID();
        for (String name : names) {
            String id = P + name;
            IndividualStageData.get(level).removeStage(uuid, id);
            LostStagesData.get(level).clearIndividual(uuid, id);
            LockTriggerProgressData.get(level).clearIndividual(uuid, id);
            AutoTriggerProgressData.get(level).clear(uuid, id);
        }
    }

    // --- conditional --------------------------------------------------------

    @GameTest(template = "empty")
    public static void conditionalOpensAndClosesWithXp(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "cond_xp";
        try {
            conditional("cond_xp", true, "any", List.of(new XpLevelTrigger(5)));
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);

            player.experienceLevel = 7;
            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (!open(helper, player, id)) {
                helper.fail("level 7 meets the XP state of the conditional stage; the poll must open it");
                return;
            }
            player.experienceLevel = 3;
            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (open(helper, player, id)) {
                helper.fail("level 3 no longer meets the XP state; the poll must close the conditional stage");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "cond_xp");
        }
    }

    @GameTest(template = "empty")
    public static void conditionalIgnoresAnEventTrigger(GameTestHelper helper) {
        ServerPlayer player = null;
        String onlyEvent = P + "cond_event";
        String mixed = P + "cond_mixed";
        try {
            EntityTrigger zombie = new EntityTrigger("minecraft:zombie", null);
            conditional("cond_event", true, "any", List.of(zombie));
            // ALL with an event in it: the event is skipped, so it must not pin the stage shut.
            conditional("cond_mixed", true, "all", List.of(new XpLevelTrigger(5), zombie));
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);
            player.experienceLevel = 7;

            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (open(helper, player, onlyEvent)) {
                helper.fail("an event trigger is not a state; a conditional stage with only an event must stay closed");
                return;
            }
            if (!open(helper, player, mixed)) {
                helper.fail("the event trigger in an ALL conditional must be skipped, not count as unmet; "
                        + "XP 7 >= 5 should open the stage");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "cond_event", "cond_mixed");
        }
    }

    @GameTest(template = "empty")
    public static void globalConditionalIgnoresAPlayerState(GameTestHelper helper) {
        String id = P + "cond_global_xp";
        StageData data = StageData.get(helper.getLevel());
        try {
            // Level 0 holds in any view, the world view included, so only the scope rule keeps
            // this shut.
            conditional("cond_global_xp", false, "any", List.of(new XpLevelTrigger(0)));
            AutoTriggerManager.rebuildIndex();

            RelockPolls.pollWorld(helper.getLevel().getServer());
            if (StageData.get(helper.getLevel()).hasStage(id)) {
                helper.fail("a global conditional stage accepts world states only; an XP state must be ignored");
                return;
            }
            helper.succeed();
        } finally {
            data.removeStage(id);
            StageData.refreshCache(data.getUnlockedStages());
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
        }
    }

    @GameTest(template = "empty")
    public static void globalConditionalFollowsWeather(GameTestHelper helper) {
        String id = P + "cond_rain";
        ServerLevel level = helper.getLevel();
        StageData data = StageData.get(level);
        ServerLevelData weather = (ServerLevelData) level.getLevelData();
        boolean wasRaining = weather.isRaining();
        boolean wasThundering = weather.isThundering();
        int clearTime = weather.getClearWeatherTime();
        int rainTime = weather.getRainTime();
        int thunderTime = weather.getThunderTime();
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        try {
            conditional("cond_rain", false, "any", List.of(new WeatherTrigger("rain")));
            AutoTriggerManager.rebuildIndex();

            // isRaining() reads the rain level, which otherwise fades in over many ticks.
            level.setWeatherParameters(0, 6000, true, false);
            level.setRainLevel(1.0F);
            RelockPolls.pollWorld(level.getServer());
            if (!StageData.get(level).hasStage(id)) {
                helper.fail("it is raining; the global conditional stage must open on the world poll");
                return;
            }
            level.setWeatherParameters(6000, 0, false, false);
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
            RelockPolls.pollWorld(level.getServer());
            if (StageData.get(level).hasStage(id)) {
                helper.fail("the rain stopped; the global conditional stage must close on the world poll");
                return;
            }
            helper.succeed();
        } finally {
            // Field by field: setWeatherParameters ties rain and thunder time to one value.
            weather.setClearWeatherTime(clearTime);
            weather.setRainTime(rainTime);
            weather.setThunderTime(thunderTime);
            weather.setRaining(wasRaining);
            weather.setThundering(wasThundering);
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
            data.removeStage(id);
            StageData.refreshCache(data.getUnlockedStages());
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
        }
    }

    @GameTest(template = "empty")
    public static void conditionalChecksDependenciesOnlyWhenOpening(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "cond_dep";
        String dep = P + "dep";
        StageData global = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("dep");
            DependencyGroup group = new DependencyGroup();
            group.setStages(new ArrayList<>(List.of(dep)));
            conditional("cond_dep", true, "any", List.of(new XpLevelTrigger(5)), group);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);
            player.experienceLevel = 7;

            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (open(helper, player, id)) {
                helper.fail("the dependency stage is locked; the conditional stage must not open");
                return;
            }
            global.addStage(dep);
            StageData.refreshCache(global.getUnlockedStages());
            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (!open(helper, player, id)) {
                helper.fail("the dependency is met and the XP state holds; the conditional stage must open");
                return;
            }
            global.removeStage(dep);
            StageData.refreshCache(global.getUnlockedStages());
            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (!open(helper, player, id)) {
                helper.fail("dependencies are checked only when opening; losing one must not close an open "
                        + "conditional stage");
                return;
            }
            helper.succeed();
        } finally {
            global.removeStage(dep);
            StageData.refreshCache(global.getUnlockedStages());
            GameTestStages.removeAll();
            cleanUp(helper, player, "cond_dep");
        }
    }

    @GameTest(template = "empty")
    public static void blockedWhileHoldsAConditionalShut(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "cond_blocked";
        String guard = P + "guard";
        StageData global = StageData.get(helper.getLevel());
        try {
            GameTestStages.global("guard");
            GameTestStages.individual("cond_blocked", e -> {
                e.setMode(StageMode.CONDITIONAL);
                e.setAutoTrigger(new AutoTrigger("any", List.of(new XpLevelTrigger(5))));
                e.setLogic(LogicCodec.write(List.of(LogicBlock.of(LogicBlockTypes.BLOCKED_WHILE,
                        new Condition.Unlocked(guard, StageScope.GLOBAL)))));
            });
            AutoTriggerManager.rebuildIndex();
            global.addStage(guard);
            StageData.refreshCache(global.getUnlockedStages());
            player = GameTestPlayers.createConnected(helper);
            player.experienceLevel = 7;

            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (open(helper, player, id)) {
                helper.fail("the stage is blocked while the guard is unlocked; the conditional poll must not "
                        + "open it even though its XP state holds");
                return;
            }
            helper.succeed();
        } finally {
            global.removeStage(guard);
            StageData.refreshCache(global.getUnlockedStages());
            GameTestStages.removeAll();
            cleanUp(helper, player, "cond_blocked");
        }
    }

    // --- lock triggers ------------------------------------------------------

    @GameTest(template = "empty")
    public static void lockTriggerFiresOnlyWhileOpen(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "lock_open_only";
        try {
            // ALL with two: with one ANY trigger a wrongly counted event would be satisfied, cleared
            // and leave no trace, so the "closed" half could not catch anything.
            autoWithLock("lock_open_only", List.of(NEVER), "all", List.of(
                    new EffectTrigger(effectId(MobEffects.BLINDNESS)),
                    new EffectTrigger(effectId(MobEffects.CONFUSION))), true);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);

            postEffect(player, MobEffects.BLINDNESS);
            if (!lockProgress(helper, player, id).isEmpty()) {
                helper.fail("the stage was closed when the lock trigger fired; nothing may be recorded, got "
                        + lockProgress(helper, player, id));
                return;
            }
            if (!StageStates.unlockIndividual(id, player)) {
                helper.fail("setup: the stage should unlock normally");
                return;
            }
            postEffect(player, MobEffects.BLINDNESS);
            postEffect(player, MobEffects.CONFUSION);
            if (open(helper, player, id)) {
                helper.fail("the stage was open and both ALL lock triggers fired; it must be closed now");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "lock_open_only");
        }
    }

    @GameTest(template = "empty")
    public static void progressResetsOnUnlock(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "lock_reset";
        try {
            autoWithLock("lock_reset", List.of(NEVER), "all", List.of(
                    new EffectTrigger(effectId(MobEffects.BLINDNESS)),
                    new EffectTrigger(effectId(MobEffects.CONFUSION))), true);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);

            StageStates.unlockIndividual(id, player);
            postEffect(player, MobEffects.BLINDNESS);
            if (lockProgress(helper, player, id).size() != 1) {
                helper.fail("one of two ALL lock triggers fired; progress should hold exactly one entry, got "
                        + lockProgress(helper, player, id));
                return;
            }
            StageStates.relockIndividual(id, player);
            StageStates.unlockIndividual(id, player);
            if (!lockProgress(helper, player, id).isEmpty()) {
                helper.fail("the stage unlocked again; lock progress must start from zero, got "
                        + lockProgress(helper, player, id));
                return;
            }
            postEffect(player, MobEffects.CONFUSION);
            if (!open(helper, player, id)) {
                helper.fail("only the second ALL lock trigger fired since the last unlock; the stage must stay open");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "lock_reset");
        }
    }

    @GameTest(template = "empty")
    public static void negatedLockTriggerClosesWhenEffectEnds(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "lock_negated";
        try {
            autoWithLock("lock_negated", List.of(NEVER), "any",
                    List.of(new NegatedTrigger(new EffectTrigger(effectId(MobEffects.FIRE_RESISTANCE)))), true);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);

            if (!StageStates.unlockIndividual(id, player) || !open(helper, player, id)) {
                helper.fail("setup: the stage should unlock normally before the poll");
                return;
            }
            // The player has no effects at all, so "no longer has fire resistance" holds.
            AutoTriggerEventBridge.pollPlayer(player, 0);
            if (open(helper, player, id)) {
                helper.fail("the player has no fire resistance; the negated lock trigger must close the stage "
                        + "on the poll");
                return;
            }
            if (!StageStates.unlockIndividual(id, player)) {
                helper.fail("re_unlockable is true; the stage must not be lost and a normal unlock must work");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "lock_negated");
        }
    }

    @GameTest(template = "empty")
    public static void permanentLossRefusesNormalUnlockButNotAdmin(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "lock_lost";
        try {
            autoWithLock("lock_lost", List.of(NEVER), "any",
                    List.of(new EffectTrigger(effectId(MobEffects.BLINDNESS))), null);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);
            LostStagesData lost = LostStagesData.get(helper.getLevel());

            StageStates.unlockIndividual(id, player);
            postEffect(player, MobEffects.BLINDNESS);
            if (open(helper, player, id) || !lost.isLostIndividual(player.getUUID(), id)) {
                helper.fail("re_unlockable defaults to false; the lock trigger must close the stage and mark it "
                        + "lost (open=" + open(helper, player, id) + ", lost="
                        + lost.isLostIndividual(player.getUUID(), id) + ")");
                return;
            }
            if (StageStates.unlockIndividual(id, player) || open(helper, player, id)) {
                helper.fail("a stage lost for good must refuse a normal unlock");
                return;
            }
            StageStates.UnlockOutcome outcome = StageStates.forceUnlockIndividual(id, player);
            if (!outcome.unlocked() || !open(helper, player, id)) {
                helper.fail("an admin unlock must go through a permanent loss, got " + outcome);
                return;
            }
            if (lost.isLostIndividual(player.getUUID(), id)) {
                helper.fail("an admin unlock must clear the lost mark");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "lock_lost");
        }
    }

    @GameTest(template = "empty")
    public static void lostAutoStageCollectsNoProgress(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "lost_auto";
        try {
            GameTestStages.individual("lost_auto", e -> {
                e.setMode(StageMode.AUTO);
                // ALL with an unreachable second trigger: the first would otherwise be recorded
                // as progress without unlocking, which is exactly what must not happen.
                e.setAutoTrigger(new AutoTrigger("all", List.of(new XpLevelTrigger(5), NEVER)));
            });
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);
            LostStagesData.get(helper.getLevel()).markIndividual(player.getUUID(), id);
            player.experienceLevel = 7;

            AutoTriggerEventBridge.pollPlayer(player, 0);
            Set<Long> progress = AutoTriggerProgressData.get(helper.getLevel()).peek(player.getUUID(), id);
            if (progress != null && !progress.isEmpty()) {
                helper.fail("the stage is lost for good; its unlock triggers must not collect progress, got "
                        + progress);
                return;
            }
            if (open(helper, player, id)) {
                helper.fail("the stage is lost for good; it must stay closed");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "lost_auto");
        }
    }

    @GameTest(template = "empty")
    public static void oneEventDoesNotRelockAndReunlock(GameTestHelper helper) {
        ServerPlayer player = null;
        String id = P + "flip_flop";
        try {
            EffectTrigger blindness = new EffectTrigger(effectId(MobEffects.BLINDNESS));
            autoWithLock("flip_flop", List.of(blindness), "any", List.of(blindness), true);
            AutoTriggerManager.rebuildIndex();
            player = GameTestPlayers.createConnected(helper);

            StageStates.unlockIndividual(id, player);
            postEffect(player, MobEffects.BLINDNESS);
            if (open(helper, player, id)) {
                helper.fail("the same event is both the unlock and the lock trigger; the lock must win and the "
                        + "event must not reopen the stage it just closed");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            cleanUp(helper, player, "flip_flop");
        }
    }

    // --- persistence --------------------------------------------------------

    @GameTest(template = "empty")
    public static void persistenceRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID uuid = UUID.randomUUID();

        LostStagesData lost = new LostStagesData();
        lost.markGlobal(P + "a");
        lost.markIndividual(uuid, P + "b");
        LostStagesData lostBack = LostStagesData.load(lost.save(new CompoundTag(), registries), registries);
        if (!lostBack.globalSnapshot().equals(Set.of(P + "a"))
                || !lostBack.individualSnapshot(uuid).equals(Set.of(P + "b"))) {
            helper.fail("lost marks must survive save and load, got global=" + lostBack.globalSnapshot()
                    + " individual=" + lostBack.individualSnapshot(uuid));
            return;
        }

        LockTriggerProgressData progress = new LockTriggerProgressData();
        progress.global(P + "g").add(3L);
        progress.individual(uuid, P + "i").addAll(Set.of(1L, 2L));
        LockTriggerProgressData progressBack =
                LockTriggerProgressData.load(progress.save(new CompoundTag(), registries), registries);
        if (!progressBack.global(P + "g").equals(Set.of(3L))
                || !progressBack.individual(uuid, P + "i").equals(Set.of(1L, 2L))) {
            helper.fail("lock progress must survive save and load, got global=" + progressBack.global(P + "g")
                    + " individual=" + progressBack.individual(uuid, P + "i"));
            return;
        }
        helper.succeed();
    }
}
