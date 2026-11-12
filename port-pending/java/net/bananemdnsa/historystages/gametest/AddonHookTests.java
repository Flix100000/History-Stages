package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageAnnounceEvent;
import net.bananemdnsa.historystages.api.stage.StageChangeCause;
import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.api.stage.StageGraph;
import net.bananemdnsa.historystages.api.stage.StageProgress;
import net.bananemdnsa.historystages.api.stage.StageRef;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.api.team.Teams;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.compat.script.ScriptStageApi;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.data.auto.conditions.EffectTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.XpLevelTrigger;
import net.bananemdnsa.historystages.data.relock.LockTrigger;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.LostStagesData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.data.team.TeamProviders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The hooks an addon uses to react to stages rather than only ask about them: causes, the
 * announcement, the loss event, progress, the graph, batches and teams.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AddonHookTests {

    private static final String P = GameTestStages.PREFIX;

    private AddonHookTests() {}

    @GameTest(template = "empty")
    public static void eventsSayWhatCausedThem(GameTestHelper helper) {
        String viaScript = P + "hook_script";
        String viaAddon = P + "hook_addon";
        StageData data = StageData.get(helper.getLevel());
        List<String> seen = new ArrayList<>();
        Consumer<StageEvent.Unlocked> listener = event -> {
            if (event.getStageId().startsWith(P + "hook_")) seen.add(event.getStageId() + "=" + event.getCause());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            GameTestStages.global("hook_script");
            GameTestStages.global("hook_addon");
            ScriptStageApi.unlock(viaScript);
            StageStates.unlockGlobal(viaAddon, helper.getLevel());

            List<String> expected = List.of(viaScript + "=" + StageChangeCause.SCRIPT,
                    viaAddon + "=" + StageChangeCause.ADDON);
            if (!seen.equals(expected)) {
                helper.fail("expected " + expected + ", got " + seen);
                return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            GameTestStages.removeAll();
            data.removeStage(viaScript);
            data.removeStage(viaAddon);
        }
    }

    @GameTest(template = "empty")
    public static void aTeammateSeesTeamAsTheCause(GameTestHelper helper) {
        ServerPlayer alice = GameTestPlayers.createConnected(helper);
        ServerPlayer bob = GameTestPlayers.createConnected(helper);
        String id = P + "hook_team";
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        List<StageChangeCause> causes = new ArrayList<>();
        Consumer<StageEvent.IndividualUnlocked> listener = event -> {
            if (event.getStageId().equals(id)) causes.add(event.getCause());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            GameTestStages.individual("hook_team", stage -> stage.setShareWithTeam(true));
            Set<UUID> team = Set.of(alice.getUUID(), bob.getUUID());
            TeamProviders.overrideForTesting((server, player) -> team.contains(player) ? team : Set.of());

            StageStates.unlockIndividual(id, alice);
            net.bananemdnsa.historystages.data.team.TeamStageSharing.onLogin(
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(bob));

            if (!causes.equals(List.of(StageChangeCause.ADDON, StageChangeCause.TEAM))) {
                helper.fail("alice unlocked it herself, bob got it from the team; got " + causes);
                return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            TeamProviders.overrideForTesting(null);
            GameTestStages.removeAll();
            data.removeStage(alice.getUUID(), id);
            data.removeStage(bob.getUUID(), id);
            net.bananemdnsa.historystages.data.saveddata.TeamSharePending.get(helper.getLevel()).takeGrants(bob.getUUID());
        }
    }

    @GameTest(template = "empty")
    public static void anAddonCanTakeOverTheAnnouncement(GameTestHelper helper) {
        String id = P + "hook_announce";
        StageData data = StageData.get(helper.getLevel());
        List<String> announced = new ArrayList<>();
        Consumer<StageAnnounceEvent> listener = event -> {
            if (event.getStageIds().contains(id)) {
                announced.add(event.getStageIds() + ":" + event.isUnlock());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            GameTestStages.global("hook_announce");
            if (!StageStates.unlockGlobal(id, helper.getLevel()) || !data.hasStage(id)) {
                helper.fail("cancelling the announcement must not stop the unlock itself");
                return;
            }
            StageStates.relockGlobal(id, helper.getLevel());
            if (!announced.equals(List.of("[" + id + "]:true", "[" + id + "]:false"))) {
                helper.fail("expected one announcement for the unlock and one for the relock, got " + announced);
                return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            GameTestStages.removeAll();
            data.removeStage(id);
        }
    }

    @GameTest(template = "empty")
    public static void aBatchUnlockIsAnnouncedOnce(GameTestHelper helper) {
        String a = P + "hook_batch_a";
        String b = P + "hook_batch_b";
        StageData data = StageData.get(helper.getLevel());
        List<List<String>> announced = new ArrayList<>();
        Consumer<StageAnnounceEvent> listener = event -> {
            if (event.getStageIds().stream().anyMatch(s -> s.startsWith(P + "hook_batch"))) {
                announced.add(event.getStageIds());
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            GameTestStages.global("hook_batch_a");
            GameTestStages.global("hook_batch_b");
            List<String> done = StageStates.unlockGlobal(List.of(a, b, P + "hook_batch_typo"), helper.getLevel());
            if (!done.equals(List.of(a, b))) {
                helper.fail("both real stages must unlock and the typo must be skipped; got " + done);
                return;
            }
            if (!announced.equals(List.of(List.of(a, b)))) {
                helper.fail("the batch must be announced once with both stages, got " + announced);
                return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            GameTestStages.removeAll();
            data.removeStage(a);
            data.removeStage(b);
        }
    }

    @GameTest(template = "empty")
    public static void aPermanentLossIsAnnouncedAfterTheRelock(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.createConnected(helper);
        String id = P + "hook_lost";
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        List<String> order = new ArrayList<>();
        Consumer<StageEvent.IndividualLocked> locked = event -> {
            if (event.getStageId().equals(id)) order.add("locked");
        };
        Consumer<StageEvent.LostForGood> listener = event -> {
            if (event.getStageId().equals(id)) order.add("lost:" + event.getCause());
        };
        NeoForge.EVENT_BUS.addListener(locked);
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            GameTestStages.individual("hook_lost", e -> {
                e.setMode(StageMode.AUTO);
                e.setAutoTrigger(new AutoTrigger("any", List.of(new XpLevelTrigger(100_000))));
                String blindness = String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.BLINDNESS.value()));
                e.setLockTrigger(new LockTrigger("any", List.of(new EffectTrigger(blindness)), null));
            });
            AutoTriggerManager.rebuildIndex();
            StageStates.unlockIndividual(id, player);
            NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(player, null,
                    new MobEffectInstance(MobEffects.BLINDNESS, 100), null));

            if (!order.equals(List.of("locked", "lost:" + StageChangeCause.LOCK_TRIGGER))) {
                helper.fail("expected the relock first, then the loss caused by the lock trigger; got " + order);
                return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(locked);
            NeoForge.EVENT_BUS.unregister(listener);
            GameTestStages.removeAll();
            AutoTriggerManager.rebuildIndex();
            data.removeStage(player.getUUID(), id);
            LostStagesData.get(helper.getLevel()).clearIndividual(player.getUUID(), id);
        }
    }

    @GameTest(template = "empty")
    public static void progressIsReadable(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        String id = P + "hook_progress";
        try {
            GameTestStages.global("hook_progress", e -> {
                e.setMode(StageMode.AUTO);
                e.setAutoTrigger(new AutoTrigger("all", List.of(new XpLevelTrigger(100_000), new XpLevelTrigger(200_000))));
            });
            var triggers = StageProgress.triggers(id, player).orElse(null);
            if (triggers == null || !triggers.all() || triggers.entries().size() != 2
                    || triggers.met() != 0 || triggers.complete()) {
                helper.fail("expected an ALL trigger with two unmet entries, got " + triggers);
                return;
            }
            var research = StageProgress.research(ScrollVariants.createScroll(id)).orElse(null);
            if (research == null || !research.stageId().equals(id) || research.progress() != 0 || research.total() <= 0) {
                helper.fail("a fresh scroll must report its stage, no progress and a positive total; got " + research);
                return;
            }
            if (StageProgress.triggers(P + "hook_nothing", player).isPresent()) {
                helper.fail("an unknown stage has no trigger progress");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    @GameTest(template = "empty")
    public static void theGraphAnswersWhatComesNext(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.create(helper);
        StageData data = StageData.get(helper.getLevel());
        StageRef a = StageRef.global(P + "hook_graph_a");
        StageRef b = StageRef.global(P + "hook_graph_b");
        StageRef c = StageRef.global(P + "hook_graph_c");
        try {
            GameTestStages.global("hook_graph_a");
            GameTestStages.global("hook_graph_b", needs(a.id()));
            GameTestStages.global("hook_graph_c", needs(b.id()));

            if (!StageGraph.requires(c).equals(List.of(b)) || !StageGraph.requiredBy(a).equals(List.of(b))) {
                helper.fail("c requires b and a is required by b; got " + StageGraph.requires(c)
                        + " / " + StageGraph.requiredBy(a));
                return;
            }
            if (StageGraph.state(a, player) != StageGraph.Node.REACHABLE
                    || StageGraph.state(b, player) != StageGraph.Node.LOCKED) {
                helper.fail("a needs nothing and is reachable, b waits for a");
                return;
            }
            if (!StageGraph.missingOnPathTo(c, player).equals(List.of(a, b))) {
                helper.fail("the way to c is a, then b; got " + StageGraph.missingOnPathTo(c, player));
                return;
            }
            data.addStage(a.id());
            if (StageGraph.state(a, player) != StageGraph.Node.UNLOCKED
                    || StageGraph.state(b, player) != StageGraph.Node.REACHABLE
                    || !StageGraph.missingOnPathTo(c, player).equals(List.of(b))) {
                helper.fail("with a unlocked, b becomes reachable and only b is left on the way to c");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(a.id());
        }
    }

    @GameTest(template = "empty")
    public static void teamsAreReadable(GameTestHelper helper) {
        ServerPlayer alice = GameTestPlayers.create(helper);
        ServerPlayer bob = GameTestPlayers.create(helper);
        ServerPlayer carol = GameTestPlayers.create(helper);
        try {
            GameTestStages.individual("hook_shared", stage -> stage.setShareWithTeam(true));
            GameTestStages.individual("hook_private");
            Set<UUID> team = new HashSet<>(List.of(alice.getUUID(), bob.getUUID()));
            TeamProviders.overrideForTesting((server, player) -> team.contains(player) ? Set.copyOf(team) : Set.of());

            if (!Teams.teammates(alice).equals(Set.of(bob.getUUID()))) {
                helper.fail("alice's teammates are bob and not herself; got " + Teams.teammates(alice));
                return;
            }
            if (Teams.areTeammates(alice.getUUID(), carol.getUUID(), helper.getLevel().getServer())) {
                helper.fail("carol is on no team");
                return;
            }
            if (!Teams.isSharedWithTeam(P + "hook_shared") || Teams.isSharedWithTeam(P + "hook_private")) {
                helper.fail("only the stage set to share is shared");
                return;
            }
            helper.succeed();
        } finally {
            TeamProviders.overrideForTesting(null);
            GameTestStages.removeAll();
        }
    }

    private static DependencyGroup needs(String stageId) {
        DependencyGroup group = new DependencyGroup();
        group.setStages(new ArrayList<>(List.of(stageId)));
        return group;
    }
}
